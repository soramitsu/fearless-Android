package jp.co.soramitsu.common.data.storage.envelope

import android.content.Context
import java.security.Key
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.spec.SecretKeySpec
import jp.co.soramitsu.common.data.storage.PreferencesImpl
import jp.co.soramitsu.common.data.storage.encrypt.EncryptionUtil
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecureStorageHealth
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecureStorageUnavailableException
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever

class MasterKeyBoundKeyringFactorTest {

    @Before
    fun setUp() {
        resetEnvelopeGlobals()
        WalletSecureStorageHealth.resetForTest()
    }

    @After
    fun tearDown() {
        resetEnvelopeGlobals()
        WalletSecureStorageHealth.resetForTest()
    }

    @Test
    fun `a factor is 32 random bytes under the device key and its HMAC is stable`() {
        val harness = EnvelopeStorageHarness()
        val alias = harness.factor.create()

        assertTrue(alias.startsWith(MasterKeyBoundKeyringFactor.ALIAS_PREFIX))
        assertTrue(harness.factor.exists(alias))
        val stored = checkNotNull(harness.shared.values[alias] as? String)
        assertTrue("stored only as device-key ciphertext", stored.startsWith("v2:test:"))
        assertEquals(32, Base64.getDecoder().decode(stored.removePrefix("v2:test:")).size)

        val salt = ByteArray(16) { 7 }
        val first = harness.factor.hmac(alias, salt)
        assertEquals(32, first.size)
        // A new process over the same storage reads the same factor.
        assertArrayEquals(first, MasterKeyBoundKeyringFactor(harness.preferences, harness.crypto).hmac(alias, salt))
        assertNotEquals(first.toList(), harness.factor.hmac(alias, ByteArray(16) { 8 }).toList())
        assertNotEquals(first.toList(), harness.factor.hmac(harness.factor.create(), salt).toList())
    }

    @Test
    fun `the factor is no wallet secret to any pass that walks stored values`() {
        val alias = EnvelopeStorageHarness().factor.create()
        assertFalse(WalletSecretSealPolicy.isSealable(alias))
        assertTrue(WalletSecretSealPolicy.SCANNED_PREFIXES.none(alias::startsWith))
        assertFalse(jp.co.soramitsu.common.data.storage.encrypt.WalletMasterKeyAttestation.isProtectedPayloadKey(alias))
        assertFalse(jp.co.soramitsu.common.data.storage.encrypt.WalletMasterKeyAttestation.isAuthenticationCandidateKey(alias))
        // Keyring records are named apart from the factor, so a factor alone is no keyring.
        assertFalse(alias.startsWith("${WalletSecretKeyring.KEY_PREFIX}sk:"))
    }

    @Test
    fun `a missing, corrupt or mistyped factor is lost, never a wrong PIN`() {
        val harness = EnvelopeStorageHarness()
        val missing = "${MasterKeyBoundKeyringFactor.ALIAS_PREFIX}0011223344556677"
        assertFalse(harness.factor.exists(missing))
        assertThrows(WalletKeyringDeviceFactorLostException::class.java) { harness.factor.hmac(missing, ByteArray(16)) }

        val alias = harness.factor.create()
        harness.shared.values[alias] = "v2:corrupt"
        assertThrows(WalletKeyringDeviceFactorLostException::class.java) { harness.factor.hmac(alias, ByteArray(16)) }
        harness.shared.values[alias] = "v2:test:" + Base64.getEncoder().encodeToString(ByteArray(16))
        assertThrows(WalletKeyringDeviceFactorLostException::class.java) { harness.factor.hmac(alias, ByteArray(16)) }
        harness.shared.values[alias] = true
        assertThrows(WalletKeyringDeviceFactorLostException::class.java) { harness.factor.hmac(alias, ByteArray(16)) }
    }

    @Test
    fun `an unavailable device key is try again everywhere, never a wrong or unusable PIN`() {
        val harness = EnvelopeStorageHarness()
        harness.putLegacy("pin_code", PIN)
        assertEquals(PinVerification.CORRECT, harness.authority.verify(PIN))
        assertTrue(harness.keyring.exists())
        WalletSecretSession.clear()

        whenever(harness.crypto.decrypt(any())).thenThrow(WalletSecureStorageUnavailableException("device key unavailable"))

        assertThrows(WalletSecureStorageUnavailableException::class.java) { harness.keyring.pinWrapState() }
        assertThrows(WalletSecureStorageUnavailableException::class.java) { harness.keyring.openWithPin(PIN) }
        assertThrows(WalletSecureStorageUnavailableException::class.java) { harness.authority.verify(PIN) }
        assertThrows(WalletSecureStorageUnavailableException::class.java) { harness.authority.verifyForEmergency(PIN) }
        assertFalse(WalletSecretSession.isOpen())
    }

    @Test
    fun `a factor that does not read back, or does not encrypt, leaves nothing behind`() {
        val harness = EnvelopeStorageHarness()
        whenever(harness.crypto.decrypt(any())).thenReturn("")
        assertThrows(IllegalStateException::class.java) { harness.factor.create() }
        assertTrue(harness.factorValues().isEmpty())

        val other = EnvelopeStorageHarness()
        whenever(other.crypto.encrypt(any())).thenReturn("")
        assertThrows(IllegalStateException::class.java) { other.factor.create() }
        assertTrue(other.factorValues().isEmpty())
    }

    @Test
    fun `a keyring that fails to store or verify removes the factor it created`() {
        val harness = EnvelopeStorageHarness()
        // The verified read-back of the new wrap fails: the second HMAC under the factor differs.
        var calls = 0
        val flaky = object : WalletKeyringDeviceFactor by harness.factor {
            override fun hmac(alias: String, data: ByteArray): ByteArray =
                harness.factor.hmac(alias, data).also { if (++calls > 1) it[0] = (it[0].toInt() xor 1).toByte() }
        }
        val keyring = WalletSecretKeyring(harness.preferences, flaky, SecureRandom(), TEST_KDF)
        assertThrows(IllegalStateException::class.java) { keyring.create(PIN) }
        assertFalse(keyring.exists())
        assertTrue(harness.factorValues().isEmpty())
    }

    @Test
    fun `a factor written under the previous key reads back once that key returns`() {
        val slots = Slots()
        val preferences = PreferencesImpl(MapSharedPreferences())
        val factor = MasterKeyBoundKeyringFactor(preferences, slots.util)

        // Slot 1: M1 is the device key.
        slots.use(primary = slots.m1)
        val alias = factor.create()
        val expected = factor.hmac(alias, SALT)

        // M1 is unavailable and a fresh slot 2 is active: the factor written under M1 cannot be read.
        slots.use(primary = slots.m2, previousAvailable = false)
        assertThrows(WalletKeyringDeviceFactorLostException::class.java) { factor.hmac(alias, SALT) }

        // M1 recovers; slot 2's key stays a fallback. The factor reads back unchanged.
        slots.use(primary = slots.m1, fallbacks = listOf(slots.m2))
        assertArrayEquals(expected, factor.hmac(alias, SALT))
    }

    @Test
    fun `a factor written while slot 2 is active reads back through the fallback key`() {
        val slots = Slots()
        val preferences = PreferencesImpl(MapSharedPreferences())
        val factor = MasterKeyBoundKeyringFactor(preferences, slots.util)

        // M1 is unavailable: slot 2's fresh key writes the factor.
        slots.use(primary = slots.m2, previousAvailable = false)
        val alias = factor.create()
        val expected = factor.hmac(alias, SALT)

        // M1 recovers and becomes primary again; slot 2's key is the fallback the factor needs.
        slots.use(primary = slots.m1, fallbacks = listOf(slots.m2))
        assertArrayEquals(expected, factor.hmac(alias, SALT))
    }

    /**
     * The production [EncryptionUtil] cipher code with its key slots set by hand: the primary key,
     * the fallback keys and whether the previous key is available, as its slot logic leaves them.
     */
    private class Slots {
        val m1 = ByteArray(32) { 1 }
        val m2 = ByteArray(32) { 2 }
        val util: EncryptionUtil = spy(EncryptionUtil(mock<Context>()))
        private var primary: Key = SecretKeySpec(m1, "AES")

        init {
            doAnswer { primary }.whenever(util).getPrerenceAesKey()
        }

        fun use(primary: ByteArray, fallbacks: List<ByteArray> = emptyList(), previousAvailable: Boolean = true) {
            this.primary = SecretKeySpec(primary, "AES")
            field("fallbackPreferenceKeys").set(util, fallbacks)
            field("previousKeyAvailable").setBoolean(util, previousAvailable)
        }

        private fun field(name: String) = EncryptionUtil::class.java.getDeclaredField(name).apply { isAccessible = true }
    }

    private companion object {
        const val PIN = "582046"
        val SALT = ByteArray(16) { 9 }
    }
}
