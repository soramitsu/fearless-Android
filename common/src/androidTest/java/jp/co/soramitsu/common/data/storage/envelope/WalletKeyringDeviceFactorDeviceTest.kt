package jp.co.soramitsu.common.data.storage.envelope

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.security.KeyStore
import java.security.SecureRandom
import jp.co.soramitsu.common.data.storage.PreferencesImpl
import jp.co.soramitsu.common.data.storage.encrypt.EncryptionUtil
import jp.co.soramitsu.common.data.storage.encrypt.WalletPinHealth
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecureStorageHealth
import jp.co.soramitsu.common.di.modules.SHARED_PREFERENCES_FILE
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The PIN wrap's device factor on the real device key: 32 random bytes under the
 * Keystore-wrapped master key M, in the wallet preference file, with the
 * production Argon2id parameters. No Keystore key of its own is created. Logs
 * only how long one PIN check takes; never a PIN, a key or a wrap.
 */
@RunWith(AndroidJUnit4::class)
class WalletKeyringDeviceFactorDeviceTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        check(context.packageName != "jp.co.soramitsu.fearless") { "Never run in the production wallet UID" }
        clear()
    }

    @After
    fun tearDown() = clear()

    private fun clear() {
        WalletSecretSession.clear()
        WalletPinHealth.resetForTest()
        WalletSecureStorageHealth.resetForTest()
        context.getSharedPreferences(SHARED_PREFERENCES_FILE, Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("key_alias", Context.MODE_PRIVATE).edit().clear().commit()
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        listOf("key_alias", "key_alias_v2").forEach { if (store.containsAlias(it)) store.deleteEntry(it) }
    }

    private fun preferences() = PreferencesImpl(context.getSharedPreferences(SHARED_PREFERENCES_FILE, Context.MODE_PRIVATE))

    private fun keystoreAliases(): Set<String> =
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.aliases().toList().toSet()

    @Test
    fun theFactorIsStableAndLivesUnderTheDeviceKey() {
        val preferences = preferences()
        val factor = MasterKeyBoundKeyringFactor(preferences, EncryptionUtil(context))
        val keystoreBefore = keystoreAliases()
        val alias = factor.create()

        assertTrue(alias.startsWith(MasterKeyBoundKeyringFactor.ALIAS_PREFIX))
        assertTrue(factor.exists(alias))
        // No new Keystore entry: the factor is kept under M only.
        assertEquals(keystoreBefore + setOf("key_alias").filter { it !in keystoreBefore }, keystoreAliases())
        val stored = checkNotNull(preferences.getString(alias))
        assertTrue(stored.startsWith("v2:"))
        val salt = ByteArray(16) { 7 }
        val first = factor.hmac(alias, salt)
        assertEquals(32, first.size)
        // A new process: the same files and Keystore, a new EncryptionUtil.
        assertArrayEquals(first, MasterKeyBoundKeyringFactor(preferences(), EncryptionUtil(context)).hmac(alias, salt))
        assertNotEquals(first.toList(), factor.hmac(alias, ByteArray(16) { 8 }).toList())
        assertNotEquals(first.toList(), factor.hmac(factor.create(), salt).toList())
    }

    @Test
    fun aMissingOrCorruptFactorIsLostNotWrong() {
        val preferences = preferences()
        val factor = MasterKeyBoundKeyringFactor(preferences, EncryptionUtil(context))
        assertFalse(factor.exists("${MasterKeyBoundKeyringFactor.ALIAS_PREFIX}missing"))
        assertThrows(WalletKeyringDeviceFactorLostException::class.java) {
            factor.hmac("${MasterKeyBoundKeyringFactor.ALIAS_PREFIX}missing", ByteArray(16))
        }
        val alias = factor.create()
        val stored = checkNotNull(preferences.getString(alias))
        // One base64 character of the sealed bytes changed: still decodable, no longer authentic.
        val index = "v2:".length + 20
        val flipped = stored.substring(0, index) + (if (stored[index] == 'A') 'B' else 'A') + stored.substring(index + 1)
        preferences.replaceStringsDurably(mapOf(alias to flipped), emptySet())
        assertThrows(WalletKeyringDeviceFactorLostException::class.java) { factor.hmac(alias, ByteArray(16)) }
    }

    @Test
    fun thePinWrapOpensOnlyWithItsPinAtTheProductionCost() {
        val preferences = preferences()
        val keyring = WalletSecretKeyring(
            preferences, MasterKeyBoundKeyringFactor(preferences, EncryptionUtil(context)), SecureRandom(), WalletPinKdfParameters.DEFAULT
        )
        // Creation runs the KDF twice (wrap, then the verified read-back); the first run is also the coldest.
        val createStarted = System.nanoTime()
        val sk = keyring.create("482916")
        val createMillis = (System.nanoTime() - createStarted) / 1_000_000
        val openMillis = (1..3).map {
            val started = System.nanoTime()
            val opened = keyring.openWithPin("482916") as WalletSecretKeyring.PinOpening.Opened
            val elapsed = (System.nanoTime() - started) / 1_000_000
            assertArrayEquals(sk.key, opened.key.key)
            opened.key.close()
            elapsed
        }
        Log.i(
            "WalletKeyringDeviceFactorDeviceTest",
            "keyring_create_ms=$createMillis pin_wrap_open_ms=${openMillis.joinToString(",")} kdf=${WalletPinKdfParameters.DEFAULT}"
        )
        val elapsedMillis = openMillis.max()
        assertSame(WalletSecretKeyring.PinOpening.WrongPin, keyring.openWithPin("000000"))
        assertTrue("One PIN check took $elapsedMillis ms", elapsedMillis < 10_000)

        // Removing the factor makes the wrap unusable, never a wrong PIN.
        val factorKeys = context.getSharedPreferences(SHARED_PREFERENCES_FILE, Context.MODE_PRIVATE).all.keys
            .filter { it.startsWith(MasterKeyBoundKeyringFactor.ALIAS_PREFIX) }.toSet()
        assertEquals(1, factorKeys.size)
        preferences.replaceStringsDurably(emptyMap(), factorKeys)
        assertEquals(WalletSecretKeyring.PinWrapState.UNUSABLE, keyring.pinWrapState())
        assertSame(WalletSecretKeyring.PinOpening.Unusable, keyring.openWithPin("482916"))
    }
}
