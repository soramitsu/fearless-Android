package jp.co.soramitsu.common.data.storage.encrypt

import android.content.Context
import android.content.SharedPreferences
import jp.co.soramitsu.common.di.modules.SHARED_PREFERENCES_FILE
import java.security.KeyPairGenerator
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.core.extrinsic.MutationExecutionGuard
import org.bouncycastle.util.encoders.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever
import java.security.SecureRandom
import java.security.spec.AlgorithmParameterSpec
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class EncryptionUtilMutationGuardTest {
    @Test
    fun delayedBackingReadCannotDecryptAfterRevocation() {
        val guard = Guard()
        val provider = RecordingDecryptor()
        val util = utility(provider)
        val preferences = mock<Preferences>()
        whenever(preferences.getString("secret")).thenAnswer { guard.allowed = false; ciphertext() }
        val storage = EncryptedPreferencesImpl(preferences, util)
        assertThrows(WalletSecureStorageUnavailableException::class.java) {
            storage.getAuthorizedDecryptedStringSnapshot("secret", guard, INTENT)
        }
        assertEquals(0, provider.decryptions)
    }

    @Test
    fun delayedWrappingKeyLoadCannotDecryptAfterExpiry() {
        val guard = Guard()
        val provider = RecordingDecryptor()
        val util = utility(provider)
        doAnswer { guard.allowed = false; SecretKeySpec(KEY, "AES") }
            .whenever(util).getAuthorizedPreferenceAesKey(any())
        assertThrows(WalletSecureStorageUnavailableException::class.java) { util.decryptAuthorized(ciphertext(), guard, INTENT) }
        assertEquals(0, provider.decryptions)
    }

    @Test
    fun providerPreparationCannotCarryStaleAuthorizationIntoDecryption() {
        val guard = Guard()
        val provider = RecordingDecryptor { guard.allowed = false }
        assertThrows(WalletSecureStorageUnavailableException::class.java) {
            utility(provider).decryptAuthorized(ciphertext(), guard, INTENT)
        }
        assertEquals(0, provider.decryptions)
    }

    @Test
    fun allowedSnapshotRetainsExactCiphertextAndUsesAuthorizedProvider() {
        val guard = Guard()
        val provider = RecordingDecryptor()
        val preferences = mock<Preferences>()
        val raw = ciphertext()
        whenever(preferences.getString("secret")).thenReturn(raw)
        val snapshot = EncryptedPreferencesImpl(preferences, utility(provider))
            .getAuthorizedDecryptedStringSnapshot("secret", guard, INTENT)
        assertEquals("wallet test payload", snapshot?.plaintext)
        assertEquals(1, provider.decryptions)
        assertEquals(1, guard.checks)
    }

    @Test
    fun productionJcaProviderPreservesLegacyCbcEncodingWithFinalGuard() {
        val guard = Guard()
        val result = utility(JcaWalletPayloadDecryptor).decryptAuthorized(ciphertext(), guard, INTENT)
        assertEquals("wallet test payload", result)
        assertEquals(1, guard.checks)
    }

    @Test
    fun attestationAfterDelayedPreferenceReadAlsoRequiresCurrentAuthorization() {
        WalletSecureStorageHealth.resetForTest()
        val guard = Guard()
        val provider = RecordingDecryptor()
        val context = mock<Context>()
        val wrappedPreferences = mock<SharedPreferences>()
        val walletPreferences = mock<SharedPreferences>()
        whenever(context.getSharedPreferences("key_alias", Context.MODE_PRIVATE)).thenReturn(wrappedPreferences)
        whenever(context.getSharedPreferences(SHARED_PREFERENCES_FILE, Context.MODE_PRIVATE)).thenReturn(walletPreferences)
        val wrappingKey = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val wrapper = Cipher.getInstance("RSA/ECB/PKCS1Padding").apply { init(Cipher.ENCRYPT_MODE, wrappingKey.public) }
        whenever(wrappedPreferences.getString("secret_key", "")).thenReturn(Base64.toBase64String(wrapper.doFinal(KEY)))
        whenever(walletPreferences.all).thenAnswer {
            guard.allowed = false
            mapOf(WalletMasterKeyAttestation.SENTINEL_KEY to ciphertext())
        }
        val util = EncryptionUtil(context, provider)
        // Test-generated key material avoids AndroidKeyStore and exercises the actual
        // unwrap, delayed attestation read, and guarded payload provider chain.
        for ((name, value) in mapOf("privateKey" to wrappingKey.private, "publicKey" to wrappingKey.public)) {
            EncryptionUtil::class.java.getDeclaredField(name).apply { isAccessible = true }.set(util, value)
        }
        assertThrows(WalletSecureStorageUnavailableException::class.java) {
            util.decryptAuthorized(ciphertext(), guard, INTENT)
        }
        assertEquals(2, guard.checks) // Wrapping-key unwrap, then denied attestation decrypt.
        assertEquals(0, provider.decryptions)
    }

    @Test
    fun unsupportedProviderFailsClosedWithoutUsingOrdinaryDecrypt() {
        var ordinaryCalls = 0
        val provider = WalletPayloadDecryptor { _, _, _, _, _ -> ordinaryCalls++; byteArrayOf(1) }
        assertThrows(WalletSecureStorageUnavailableException::class.java) {
            utility(provider).decryptAuthorized(ciphertext(), Guard(), INTENT)
        }
        assertEquals(0, ordinaryCalls)
    }

    private fun utility(provider: WalletPayloadDecryptor): EncryptionUtil {
        val util = spy(EncryptionUtil(mock<Context>(), provider))
        doReturn(SecretKeySpec(KEY, "AES")).whenever(util).getAuthorizedPreferenceAesKey(any())
        return util
    }

    private class Guard : MutationExecutionGuard {
        var allowed = true
        var checks = 0
        override fun <T> runIfAuthorized(intentSha256: String, operation: () -> T): T {
            assertEquals(INTENT, intentSha256)
            checks++
            check(allowed) { "Expired or revoked" }
            return operation()
        }
    }

    private class RecordingDecryptor(private val prepare: () -> Unit = {}) : WalletPayloadDecryptor {
        var decryptions = 0
        override fun decrypt(
            transformation: String, key: ByteArray, parameters: AlgorithmParameterSpec,
            ciphertext: ByteArray, secureRandom: SecureRandom
        ): ByteArray = error("Ordinary decryption must not be used")
        override fun decryptAuthorized(
            transformation: String, key: ByteArray, parameters: AlgorithmParameterSpec,
            ciphertext: ByteArray, secureRandom: SecureRandom, authorization: WalletPayloadAuthorization
        ): ByteArray {
            prepare()
            return authorization.run { decryptions++; "wallet test payload".toByteArray() }
        }
    }

    private fun ciphertext(): String {
        val iv = ByteArray(16) { 7 }
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(KEY, "AES"), IvParameterSpec(iv))
        return Base64.toBase64String(iv + cipher.doFinal("wallet test payload".toByteArray()))
    }

    private companion object {
        val KEY = ByteArray(32) { 3 }
        const val INTENT = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    }
}
