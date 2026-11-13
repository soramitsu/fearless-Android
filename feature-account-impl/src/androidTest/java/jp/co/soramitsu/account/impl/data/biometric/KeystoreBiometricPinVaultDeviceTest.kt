package jp.co.soramitsu.account.impl.data.biometric

import androidx.biometric.BiometricManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.lang.reflect.Proxy
import jp.co.soramitsu.common.data.storage.Preferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs against the real Android Keystore. Without an enrolled strong biometric the platform
 * refuses to create the vault key, so nothing is sealed; with one, the sealed PIN stays closed to
 * a cipher that no BiometricPrompt authenticated.
 */
@RunWith(AndroidJUnit4::class)
class KeystoreBiometricPinVaultDeviceTest {
    private val values = mutableMapOf<String, Any?>()
    private val preferences = Proxy.newProxyInstance(
        Preferences::class.java.classLoader,
        arrayOf(Preferences::class.java)
    ) { _, method, args ->
        when (method.name) {
            "getString" -> values[args[0] as String] as String? ?: args.getOrNull(1)
            "putString" -> values.put(args[0] as String, args[1]).let { }
            "getBoolean" -> values[args[0] as String] as Boolean? ?: args[1]
            "putBoolean" -> values.put(args[0] as String, args[1]).let { }
            "removeField" -> values.remove(args[0] as String).let { }
            "contains" -> values.containsKey(args[0] as String)
            else -> error("unexpected ${method.name}")
        }
    } as Preferences
    private val vault = KeystoreBiometricPinVault(preferences)

    @After fun tearDown() = vault.clear()

    @Test fun aSealedPinNeedsAStrongBiometricToOpen() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val strong = BiometricManager.from(context)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
        if (!strong) {
            try {
                vault.enroll("123456")
                fail("The Keystore must refuse a biometric-bound key without an enrolled strong biometric")
            } catch (expected: Exception) {
                assertFalse(vault.isEnrolled())
                assertEquals(BiometricPinVault.UnlockCipher.Unavailable, vault.prepareUnlock())
            }
            return
        }
        vault.enroll("123456")
        assertTrue(vault.isEnrolled())
        val ready = vault.prepareUnlock() as BiometricPinVault.UnlockCipher.Ready
        assertNull("No prompt authenticated this cipher", vault.decryptPin(ready.cipher))
        vault.clear()
        assertFalse(vault.isEnrolled())
        assertEquals(BiometricPinVault.UnlockCipher.Unavailable, vault.prepareUnlock())
    }

    @Test fun nothingSealedMeansNoUnlockAndNoInvalidation() {
        assertFalse(vault.isEnrolled())
        assertEquals(BiometricPinVault.UnlockCipher.Unavailable, vault.prepareUnlock())
        assertFalse(vault.wasInvalidated())
    }
}
