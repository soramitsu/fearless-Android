package jp.co.soramitsu.common.data.storage.encrypt

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.security.KeyStore
import java.security.ProviderException
import jp.co.soramitsu.common.di.modules.SHARED_PREFERENCES_FILE
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EncryptionUtilAndroidKeyStoreTest {

    private lateinit var context: Context

    @Before
    fun resetIsolatedTestStorage() {
        context = ApplicationProvider.getApplicationContext()
        check(context.packageName != PRODUCTION_PACKAGE) {
            "AndroidKeyStore tests must never run in the production wallet UID"
        }
        clearTestStorage()
    }

    @After
    fun clearIsolatedTestStorage() {
        clearTestStorage()
    }

    @Test
    fun freshInstallPersistsRecoversAndAuthenticatesExactAesKey() {
        val first = EncryptionUtil(context)
        val firstKey = first.getPrerenceAesKey().encoded
        val ciphertext = first.encrypt(TEST_PLAINTEXT)

        assertEquals(32, firstKey.size)
        assertTrue(ciphertext.startsWith("v2:"))
        assertTrue(keyPreferences().contains(WRAPPED_KEY_FIELD))
        assertTrue(
            walletPreferences().contains(
                WalletMasterKeyAttestation.SENTINEL_KEY
            )
        )
        assertTrue(androidKeyStore().containsAlias(KEY_ALIAS))

        val recreated = EncryptionUtil(context)
        assertArrayEquals(
            firstKey,
            recreated.getPrerenceAesKey().encoded
        )
        assertEquals(TEST_PLAINTEXT, recreated.decrypt(ciphertext))
    }

    @Test
    fun missingKeystoreAliasNeverCreatesReplacementForExistingWrappedKey() {
        EncryptionUtil(context).getPrerenceAesKey()
        val wrappedKey = checkNotNull(
            keyPreferences().getString(WRAPPED_KEY_FIELD, null)
        )
        androidKeyStore().deleteEntry(KEY_ALIAS)

        val failure = assertThrows(
            WalletSecureStorageUnavailableException::class.java
        ) {
            EncryptionUtil(context).getPrerenceAesKey()
        }

        assertEquals(
            WalletSecureStorageFailureKind.PERMANENT_KEY_LOSS,
            failure.kind
        )
        assertEquals(
            wrappedKey,
            keyPreferences().getString(WRAPPED_KEY_FIELD, null)
        )
        assertFalse(androidKeyStore().containsAlias(KEY_ALIAS))
    }

    @Test
    fun missingWrappedKeyNeverCreatesReplacementWhileProtectedDataRemains() {
        EncryptionUtil(context).getPrerenceAesKey()
        val originalCertificate = androidKeyStore()
            .getCertificate(KEY_ALIAS)
            .encoded
        assertTrue(
            keyPreferences().edit()
                .remove(WRAPPED_KEY_FIELD)
                .commit()
        )

        val failure = assertThrows(
            WalletSecureStorageUnavailableException::class.java
        ) {
            EncryptionUtil(context).getPrerenceAesKey()
        }

        assertEquals(
            WalletSecureStorageFailureKind.PERMANENT_KEY_LOSS,
            failure.kind
        )
        assertFalse(keyPreferences().contains(WRAPPED_KEY_FIELD))
        assertArrayEquals(
            originalCertificate,
            androidKeyStore().getCertificate(KEY_ALIAS).encoded
        )
    }

    @Test
    fun hostileWrappedKeyRepresentationsFailPermanentlyWithoutMutation() {
        EncryptionUtil(context).getPrerenceAesKey()

        val hostileValues = listOf<Any>(
            7,
            "not-valid-base64!?",
            "A".repeat(MAX_WRAPPED_KEY_CHARS + 1)
        )
        hostileValues.forEach { hostileValue ->
            val editor = keyPreferences().edit().clear()
            when (hostileValue) {
                is Int -> editor.putInt(WRAPPED_KEY_FIELD, hostileValue)
                is String -> editor.putString(
                    WRAPPED_KEY_FIELD,
                    hostileValue
                )
            }
            assertTrue(editor.commit())

            val failure = assertThrows(
                WalletSecureStorageUnavailableException::class.java
            ) {
                EncryptionUtil(context).getPrerenceAesKey()
            }

            assertEquals(
                WalletSecureStorageFailureKind.PERMANENT_KEY_LOSS,
                failure.kind
            )
            assertEquals(
                hostileValue,
                keyPreferences().all[WRAPPED_KEY_FIELD]
            )
        }
    }

    @Test
    fun transientPinDecryptProviderFailureRemainsRetryableAndSideEffectFree() {
        val healthyEncryption = EncryptionUtil(context)
        healthyEncryption.getPrerenceAesKey()
        val encryptedPin = healthyEncryption.encrypt(TEST_PIN)
        assertTrue(encryptedPin.startsWith("v2:"))
        assertTrue(
            walletPreferences().edit()
                .putString(WalletMasterKeyAttestation.PIN_CODE_KEY, encryptedPin)
                .remove(WalletMasterKeyAttestation.SENTINEL_KEY)
                .commit()
        )
        val wrappedKey = checkNotNull(
            keyPreferences().getString(WRAPPED_KEY_FIELD, null)
        )
        val providerFailure = ProviderException("temporary provider outage")
        val unavailableEncryption = EncryptionUtil(
            context = context,
            payloadDecryptor = WalletPayloadDecryptor { _, _, _, _, _ ->
                throw providerFailure
            }
        )

        val failure = assertThrows(
            WalletSecureStorageUnavailableException::class.java
        ) {
            unavailableEncryption.getPrerenceAesKey()
        }

        assertEquals(WalletSecureStorageFailureKind.RETRYABLE, failure.kind)
        assertSame(providerFailure, failure.cause)
        assertEquals(
            encryptedPin,
            walletPreferences().getString(
                WalletMasterKeyAttestation.PIN_CODE_KEY,
                null
            )
        )
        assertFalse(
            walletPreferences().contains(WalletMasterKeyAttestation.SENTINEL_KEY)
        )
        assertEquals(
            wrappedKey,
            keyPreferences().getString(WRAPPED_KEY_FIELD, null)
        )
    }

    private fun clearTestStorage() {
        walletPreferences().edit().clear().commit()
        keyPreferences().edit().clear().commit()
        context.deleteDatabase(APP_DATABASE_NAME)
        androidKeyStore().let { keyStore ->
            if (keyStore.containsAlias(KEY_ALIAS)) {
                keyStore.deleteEntry(KEY_ALIAS)
            }
        }
    }

    private fun walletPreferences() = context.getSharedPreferences(
        SHARED_PREFERENCES_FILE,
        Context.MODE_PRIVATE
    )

    private fun keyPreferences() = context.getSharedPreferences(
        KEY_ALIAS,
        Context.MODE_PRIVATE
    )

    private fun androidKeyStore(): KeyStore {
        return KeyStore.getInstance(ANDROID_KEY_STORE).apply {
            load(null)
        }
    }

    private companion object {
        const val PRODUCTION_PACKAGE = "jp.co.soramitsu.fearless"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "key_alias"
        const val WRAPPED_KEY_FIELD = "secret_key"
        const val APP_DATABASE_NAME = "app.db"
        const val MAX_WRAPPED_KEY_CHARS = 4_096
        const val TEST_PLAINTEXT = "isolated-wallet-keystore-test"
        const val TEST_PIN = "123456"
    }
}
