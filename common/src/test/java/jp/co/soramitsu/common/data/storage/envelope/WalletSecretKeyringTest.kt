package jp.co.soramitsu.common.data.storage.envelope

import java.security.SecureRandom
import jp.co.soramitsu.common.data.storage.pin.InMemoryPreferences
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WalletSecretKeyringTest {

    private val preferences = InMemoryPreferences()
    private val hardware = FakeKeyringHardwareKey()
    private val keyring = WalletSecretKeyring(preferences, hardware, SecureRandom(), TEST_KDF)

    @Test
    fun `a new keyring opens only with its PIN`() {
        assertEquals(WalletSecretKeyring.PinWrapState.ABSENT, keyring.pinWrapState())
        val created = keyring.create(PIN)

        assertEquals(WalletSecretKeyring.PinWrapState.USABLE, keyring.pinWrapState())
        assertTrue(keyring.exists())
        assertArrayEquals(created.keyId, keyring.currentKeyId())
        val opened = keyring.openWithPin(PIN) as WalletSecretKeyring.PinOpening.Opened
        assertArrayEquals(created.key, opened.key.key)
        assertSame(WalletSecretKeyring.PinOpening.WrongPin, keyring.openWithPin("000000"))
        assertSame(WalletSecretKeyring.PinOpening.WrongPin, keyring.openWithPin("123457"))
    }

    @Test
    fun `no PIN or key byte is stored in the clear`() {
        val created = keyring.create(PIN)
        val stored = preferences.snapshot().values.joinToString("|")
        assertFalse(stored.contains(PIN))
        assertFalse(stored.contains(org.bouncycastle.util.encoders.Hex.toHexString(created.key)))
        assertFalse(stored.contains(java.util.Base64.getEncoder().encodeToString(created.key)))
    }

    @Test
    fun `a second keyring is never created over an existing one`() {
        keyring.create(PIN)
        val before = preferences.snapshot()
        assertThrows(IllegalStateException::class.java) { keyring.create("654321") }
        assertEquals(before, preferences.snapshot())
    }

    @Test
    fun `a PIN change rewraps the same SK and the old PIN stops working`() {
        val created = keyring.create(PIN)
        keyring.replacePin(created, "654321", expectedRecord = keyring.pinRecord())

        val reopened = keyring.openWithPin("654321") as WalletSecretKeyring.PinOpening.Opened
        assertArrayEquals(created.key, reopened.key.key)
        assertArrayEquals(created.keyId, reopened.key.keyId)
        assertSame(WalletSecretKeyring.PinOpening.WrongPin, keyring.openWithPin(PIN))
    }

    @Test
    fun `a PIN change bound to a stale record changes nothing`() {
        val created = keyring.create(PIN)
        val stale = keyring.pinRecord()
        keyring.replacePin(created, "654321", expectedRecord = stale)
        val current = keyring.pinRecord()
        assertThrows(IllegalStateException::class.java) { keyring.replacePin(created, "111111", expectedRecord = stale) }
        assertEquals(current, keyring.pinRecord())
    }

    @Test
    fun `a lost hardware key makes the PIN wrap unusable, never a wrong PIN`() {
        keyring.create(PIN)
        hardware.loseAll()
        assertEquals(WalletSecretKeyring.PinWrapState.UNUSABLE, keyring.pinWrapState())
        assertSame(WalletSecretKeyring.PinOpening.Unusable, keyring.openWithPin(PIN))
        assertSame(WalletSecretKeyring.PinOpening.Unusable, keyring.openWithPin("000000"))
    }

    @Test
    fun `a backup wrap recovers SK and a new PIN wrap gets a new hardware key`() {
        val created = keyring.create(PIN)
        val root = SyntheticSecrets.bytes(42)
        keyring.addBackupWrap(created, root)
        assertTrue(keyring.hasBackupWrap(created.keyId, root))
        hardware.loseAll()

        assertNull(keyring.openWithBackup(listOf(SyntheticSecrets.bytes(43))))
        val recovered = checkNotNull(keyring.openWithBackup(listOf(SyntheticSecrets.bytes(43), root)))
        assertArrayEquals(created.key, recovered.key)
        val creationsBefore = hardware.creations
        keyring.replacePin(recovered, "246810", expectedRecord = keyring.pinRecord())

        assertEquals(creationsBefore + 1, hardware.creations)
        assertEquals(WalletSecretKeyring.PinWrapState.USABLE, keyring.pinWrapState())
        val opened = keyring.openWithPin("246810") as WalletSecretKeyring.PinOpening.Opened
        assertArrayEquals(created.key, opened.key.key)
    }

    @Test
    fun `backup wraps are added once and kept`() {
        val created = keyring.create(PIN)
        val root = SyntheticSecrets.bytes(7)
        keyring.addBackupWrap(created, root)
        val after = preferences.snapshot()
        keyring.addBackupWrap(created, root)
        assertEquals(after, preferences.snapshot())
    }

    @Test
    fun `a damaged backup wrap is replaced by one that opens`() {
        val created = keyring.create(PIN)
        val root = SyntheticSecrets.bytes(7)
        val name = WalletSecretKeyring.backupKey(created.keyId, WalletSecretKeyring.rootId(root))
        preferences.putString(name, "AAAA")
        keyring.addBackupWrap(created, root)
        assertArrayEquals(created.key, keyring.openWithBackup(listOf(root))!!.key)
    }

    @Test
    fun `a damaged PIN wrap is unusable, not wrong`() {
        val created = keyring.create(PIN)
        val name = WalletSecretKeyring.pinKey(created.keyId)
        val record = checkNotNull(preferences.getString(name))
        preferences.putString(name, record.dropLast(4))
        assertEquals(WalletSecretKeyring.PinWrapState.UNUSABLE, keyring.pinWrapState())
        assertSame(WalletSecretKeyring.PinOpening.Unusable, keyring.openWithPin(PIN))
    }

    @Test
    fun `hostile KDF parameters are refused before any work`() {
        val created = keyring.create(PIN)
        val name = WalletSecretKeyring.pinKey(created.keyId)
        val bytes = java.util.Base64.getUrlDecoder().decode(checkNotNull(preferences.getString(name)))
        // Memory field: magic(4) version(1) id(16) algorithm(1) -> bytes 22..25.
        bytes[22] = 0x7f
        preferences.putString(name, java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes))
        assertSame(WalletSecretKeyring.PinOpening.Unusable, keyring.openWithPin(PIN))
    }

    @Test
    fun `a creation that cannot be verified leaves no keyring behind`() {
        val failing = object : WalletKeyringHardwareKey by hardware {
            var calls = 0
            override fun hmac(alias: String, data: ByteArray): ByteArray {
                calls++
                val mac = hardware.hmac(alias, data)
                if (calls > 1) mac[0] = (mac[0].toInt() xor 1).toByte()
                return mac
            }
        }
        val unstable = WalletSecretKeyring(preferences, failing, SecureRandom(), TEST_KDF)
        assertThrows(IllegalStateException::class.java) { unstable.create(PIN) }
        assertFalse(unstable.exists())
        assertTrue(preferences.snapshot().isEmpty())
    }

    @Test
    fun `the production KDF stays within its memory budget`() {
        val parameters = WalletPinKdfParameters.DEFAULT
        assertTrue(parameters.isAcceptable)
        assertTrue(parameters.memoryKiB <= 32 * 1024)
        assertEquals(3, parameters.iterations)
        assertFalse(WalletPinKdfParameters(memoryKiB = 128 * 1024).isAcceptable)
        assertFalse(WalletPinKdfParameters(iterations = 0).isAcceptable)
        assertFalse(WalletPinKdfParameters(algorithm = 2).isAcceptable)
    }

    @Test
    fun `the PIN KDF is deterministic per salt`() {
        val salt = ByteArray(16) { 3 }
        val a = WalletPinKdf.derive(PIN, salt, TEST_KDF)
        assertArrayEquals(a, WalletPinKdf.derive(PIN, salt, TEST_KDF))
        assertFalse(a.contentEquals(WalletPinKdf.derive("123457", salt, TEST_KDF)))
        assertFalse(a.contentEquals(WalletPinKdf.derive(PIN, ByteArray(16) { 4 }, TEST_KDF)))
    }

    private companion object {
        const val PIN = "123456"
    }
}
