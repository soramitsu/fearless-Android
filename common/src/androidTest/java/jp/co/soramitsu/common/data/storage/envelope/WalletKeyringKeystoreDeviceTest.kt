package jp.co.soramitsu.common.data.storage.envelope

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.lang.reflect.Proxy
import java.security.KeyStore
import java.security.SecureRandom
import jp.co.soramitsu.common.data.storage.Preferences
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * K_hw and the PIN wrap on the real Android Keystore, with the production Argon2id parameters.
 * Logs only how long one PIN check takes; never a PIN, a key or a wrap.
 */
@RunWith(AndroidJUnit4::class)
class WalletKeyringKeystoreDeviceTest {
    private val hardware = AndroidKeystoreWalletKeyringHardwareKey()
    private val created = mutableListOf<String>()
    private val values = mutableMapOf<String, Any?>()
    private val preferences = Proxy.newProxyInstance(
        Preferences::class.java.classLoader,
        arrayOf(Preferences::class.java)
    ) { _, method, args ->
        when (method.name) {
            "getString" -> values[args[0] as String] as String? ?: args.getOrNull(1)
            "contains" -> values.containsKey(args[0] as String)
            "hasKeyWithPrefix" -> values.keys.any { it.startsWith(args[0] as String) }
            "replaceStringsDurably" -> {
                @Suppress("UNCHECKED_CAST")
                values.putAll(args[0] as Map<String, String>)
                @Suppress("UNCHECKED_CAST")
                (args[1] as Set<String>).forEach(values::remove)
                true
            }
            else -> error("unexpected ${method.name}")
        }
    } as Preferences

    @After
    fun tearDown() {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        created.forEach { if (store.containsAlias(it)) store.deleteEntry(it) }
    }

    @Test
    fun theHardwareKeyIsAStableNonExportableHmac() {
        val alias = hardware.create().also(created::add)
        assertTrue(hardware.exists(alias))
        val salt = ByteArray(16) { 7 }
        val first = hardware.hmac(alias, salt)
        assertEquals(32, first.size)
        assertArrayEquals(first, hardware.hmac(alias, salt))
        assertFalse(first.contentEquals(hardware.hmac(alias, ByteArray(16) { 8 })))
        val other = hardware.create().also(created::add)
        assertFalse(first.contentEquals(hardware.hmac(other, salt)))
        val entry = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.getKey(alias, null)
        assertEquals(null, entry.encoded)
    }

    @Test
    fun aMissingHardwareKeyIsLostNotWrong() {
        assertFalse(hardware.exists("fearless_wallet_keyring_hmac_v1_missing"))
        assertThrows(WalletKeyringHardwareKeyLostException::class.java) {
            hardware.hmac("fearless_wallet_keyring_hmac_v1_missing", ByteArray(16))
        }
    }

    @Test
    fun thePinWrapOpensOnlyWithItsPinAtTheProductionCost() {
        val recording = object : WalletKeyringHardwareKey by hardware {
            override fun create(): String = hardware.create().also(created::add)
        }
        val keyring = WalletSecretKeyring(preferences, recording, SecureRandom(), WalletPinKdfParameters.DEFAULT)
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
            "WalletKeyringKeystoreDeviceTest",
            "keyring_create_ms=$createMillis pin_wrap_open_ms=${openMillis.joinToString(",")} kdf=${WalletPinKdfParameters.DEFAULT}"
        )
        val elapsedMillis = openMillis.max()
        assertSame(WalletSecretKeyring.PinOpening.WrongPin, keyring.openWithPin("000000"))
        assertTrue("One PIN check took $elapsedMillis ms", elapsedMillis < 10_000)

        // Deleting K_hw makes the wrap unusable, never a wrong PIN.
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(created.single())
        assertEquals(WalletSecretKeyring.PinWrapState.UNUSABLE, keyring.pinWrapState())
        assertSame(WalletSecretKeyring.PinOpening.Unusable, keyring.openWithPin("482916"))
    }
}
