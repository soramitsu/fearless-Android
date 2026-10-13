package jp.co.soramitsu.common.data.storage.encrypt

import android.content.Context
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.security.GeneralSecurityException
import java.security.InvalidAlgorithmParameterException
import java.security.InvalidKeyException
import java.security.KeyStore
import java.security.NoSuchAlgorithmException
import java.security.ProviderException
import javax.crypto.Cipher
import javax.crypto.NoSuchPaddingException
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import jp.co.soramitsu.common.data.secrets.v1.SourceInternal
import jp.co.soramitsu.common.data.secrets.v1.SourceType
import jp.co.soramitsu.common.data.secrets.v2.KeyPairSchema
import jp.co.soramitsu.common.data.secrets.v2.MetaAccountSecrets
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecretStore
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecrets
import jp.co.soramitsu.common.data.storage.PreferencesImpl
import jp.co.soramitsu.common.di.modules.SHARED_PREFERENCES_FILE
import jp.co.soramitsu.common.utils.invoke
import jp.co.soramitsu.common.utils.substrateAccountId
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.fearless_utils.encrypt.keypair.BaseKeypair
import jp.co.soramitsu.fearless_utils.encrypt.keypair.ethereum.EthereumKeypairFactory
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.scale.toHexString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EncryptionUtilSafetyTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        clearTestStorage()
    }

    @After
    fun tearDown() {
        clearTestStorage()
    }

    @Test
    fun freshInstallCreatesAndReusesOneDurableAesKey() {
        val encryptionUtil = EncryptionUtil(context)

        val first = encryptionUtil.getPrerenceAesKey().encoded
        val wrappedKey = keyPreferences().getString(SECRET_KEY, null)
        val sentinel = walletPreferences().getString(
            WalletMasterKeyAttestation.SENTINEL_KEY,
            null
        )
        val second = encryptionUtil.getPrerenceAesKey().encoded

        assertTrue(first.size in setOf(16, 24, 32))
        assertTrue(!wrappedKey.isNullOrBlank())
        assertTrue(!sentinel.isNullOrBlank())
        assertTrue(encryptionUtil.isModernCiphertext(sentinel))
        assertEquals(
            WalletMasterKeyAttestation.SENTINEL_PLAINTEXT,
            encryptionUtil.decrypt(first, checkNotNull(sentinel))
        )
        assertTrue(first.contentEquals(second))
    }

    @Test
    fun existingWalletRowsWithoutAnyProofRejectEvenCorrectWrappedKey() {
        val originalUtil = EncryptionUtil(context)
        val originalKey = originalUtil.getPrerenceAesKey().encoded
        val exactWrappedKey = keyPreferences().getString(SECRET_KEY, null)
        createUsersDatabase(rowCount = 1)
        walletPreferences().edit().clear().commit()

        assertThrows(WalletSecureStorageUnavailableException::class.java) {
            EncryptionUtil(context).getPrerenceAesKey()
        }

        assertEquals(
            exactWrappedKey,
            keyPreferences().getString(SECRET_KEY, null)
        )
        assertEquals(1, usersRowCount())
        assertFalse(
            walletPreferences().contains(WalletMasterKeyAttestation.SENTINEL_KEY)
        )
        assertTrue(
            originalKey.contentEquals(
                unwrapExistingKeyForTest(checkNotNull(exactWrappedKey))
            )
        )
    }

    @Test
    fun existingWalletRowsCannotCementValidButWrongWrappedKeyWithoutProof() {
        val originalUtil = EncryptionUtil(context)
        val originalKey = originalUtil.getPrerenceAesKey().encoded
        createUsersDatabase(rowCount = 1)
        val replacementKey = ByteArray(32) { index -> (index + 101).toByte() }
        assertFalse(originalKey.contentEquals(replacementKey))
        val exactWrongWrappedKey = wrapWithExistingAlias(replacementKey)
        keyPreferences().edit()
            .putString(SECRET_KEY, exactWrongWrappedKey)
            .commit()
        walletPreferences().edit().clear().commit()

        assertThrows(WalletSecureStorageUnavailableException::class.java) {
            EncryptionUtil(context).getPrerenceAesKey()
        }

        assertEquals(
            exactWrongWrappedKey,
            keyPreferences().getString(SECRET_KEY, null)
        )
        assertEquals(1, usersRowCount())
        assertFalse(
            walletPreferences().contains(WalletMasterKeyAttestation.SENTINEL_KEY)
        )
    }

    @Test
    fun emptyWalletDatabaseStillAllowsExistingKeySentinelBackfill() {
        val originalUtil = EncryptionUtil(context)
        val originalKey = originalUtil.getPrerenceAesKey().encoded
        val exactWrappedKey = keyPreferences().getString(SECRET_KEY, null)
        createUsersDatabase(rowCount = 0)
        walletPreferences().edit().clear().commit()

        val reopened = EncryptionUtil(context)
        val reopenedKey = reopened.getPrerenceAesKey().encoded

        assertTrue(originalKey.contentEquals(reopenedKey))
        assertEquals(
            exactWrappedKey,
            keyPreferences().getString(SECRET_KEY, null)
        )
        assertEquals(0, usersRowCount())
        assertTrue(
            reopened.isModernCiphertext(
                walletPreferences().getString(
                    WalletMasterKeyAttestation.SENTINEL_KEY,
                    null
                )
            )
        )
    }

    @Test
    fun unreadableExistingDatabaseCannotBackfillSentinelWithoutProof() {
        val originalUtil = EncryptionUtil(context)
        originalUtil.getPrerenceAesKey()
        val exactWrappedKey = keyPreferences().getString(SECRET_KEY, null)
        walletPreferences().edit().clear().commit()
        context.deleteDatabase(APP_DATABASE_NAME)
        val databaseFile = context.getDatabasePath(APP_DATABASE_NAME)
        checkNotNull(databaseFile.parentFile).mkdirs()
        val exactInvalidDatabase = "not-a-sqlite-database".toByteArray()
        databaseFile.writeBytes(exactInvalidDatabase)

        assertThrows(WalletSecureStorageUnavailableException::class.java) {
            EncryptionUtil(context).getPrerenceAesKey()
        }

        assertEquals(
            exactWrappedKey,
            keyPreferences().getString(SECRET_KEY, null)
        )
        assertTrue(exactInvalidDatabase.contentEquals(databaseFile.readBytes()))
        assertFalse(
            walletPreferences().contains(WalletMasterKeyAttestation.SENTINEL_KEY)
        )
    }

    @Test
    fun validWrappedButWrongAesKeyBlocksAllMigrationAndPreservesEveryCiphertext() {
        val originalUtil = EncryptionUtil(context)
        val originalKey = originalUtil.getPrerenceAesKey().encoded
        val originalCiphertexts = mapOf(
            PIN_CODE to encryptLegacy(originalKey, "123456"),
            "${LEGACY_SECRET_KEY}-a" to encryptLegacy(originalKey, "0011223344556677"),
            "${LEGACY_SECRET_KEY}-b" to encryptLegacy(originalKey, "8899aabbccddeeff")
        )

        // Reproduce the historical crash window: a release overwrote the
        // wrapped AES key with a new, perfectly valid key while old encrypted
        // wallet material remained on disk.
        walletPreferences().edit().clear().commit()
        keyPreferences().edit().clear().commit()
        val replacementKey = EncryptionUtil(context).getPrerenceAesKey().encoded
        assertFalse(originalKey.contentEquals(replacementKey))
        val validButWrongWrappedKey = keyPreferences().getString(SECRET_KEY, null)
        walletPreferences().edit().clear().apply {
            originalCiphertexts.forEach { (field, ciphertext) ->
                putString(field, ciphertext)
            }
        }.commit()

        assertThrows(WalletSecureStorageUnavailableException::class.java) {
            EncryptionUtil(context).getPrerenceAesKey()
        }

        assertEquals(
            validButWrongWrappedKey,
            keyPreferences().getString(SECRET_KEY, null)
        )
        originalCiphertexts.forEach { (field, ciphertext) ->
            assertEquals(ciphertext, walletPreferences().getString(field, null))
        }
        assertFalse(
            walletPreferences().contains(WalletMasterKeyAttestation.SENTINEL_KEY)
        )
        assertFalse(
            walletPreferences().all.keys.any {
                it.startsWith("wallet_secret_quarantine:")
            }
        )
    }

    @Test
    fun existingLegacyPinAuthenticatesOldKeyAndBackfillsDurableSentinel() {
        val originalUtil = EncryptionUtil(context)
        val key = originalUtil.getPrerenceAesKey().encoded
        val pinCiphertext = encryptLegacy(key, "123456")
        walletPreferences().edit()
            .clear()
            .putString(PIN_CODE, pinCiphertext)
            .commit()

        val reopened = EncryptionUtil(context)
        val reopenedKey = reopened.getPrerenceAesKey().encoded
        val sentinel = walletPreferences().getString(
            WalletMasterKeyAttestation.SENTINEL_KEY,
            null
        )

        assertTrue(key.contentEquals(reopenedKey))
        assertEquals(pinCiphertext, walletPreferences().getString(PIN_CODE, null))
        assertTrue(reopened.isModernCiphertext(sentinel))
        assertEquals(
            WalletMasterKeyAttestation.SENTINEL_PLAINTEXT,
            reopened.decrypt(reopenedKey, checkNotNull(sentinel))
        )
    }

    @Test
    fun realLegacySecretAloneAuthenticatesOldKeyAndBackfillsSentinel() {
        val originalUtil = EncryptionUtil(context)
        val key = originalUtil.getPrerenceAesKey().encoded
        val source = SourceInternal {
            it[Type] = SourceType.SEED.name
            it[PrivateKey] = ByteArray(32) { index -> (index + 1).toByte() }
            it[PublicKey] = ByteArray(32) { index -> (index + 33).toByte() }
            it[Nonce] = null
            it[Seed] = ByteArray(32) { index -> (index + 65).toByte() }
            it[Mnemonic] = null
            it[DerivationPath] = null
        }
        val encodedSource = SourceInternal.toHexString(source)
        val exactCiphertext = encryptLegacy(key, encodedSource)
        walletPreferences().edit()
            .clear()
            .putString(LEGACY_SECRET_KEY, exactCiphertext)
            .commit()

        val reopened = EncryptionUtil(context)
        assertTrue(key.contentEquals(reopened.getPrerenceAesKey().encoded))
        assertEquals(
            exactCiphertext,
            walletPreferences().getString(LEGACY_SECRET_KEY, null)
        )
        assertTrue(
            reopened.isModernCiphertext(
                walletPreferences().getString(
                    WalletMasterKeyAttestation.SENTINEL_KEY,
                    null
                )
            )
        )
    }

    @Test
    fun preTonV69AccessSecretAloneAuthenticatesAndBackfillsSentinel() {
        val originalUtil = EncryptionUtil(context)
        val key = originalUtil.getPrerenceAesKey().encoded
        val currentSecrets = MetaAccountSecrets(
            substrateKeyPair = BaseKeypair(
                privateKey = ByteArray(32) { index -> (index + 1).toByte() },
                publicKey = ByteArray(32) { index -> (index + 33).toByte() }
            ),
            seed = ByteArray(32) { index -> (index + 65).toByte() }
        )
        val currentBytes = MetaAccountSecrets.toByteArray(currentSecrets)
        assertTrue(currentBytes.last() == 0.toByte())
        val historicalV69 = currentBytes
            .copyOf(currentBytes.size - 1)
            .toHexString(withPrefix = true)
        val exactCiphertext = encryptLegacy(key, historicalV69)
        walletPreferences().edit()
            .clear()
            .putString("7:ACCESS_SECRETS", exactCiphertext)
            .commit()

        val reopened = EncryptionUtil(context)
        assertTrue(key.contentEquals(reopened.getPrerenceAesKey().encoded))
        assertEquals(
            exactCiphertext,
            walletPreferences().getString("7:ACCESS_SECRETS", null)
        )
        assertTrue(
            reopened.isModernCiphertext(
                walletPreferences().getString(
                    WalletMasterKeyAttestation.SENTINEL_KEY,
                    null
                )
            )
        )
    }

    @Test
    fun releasedV04PrivatePreferenceAloneAuthenticatesAndBackfillsSentinel() {
        val originalUtil = EncryptionUtil(context)
        val key = originalUtil.getPrerenceAesKey().encoded
        val signingData = KeyPairSchema {
            it[PrivateKey] = ByteArray(32) { index -> (index + 1).toByte() }
            it[PublicKey] = ByteArray(32) { index -> (index + 33).toByte() }
            it[Nonce] = ByteArray(64) { index -> (index + 65).toByte() }
        }
        val exactCiphertext = encryptLegacy(
            key,
            KeyPairSchema.toHexString(signingData)
        )
        val oldKey = "private_1ReleasedLegacyAddress"
        walletPreferences().edit()
            .clear()
            .putString(oldKey, exactCiphertext)
            .commit()

        val reopened = EncryptionUtil(context)
        assertTrue(key.contentEquals(reopened.getPrerenceAesKey().encoded))
        assertEquals(
            exactCiphertext,
            walletPreferences().getString(oldKey, null)
        )
        assertTrue(
            reopened.isModernCiphertext(
                walletPreferences().getString(
                    WalletMasterKeyAttestation.SENTINEL_KEY,
                    null
                )
            )
        )
    }

    @Test
    fun wrongAesKeyCannotIgnoreReleasedV04WalletCiphertext() {
        val originalUtil = EncryptionUtil(context)
        val originalKey = originalUtil.getPrerenceAesKey().encoded
        val signingData = KeyPairSchema {
            it[PrivateKey] = ByteArray(32) { index -> (index + 1).toByte() }
            it[PublicKey] = ByteArray(32) { index -> (index + 33).toByte() }
            it[Nonce] = null
        }
        val oldKey = "private_1ReleasedLegacyAddress"
        val exactCiphertext = encryptLegacy(
            originalKey,
            KeyPairSchema.toHexString(signingData)
        )

        walletPreferences().edit().clear().commit()
        keyPreferences().edit().clear().commit()
        val replacementKey = EncryptionUtil(context).getPrerenceAesKey().encoded
        assertFalse(originalKey.contentEquals(replacementKey))
        val exactWrongWrappedKey = keyPreferences().getString(SECRET_KEY, null)
        walletPreferences().edit()
            .clear()
            .putString(oldKey, exactCiphertext)
            .commit()

        assertThrows(WalletSecureStorageUnavailableException::class.java) {
            EncryptionUtil(context).getPrerenceAesKey()
        }

        assertEquals(
            exactWrongWrappedKey,
            keyPreferences().getString(SECRET_KEY, null)
        )
        assertEquals(
            exactCiphertext,
            walletPreferences().getString(oldKey, null)
        )
        assertFalse(
            walletPreferences().contains(WalletMasterKeyAttestation.SENTINEL_KEY)
        )
    }

    @Test
    fun corruptSentinelIsRepairedOnlyAfterExistingPinAuthenticatesTheKey() {
        val originalUtil = EncryptionUtil(context)
        val key = originalUtil.getPrerenceAesKey().encoded
        val pinCiphertext = encryptLegacy(key, "654321")
        walletPreferences().edit()
            .putString(PIN_CODE, pinCiphertext)
            .putString(
                WalletMasterKeyAttestation.SENTINEL_KEY,
                EXACT_WALLET_CIPHERTEXT
            )
            .commit()

        val reopened = EncryptionUtil(context)
        reopened.getPrerenceAesKey()
        val repaired = walletPreferences().getString(
            WalletMasterKeyAttestation.SENTINEL_KEY,
            null
        )

        assertTrue(reopened.isModernCiphertext(repaired))
        assertEquals(
            WalletMasterKeyAttestation.SENTINEL_PLAINTEXT,
            reopened.decrypt(key, checkNotNull(repaired))
        )
        assertEquals(pinCiphertext, walletPreferences().getString(PIN_CODE, null))
    }

    @Test
    fun validSentinelNeverMasksMalformedOrInvalidPinCiphertext() {
        val originalUtil = EncryptionUtil(context)
        val key = originalUtil.getPrerenceAesKey().encoded
        val sentinel = checkNotNull(
            walletPreferences().getString(
                WalletMasterKeyAttestation.SENTINEL_KEY,
                null
            )
        )
        val invalidPins = listOf(
            EXACT_WALLET_CIPHERTEXT,
            encryptLegacy(key, ""),
            encryptLegacy(key, "12345"),
            encryptLegacy(key, "1234567"),
            encryptLegacy(key, "12a456")
        )

        invalidPins.forEach { exactPinCiphertext ->
            walletPreferences().edit()
                .clear()
                .putString(WalletMasterKeyAttestation.SENTINEL_KEY, sentinel)
                .putString(PIN_CODE, exactPinCiphertext)
                .commit()

            assertThrows(WalletSecureStorageUnavailableException::class.java) {
                EncryptionUtil(context).getPrerenceAesKey()
            }
            assertEquals(
                exactPinCiphertext,
                walletPreferences().getString(PIN_CODE, null)
            )
            assertEquals(
                sentinel,
                walletPreferences().getString(
                    WalletMasterKeyAttestation.SENTINEL_KEY,
                    null
                )
            )
        }
    }

    @Test
    fun validSentinelNeverMasksWrongTypePinAndPreservesIt() {
        val originalUtil = EncryptionUtil(context)
        originalUtil.getPrerenceAesKey()
        walletPreferences().edit().putInt(PIN_CODE, WRONG_TYPE_SENTINEL).commit()

        assertThrows(WalletSecureStorageUnavailableException::class.java) {
            EncryptionUtil(context).getPrerenceAesKey()
        }

        assertEquals(
            WRONG_TYPE_SENTINEL,
            walletPreferences().getInt(PIN_CODE, -1)
        )
    }

    @Test
    fun malformedPinPreventsSentinelBackfillEvenWhenAnotherPayloadAuthenticates() {
        val originalUtil = EncryptionUtil(context)
        val key = originalUtil.getPrerenceAesKey().encoded
        val walletKey = "17:SUBSTRATE_SECRETS"
        val exactWalletCiphertext = originalUtil.encrypt(
            key,
            "authenticated-wallet-payload"
        )
        val exactInvalidPinCiphertext = encryptLegacy(key, "12x456")
        walletPreferences().edit()
            .clear()
            .putString(walletKey, exactWalletCiphertext)
            .putString(PIN_CODE, exactInvalidPinCiphertext)
            .commit()

        assertThrows(WalletSecureStorageUnavailableException::class.java) {
            EncryptionUtil(context).getPrerenceAesKey()
        }

        assertFalse(
            walletPreferences().contains(WalletMasterKeyAttestation.SENTINEL_KEY)
        )
        assertEquals(
            exactWalletCiphertext,
            walletPreferences().getString(walletKey, null)
        )
        assertEquals(
            exactInvalidPinCiphertext,
            walletPreferences().getString(PIN_CODE, null)
        )
    }

    @Test
    fun corruptSentinelWithoutAnotherAuthenticationProofIsNeverOverwritten() {
        val originalUtil = EncryptionUtil(context)
        originalUtil.getPrerenceAesKey()
        walletPreferences().edit()
            .putString(
                WalletMasterKeyAttestation.SENTINEL_KEY,
                EXACT_WALLET_CIPHERTEXT
            )
            .commit()

        assertThrows(WalletSecureStorageUnavailableException::class.java) {
            EncryptionUtil(context).getPrerenceAesKey()
        }

        assertEquals(
            EXACT_WALLET_CIPHERTEXT,
            walletPreferences().getString(
                WalletMasterKeyAttestation.SENTINEL_KEY,
                null
            )
        )
    }

    @Test
    fun validQuarantinedModernCiphertextAuthenticatesKeyWithoutChangingIt() {
        val originalUtil = EncryptionUtil(context)
        val originalKey = originalUtil.getPrerenceAesKey().encoded
        val quarantineKey = WalletSecretQuarantine.keyFor(
            "41:SUBSTRATE_SECRETS"
        )
        val exactCiphertext = originalUtil.encrypt(
            originalKey,
            "authenticated-quarantine-payload"
        )
        walletPreferences().edit()
            .clear()
            .putString(quarantineKey, exactCiphertext)
            .commit()

        val reopened = EncryptionUtil(context)
        assertTrue(
            originalKey.contentEquals(reopened.getPrerenceAesKey().encoded)
        )
        assertEquals(
            exactCiphertext,
            walletPreferences().getString(quarantineKey, null)
        )
        assertTrue(
            reopened.isModernCiphertext(
                walletPreferences().getString(
                    WalletMasterKeyAttestation.SENTINEL_KEY,
                    null
                )
            )
        )
    }

    @Test
    fun legacyV1QuarantineAloneAuthenticatesItsBoundKeyAndRemainsExact() {
        val originalUtil = EncryptionUtil(context)
        val key = originalUtil.getPrerenceAesKey().encoded
        val publicKey = ByteArray(32) { index -> (index + 33).toByte() }
        val source = SourceInternal {
            it[Type] = SourceType.SEED.name
            it[PrivateKey] = ByteArray(32) { index -> (index + 1).toByte() }
            it[PublicKey] = publicKey
            it[Nonce] = null
            it[Seed] = ByteArray(32) { index -> (index + 65).toByte() }
            it[Mnemonic] = null
            it[DerivationPath] = null
        }
        val quarantineKey =
            WalletSecretQuarantine.legacyV1KeyForPublicKey(publicKey)
        val exactCiphertext = encryptLegacy(
            key,
            SourceInternal.toHexString(source)
        )
        walletPreferences().edit()
            .clear()
            .putString(quarantineKey, exactCiphertext)
            .commit()

        val reopened = EncryptionUtil(context)
        assertTrue(key.contentEquals(reopened.getPrerenceAesKey().encoded))
        assertEquals(
            exactCiphertext,
            walletPreferences().getString(quarantineKey, null)
        )
        assertTrue(
            reopened.isModernCiphertext(
                walletPreferences().getString(
                    WalletMasterKeyAttestation.SENTINEL_KEY,
                    null
                )
            )
        )
    }

    @Test
    fun wrongAesKeyCannotBypassAttestationWhenOnlyQuarantineSurvives() {
        val originalUtil = EncryptionUtil(context)
        val originalKey = originalUtil.getPrerenceAesKey().encoded
        val quarantineKey = WalletSecretQuarantine.keyFor(
            "73:ETHEREUM_SECRETS"
        )
        val exactCiphertext = originalUtil.encrypt(
            originalKey,
            "quarantined-wallet-material"
        )

        walletPreferences().edit().clear().commit()
        keyPreferences().edit().clear().commit()
        val replacementUtil = EncryptionUtil(context)
        val replacementKey = replacementUtil.getPrerenceAesKey().encoded
        assertFalse(originalKey.contentEquals(replacementKey))
        val validButWrongWrappedKey = keyPreferences().getString(SECRET_KEY, null)
        walletPreferences().edit()
            .clear()
            .putString(quarantineKey, exactCiphertext)
            .commit()

        assertThrows(WalletSecureStorageUnavailableException::class.java) {
            EncryptionUtil(context).getPrerenceAesKey()
        }

        assertEquals(
            validButWrongWrappedKey,
            keyPreferences().getString(SECRET_KEY, null)
        )
        assertEquals(
            exactCiphertext,
            walletPreferences().getString(quarantineKey, null)
        )
        assertFalse(
            walletPreferences().contains(WalletMasterKeyAttestation.SENTINEL_KEY)
        )
    }

    @Test
    fun malformedWrappedKeyIsGlobalFailureAndIsNeverReplaced() {
        EncryptionUtil(context).getPrerenceAesKey()
        keyPreferences().edit().putString(SECRET_KEY, MALFORMED_WRAPPED_KEY).commit()
        walletPreferences().edit()
            .putString(LEGACY_SECRET_KEY, EXACT_WALLET_CIPHERTEXT)
            .commit()

        assertThrows(WalletSecureStorageUnavailableException::class.java) {
            EncryptionUtil(context).getPrerenceAesKey()
        }

        assertEquals(
            MALFORMED_WRAPPED_KEY,
            keyPreferences().getString(SECRET_KEY, null)
        )
        assertEquals(
            EXACT_WALLET_CIPHERTEXT,
            walletPreferences().getString(LEGACY_SECRET_KEY, null)
        )
        assertFalse(
            walletPreferences().all.keys.any {
                it.startsWith("wallet_secret_quarantine:")
            }
        )
    }

    @Test
    fun validWrappedKeyWithMissingAliasNeverRecreatesAliasOrMutatesPreferences() {
        EncryptionUtil(context).getPrerenceAesKey()
        val exactWrappedKey = keyPreferences().getString(SECRET_KEY, null)
        val exactSentinel = walletPreferences().getString(
            WalletMasterKeyAttestation.SENTINEL_KEY,
            null
        )
        val keyStore = walletKeyStore()
        keyStore.deleteEntry(KEY_ALIAS)
        assertFalse(keyStore.containsAlias(KEY_ALIAS))

        assertThrows(WalletSecureStorageUnavailableException::class.java) {
            EncryptionUtil(context).getPrerenceAesKey()
        }

        assertFalse(walletKeyStore().containsAlias(KEY_ALIAS))
        assertEquals(
            exactWrappedKey,
            keyPreferences().getString(SECRET_KEY, null)
        )
        assertEquals(
            exactSentinel,
            walletPreferences().getString(
                WalletMasterKeyAttestation.SENTINEL_KEY,
                null
            )
        )
    }

    @Test
    fun sameInstanceCannotUseCachedAttestationAfterPinBecomesWrongType() {
        val encryptionUtil = EncryptionUtil(context)
        encryptionUtil.getPrerenceAesKey()
        val exactSentinel = walletPreferences().getString(
            WalletMasterKeyAttestation.SENTINEL_KEY,
            null
        )
        walletPreferences().edit()
            .putInt(PIN_CODE, WRONG_TYPE_SENTINEL)
            .commit()

        assertThrows(WalletSecureStorageUnavailableException::class.java) {
            encryptionUtil.getPrerenceAesKey()
        }

        assertEquals(
            WRONG_TYPE_SENTINEL,
            walletPreferences().getInt(PIN_CODE, -1)
        )
        assertEquals(
            exactSentinel,
            walletPreferences().getString(
                WalletMasterKeyAttestation.SENTINEL_KEY,
                null
            )
        )
    }

    @Test
    fun wrongWrappedKeyPreferenceTypeIsGlobalFailureAndRemainsUntouched() {
        EncryptionUtil(context)
        keyPreferences().edit().putInt(SECRET_KEY, WRONG_TYPE_SENTINEL).commit()

        assertThrows(WalletSecureStorageUnavailableException::class.java) {
            EncryptionUtil(context).getPrerenceAesKey()
        }

        assertEquals(WRONG_TYPE_SENTINEL, keyPreferences().getInt(SECRET_KEY, -1))
    }

    @Test
    fun missingWrappedKeyWithExistingEncryptedWalletNeverGeneratesReplacement() {
        walletPreferences().edit()
            .putString(LEGACY_SECRET_KEY, EXACT_WALLET_CIPHERTEXT)
            .commit()

        assertThrows(WalletSecureStorageUnavailableException::class.java) {
            EncryptionUtil(context).getPrerenceAesKey()
        }

        assertFalse(keyPreferences().contains(SECRET_KEY))
        assertEquals(
            EXACT_WALLET_CIPHERTEXT,
            walletPreferences().getString(LEGACY_SECRET_KEY, null)
        )
    }

    @Test
    fun payloadProviderFailuresAreGlobalAndNeverCollapseToLocalCorruption() {
        val normalUtil = EncryptionUtil(context)
        val key = normalUtil.getPrerenceAesKey().encoded
        val ciphertext = normalUtil.encrypt(key, "provider-failure-probe")
        val providerFailures = listOf(
            NoSuchAlgorithmException("missing payload algorithm"),
            NoSuchPaddingException("missing payload padding"),
            InvalidKeyException("rejected payload key"),
            InvalidAlgorithmParameterException("rejected payload parameters"),
            ProviderException("payload provider failed"),
            GeneralSecurityException("unknown payload cipher failure"),
            SecurityException("payload cipher permission failure"),
            RuntimeException("unknown payload provider runtime failure")
        )

        providerFailures.forEach { providerFailure ->
            val failingUtil = EncryptionUtilTestFactory.withPayloadDecryptFailure(
                context = context,
                transformation = AES_GCM_TRANSFORMATION,
                failure = providerFailure
            )

            val thrown = assertThrows(
                WalletSecureStorageUnavailableException::class.java
            ) {
                failingUtil.decrypt(key, ciphertext)
            }

            assertTrue(thrown.cause === providerFailure)
        }
    }

    @Test
    fun fatalPayloadCipherErrorEscapesWithoutReclassification() {
        val normalUtil = EncryptionUtil(context)
        val key = normalUtil.getPrerenceAesKey().encoded
        val ciphertext = normalUtil.encrypt(key, "fatal-provider-probe")
        val fatalFailure = AssertionError("fatal payload provider failure")
        val failingUtil = EncryptionUtil(
            context = context,
            payloadDecryptor = WalletPayloadDecryptor {
                    _,
                    _,
                    _,
                    _,
                    _ ->
                throw fatalFailure
            }
        )

        val thrown = assertThrows(AssertionError::class.java) {
            failingUtil.decrypt(key, ciphertext)
        }

        assertTrue(thrown === fatalFailure)
    }

    @Test
    fun payloadProviderFailurePreservesActiveCiphertextAndRuntimeRetrySucceeds() {
        val keypair = EthereumKeypairFactory.createWithPrivateKey(
            ByteArray(32) { index -> (index + 1).toByte() }
        )
        val publicKey = keypair.publicKey
        val accountId = publicKey.substrateAccountId()
        val plaintext = SubstrateSecrets(
            substrateKeyPair = keypair
        ).toHexString()
        val activeKey = "$META_ID:SUBSTRATE_SECRETS"
        val quarantineKey = WalletSecretQuarantine.keyFor(activeKey)
        val normalPreferences = encryptedPreferences(EncryptionUtil(context))
        normalPreferences.putEncryptedString(activeKey, plaintext)
        val exactCiphertext = checkNotNull(
            walletPreferences().getString(activeKey, null)
        )
        val failingPreferences = encryptedPreferences(
            EncryptionUtilTestFactory.withPayloadDecryptFailure(
                context = context,
                transformation = AES_GCM_TRANSFORMATION,
                successfulMatchingDecryptions = 1,
                failure = ProviderException("transient payload provider failure")
            )
        )

        assertThrows(WalletSecureStorageUnavailableException::class.java) {
            SubstrateSecretStore(failingPreferences).get(
                metaId = META_ID,
                expectedPublicKey = publicKey,
                expectedCryptoType = CryptoType.ECDSA,
                expectedAccountId = accountId
            )
        }

        assertEquals(
            exactCiphertext,
            walletPreferences().getString(activeKey, null)
        )
        assertFalse(walletPreferences().contains(quarantineKey))

        val retried = SubstrateSecretStore(
            encryptedPreferences(EncryptionUtil(context))
        ).get(
            metaId = META_ID,
            expectedPublicKey = publicKey,
            expectedCryptoType = CryptoType.ECDSA,
            expectedAccountId = accountId
        )
        assertTrue(retried != null)
        assertEquals(
            plaintext,
            encryptedPreferences(EncryptionUtil(context))
                .getDecryptedString(activeKey)
        )
    }

    @Test
    fun malformedPayloadIsExactlyQuarantinedByRuntimeReader() {
        EncryptionUtil(context).getPrerenceAesKey()
        val activeKey = "$META_ID:SUBSTRATE_SECRETS"
        val quarantineKey = WalletSecretQuarantine.keyFor(activeKey)
        val exactCorruptCiphertext = "v2:%%%not-base64%%%"
        walletPreferences().edit()
            .putString(activeKey, exactCorruptCiphertext)
            .commit()
        val preferences = encryptedPreferences(EncryptionUtil(context))

        assertThrows(WalletRecoveryRequiredException::class.java) {
            SubstrateSecretStore(preferences).get(META_ID)
        }

        assertFalse(walletPreferences().contains(activeKey))
        assertEquals(
            exactCorruptCiphertext,
            walletPreferences().getString(quarantineKey, null)
        )
    }

    @Test
    fun malformedAuthenticatedAndLegacyPayloadsRemainLocalCorruption() {
        val encryptionUtil = EncryptionUtil(context)
        val key = encryptionUtil.getPrerenceAesKey().encoded
        val validModern = encryptionUtil.encrypt(key, "authenticated payload")
        val invalidModernTag = decodeModernPayload(validModern).also {
            it[it.lastIndex] = (it.last().toInt() xor 1).toByte()
        }.toModernCiphertext()
        val invalidLegacyPadding = Base64.decode(
            encryptLegacy(key, "padding"),
            Base64.NO_WRAP
        ).also {
            // CBC's first plaintext block is XORed with the IV. Flipping the
            // final IV byte deterministically invalidates PKCS#5 padding.
            it[LEGACY_IV_BYTES - 1] =
                (it[LEGACY_IV_BYTES - 1].toInt() xor 1).toByte()
        }.toBase64()
        val corruptPayloads = listOf(
            "v2:%%%not-base64%%%",
            "v2:" + "A".repeat(MAX_ENCRYPTED_CIPHERTEXT_CHARS),
            ByteArray(GCM_MINIMUM_PAYLOAD_BYTES - 1).toModernCiphertext(),
            invalidModernTag,
            invalidLegacyPadding,
            ByteArray(LEGACY_IV_BYTES + 17).toBase64()
        )

        corruptPayloads.forEach { exactCiphertext ->
            assertEquals("", encryptionUtil.decrypt(key, exactCiphertext))
        }
    }

    private fun encryptedPreferences(
        encryptionUtil: EncryptionUtil
    ): EncryptedPreferences {
        return EncryptedPreferencesImpl(
            preferences = PreferencesImpl(walletPreferences()),
            encryptionUtil = encryptionUtil
        )
    }

    private fun decodeModernPayload(ciphertext: String): ByteArray {
        check(ciphertext.startsWith(MODERN_CIPHER_PREFIX))
        return Base64.decode(
            ciphertext.removePrefix(MODERN_CIPHER_PREFIX),
            Base64.NO_WRAP
        )
    }

    private fun ByteArray.toModernCiphertext(): String {
        return MODERN_CIPHER_PREFIX + toBase64()
    }

    private fun ByteArray.toBase64(): String {
        return Base64.encodeToString(this, Base64.NO_WRAP)
    }

    private fun keyPreferences() =
        context.getSharedPreferences(KEY_ALIAS, Context.MODE_PRIVATE)

    private fun walletPreferences() =
        context.getSharedPreferences(SHARED_PREFERENCES_FILE, Context.MODE_PRIVATE)

    private fun createUsersDatabase(rowCount: Int) {
        context.openOrCreateDatabase(
            APP_DATABASE_NAME,
            Context.MODE_PRIVATE,
            null
        ).use { database ->
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS users (id INTEGER PRIMARY KEY)"
            )
            repeat(rowCount) { index ->
                database.execSQL(
                    "INSERT INTO users (id) VALUES (?)",
                    arrayOf(index + 1)
                )
            }
        }
    }

    private fun usersRowCount(): Int {
        return context.openOrCreateDatabase(
            APP_DATABASE_NAME,
            Context.MODE_PRIVATE,
            null
        ).use { database ->
            database.rawQuery("SELECT COUNT(*) FROM users", null).use { cursor ->
                check(cursor.moveToFirst())
                cursor.getInt(0)
            }
        }
    }

    private fun walletKeyStore(): KeyStore {
        return KeyStore.getInstance(KEY_STORE_PROVIDER).apply {
            load(null)
        }
    }

    private fun wrapWithExistingAlias(key: ByteArray): String {
        val publicKey = checkNotNull(
            walletKeyStore().getCertificate(KEY_ALIAS)
        ).publicKey
        val cipher = Cipher.getInstance(RSA_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)
        return Base64.encodeToString(cipher.doFinal(key), Base64.NO_WRAP)
    }

    private fun unwrapExistingKeyForTest(wrappedKey: String): ByteArray {
        val privateKey = checkNotNull(
            walletKeyStore().getKey(KEY_ALIAS, null)
        )
        val cipher = Cipher.getInstance(RSA_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, privateKey)
        return cipher.doFinal(Base64.decode(wrappedKey, Base64.NO_WRAP))
    }

    private fun clearTestStorage() {
        keyPreferences().edit().clear().commit()
        walletPreferences().edit().clear().commit()
        context.deleteDatabase(APP_DATABASE_NAME)

        val keyStore = walletKeyStore()
        if (keyStore.containsAlias(KEY_ALIAS)) {
            keyStore.deleteEntry(KEY_ALIAS)
        }
    }

    private fun encryptLegacy(key: ByteArray, plaintext: String): String {
        val iv = ByteArray(16) { index -> (index + 1).toByte() }
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key, "AES"),
            IvParameterSpec(iv)
        )
        val payload = iv + cipher.doFinal(plaintext.toByteArray())
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    private companion object {
        const val KEY_STORE_PROVIDER = "AndroidKeyStore"
        const val KEY_ALIAS = "key_alias"
        const val SECRET_KEY = "secret_key"
        const val APP_DATABASE_NAME = "app.db"
        const val PIN_CODE = "pin_code"
        const val LEGACY_SECRET_KEY = "security_source_test-wallet"
        const val RSA_TRANSFORMATION = "RSA/ECB/PKCS1Padding"
        const val EXACT_WALLET_CIPHERTEXT = "not-a-wallet-secret\u0000"
        const val MALFORMED_WRAPPED_KEY = "%%%not-base64%%%"
        const val WRONG_TYPE_SENTINEL = 73
        const val META_ID = 42L
        const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        const val MODERN_CIPHER_PREFIX = "v2:"
        const val MAX_ENCRYPTED_CIPHERTEXT_CHARS = 2_097_152
        const val GCM_MINIMUM_PAYLOAD_BYTES = 28
        const val LEGACY_IV_BYTES = 16
    }
}
