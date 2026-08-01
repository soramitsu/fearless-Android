package jp.co.soramitsu.coredb.migrations

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Base64
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import java.security.KeyStore
import java.security.NoSuchAlgorithmException
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import jp.co.soramitsu.common.data.secrets.v1.SecretStoreV1Impl
import jp.co.soramitsu.common.data.secrets.v2.KeyPairSchema
import jp.co.soramitsu.common.data.secrets.v2.SecretStoreV2
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecretStore
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecrets
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecretStore
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecrets
import jp.co.soramitsu.common.data.storage.PreferencesImpl
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferenceSnapshot
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferenceSnapshotMove
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferencesImpl
import jp.co.soramitsu.common.data.storage.encrypt.EncryptionUtil
import jp.co.soramitsu.common.data.storage.encrypt.EncryptionUtilTestFactory
import jp.co.soramitsu.common.data.storage.encrypt.WalletPublicIdentityRecovery
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretQuarantine
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecureStorageUnavailableException
import jp.co.soramitsu.common.di.modules.SHARED_PREFERENCES_FILE
import jp.co.soramitsu.common.utils.deriveSeed32
import jp.co.soramitsu.common.utils.ethereumAddressFromPublicKey
import jp.co.soramitsu.common.utils.invoke
import jp.co.soramitsu.common.utils.substrateAccountId
import jp.co.soramitsu.coredb.AppDatabase
import jp.co.soramitsu.coredb.migrations.fixtures.ReleasedV374DatabaseFixture
import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.junction.BIP32JunctionDecoder
import jp.co.soramitsu.fearless_utils.encrypt.junction.SubstrateJunctionDecoder
import jp.co.soramitsu.fearless_utils.encrypt.keypair.Keypair
import jp.co.soramitsu.fearless_utils.encrypt.keypair.ethereum.EthereumKeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.Sr25519Keypair
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.mnemonic.MnemonicCreator
import jp.co.soramitsu.fearless_utils.encrypt.seed.ethereum.EthereumSeedFactory
import jp.co.soramitsu.fearless_utils.encrypt.seed.substrate.SubstrateSeedFactory
import jp.co.soramitsu.fearless_utils.extensions.toHexString as toPlainHexString
import jp.co.soramitsu.fearless_utils.scale.toHexString
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises the real Room 71 -> 77 production open path from an immutable
 * binary fixture synthesized from DDL and a Room identity extracted from the
 * official Fearless Wallet 3.7.4 (209) APK.
 *
 * All wallet material below is deterministic synthetic test data.
 */
class TonMigrationSafetyTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @After
    fun deleteTestDatabases() {
        DATABASE_NAMES.forEach(context::deleteDatabase)
    }

    @Test
    fun frozenVersion69SecretMatchesReleaseProvenanceAndRoundTripsByteExactly() {
        ReleasedV374DatabaseFixture.verifyProvenance(context)
        val encoded = ReleasedV374DatabaseFixture.legacySecretHex(context)
        val schema = TonMigration.MetaAccountSecretsV69
        val decoded = schema.read(encoded)

        assertEquals(encoded, decoded.toHexString())
        assertArrayEquals(ENTROPY, decoded[schema.Entropy])
        assertArrayEquals(SEED, decoded[schema.Seed])
        assertEquals(SUBSTRATE_DERIVATION_PATH, decoded[schema.SubstrateDerivationPath])
        assertEquals(ETHEREUM_DERIVATION_PATH, decoded[schema.EthereumDerivationPath])

        val substrateKeypair = decoded[schema.SubstrateKeypair]
        assertArrayEquals(
            SUBSTRATE_PRIVATE_KEY,
            substrateKeypair[KeyPairSchema.PrivateKey]
        )
        assertArrayEquals(
            SUBSTRATE_PUBLIC_KEY,
            substrateKeypair[KeyPairSchema.PublicKey]
        )
        assertArrayEquals(
            SUBSTRATE_NONCE,
            substrateKeypair[KeyPairSchema.Nonce]
        )

        val ethereumKeypair = requireNotNull(decoded[schema.EthereumKeypair])
        assertArrayEquals(
            ETHEREUM_PRIVATE_KEY,
            ethereumKeypair[KeyPairSchema.PrivateKey]
        )
        assertArrayEquals(
            ETHEREUM_PUBLIC_KEY,
            ethereumKeypair[KeyPairSchema.PublicKey]
        )
        assertNull(ethereumKeypair[KeyPairSchema.Nonce])
    }

    @Test
    fun version71ProductionOpenPreservesWalletSettingsAndEveryDependentRow() {
        val preferences = FaultInjectingEncryptedPreferences().apply {
            putEncryptedString(
                oldSecretKey(META_ID),
                ReleasedV374DatabaseFixture.legacySecretHex(context)
            )
        }
        createVersion71Database(PRESERVATION_DATABASE)

        openProductionDatabase(PRESERVATION_DATABASE, preferences).useDatabase { database ->
            database.openHelper.writableDatabase.assertMigratedRowsPreserved()
        }

        assertSecretsMoved(preferences, META_ID)
        assertFalse(preferences.hasKey(oldSecretKey(META_ID)))

        // A normal second launch must not replay or damage the migration.
        openProductionDatabase(PRESERVATION_DATABASE, preferences).useDatabase { database ->
            database.openHelper.writableDatabase.assertMigratedRowsPreserved()
        }
        assertSecretsMoved(preferences, META_ID)
    }

    @Test
    fun staleLegacySnapshotCannotPublishAndFreshRetryUsesConcurrentValue() {
        val originalSecret = encodedVersion69Secrets()
        val concurrentSecret = encodedVersion69Secrets(seed = null)
        assertFalse(originalSecret == concurrentSecret)
        var publishConcurrentSecret = true
        val preferences = FaultInjectingEncryptedPreferences().apply {
            putEncryptedString(oldSecretKey(META_ID), originalSecret)
            mutateBeforeEverySnapshotBoundReplacement {
                putEncryptedString(
                    oldSecretKey(META_ID),
                    if (publishConcurrentSecret) {
                        concurrentSecret
                    } else {
                        originalSecret
                    }
                )
                publishConcurrentSecret = !publishConcurrentSecret
            }
        }
        createVersion71Database(STALE_LEGACY_SNAPSHOT_DATABASE)

        assertProductionOpenFails(
            STALE_LEGACY_SNAPSHOT_DATABASE,
            preferences
        )

        assertVersion71RowsStillIntact(STALE_LEGACY_SNAPSHOT_DATABASE)
        val retainedConcurrentSecret = checkNotNull(
            preferences.getDecryptedString(oldSecretKey(META_ID))
        )
        assertTrue(
            retainedConcurrentSecret == originalSecret ||
                retainedConcurrentSecret == concurrentSecret
        )
        assertFalse(preferences.hasKey(substrateSecretKey(META_ID)))
        assertFalse(preferences.hasKey(ethereumSecretKey(META_ID)))

        preferences.clearSnapshotBoundReplacementInterception()
        openProductionDatabase(
            STALE_LEGACY_SNAPSHOT_DATABASE,
            preferences
        ).useDatabase { database ->
            database.openHelper.writableDatabase.assertMigratedRowsPreserved()
        }

        assertFalse(preferences.hasKey(oldSecretKey(META_ID)))
        assertSecretsMoved(
            preferences = preferences,
            metaId = META_ID,
            expectedSeed = if (retainedConcurrentSecret == originalSecret) {
                SEED
            } else {
                null
            }
        )
    }

    @Test
    fun concurrentlyCreatedConflictingTargetCannotPublishPartialSplit() {
        val legacySecret = encodedVersion69Secrets()
        val conflictingTarget = "concurrent-conflicting-substrate-secret"
        val preferences = FaultInjectingEncryptedPreferences().apply {
            putEncryptedString(oldSecretKey(META_ID), legacySecret)
            mutateBeforeNextSnapshotBoundReplacement {
                putEncryptedString(
                    substrateSecretKey(META_ID),
                    conflictingTarget
                )
            }
        }
        createVersion71Database(CONCURRENT_TARGET_DATABASE)

        assertProductionOpenFails(CONCURRENT_TARGET_DATABASE, preferences)

        assertVersion71RowsStillIntact(CONCURRENT_TARGET_DATABASE)
        assertEquals(
            legacySecret,
            preferences.getDecryptedString(oldSecretKey(META_ID))
        )
        assertEquals(
            conflictingTarget,
            preferences.getDecryptedString(substrateSecretKey(META_ID))
        )
        assertFalse(preferences.hasKey(ethereumSecretKey(META_ID)))
        val exactConflictedState = preferences.rawSnapshot()

        assertProductionOpenFails(CONCURRENT_TARGET_DATABASE, preferences)
        assertVersion71RowsStillIntact(CONCURRENT_TARGET_DATABASE)
        assertEquals(exactConflictedState, preferences.rawSnapshot())

        preferences.removeKey(substrateSecretKey(META_ID))
        openProductionDatabase(
            CONCURRENT_TARGET_DATABASE,
            preferences
        ).useDatabase { database ->
            database.openHelper.writableDatabase.assertMigratedRowsPreserved()
        }

        assertFalse(preferences.hasKey(oldSecretKey(META_ID)))
        assertSecretsMoved(preferences, META_ID)
    }

    @Test
    fun exactTargetAndMissingSiblingCompleteOnceAndRemainIdempotent() {
        val exactSubstrateTarget = SubstrateSecrets(
            substrateKeyPair = SUBSTRATE_KEYPAIR,
            entropy = ENTROPY,
            seed = SEED,
            substrateDerivationPath = SUBSTRATE_DERIVATION_PATH
        ).toHexString()
        val preferences = FaultInjectingEncryptedPreferences().apply {
            putEncryptedString(
                oldSecretKey(META_ID),
                encodedVersion69Secrets()
            )
            putEncryptedString(
                substrateSecretKey(META_ID),
                exactSubstrateTarget
            )
        }
        assertFalse(preferences.hasKey(ethereumSecretKey(META_ID)))
        createVersion71Database(EXACT_TARGET_MISSING_SIBLING_DATABASE)

        openProductionDatabase(
            EXACT_TARGET_MISSING_SIBLING_DATABASE,
            preferences
        ).useDatabase { database ->
            database.openHelper.writableDatabase.assertMigratedRowsPreserved()
        }

        assertEquals(
            exactSubstrateTarget,
            preferences.getDecryptedString(substrateSecretKey(META_ID))
        )
        assertFalse(preferences.hasKey(oldSecretKey(META_ID)))
        assertSecretsMoved(preferences, META_ID)
        val exactStateAfterFirstOpen = preferences.rawSnapshot()

        openProductionDatabase(
            EXACT_TARGET_MISSING_SIBLING_DATABASE,
            preferences
        ).useDatabase { database ->
            database.openHelper.writableDatabase.assertMigratedRowsPreserved()
        }

        assertEquals(exactStateAfterFirstOpen, preferences.rawSnapshot())
    }

    @Test
    fun version71NonPositiveWalletIdFailsBeforeAnyPreferenceMutation() {
        val preferences = FaultInjectingEncryptedPreferences().apply {
            putEncryptedString(oldSecretKey(META_ID), encodedVersion69Secrets())
            putEncryptedString(oldSecretKey(0L), "orphan-zero-wallet-secret")
        }
        createVersion71Database(NON_POSITIVE_META_ID_DATABASE)
        openRawReadWrite(NON_POSITIVE_META_ID_DATABASE).use { database ->
            database.execSQL(
                """
                INSERT INTO meta_accounts(
                    id,
                    substratePublicKey,
                    substrateCryptoType,
                    substrateAccountId,
                    ethereumPublicKey,
                    ethereumAddress,
                    name,
                    isSelected,
                    position,
                    isBackedUp,
                    googleBackupAddress,
                    initialized
                )
                SELECT
                    0,
                    substratePublicKey,
                    substrateCryptoType,
                    substrateAccountId,
                    ethereumPublicKey,
                    ethereumAddress,
                    ?,
                    0,
                    99,
                    isBackedUp,
                    googleBackupAddress,
                    initialized
                FROM meta_accounts
                WHERE id = ?
                """.trimIndent(),
                arrayOf<Any>("Invalid zero wallet", META_ID)
            )
        }

        assertVersion71PreflightFailurePreservesExactState(
            databaseName = NON_POSITIVE_META_ID_DATABASE,
            preferences = preferences
        ) {
            openRawReadWrite(NON_POSITIVE_META_ID_DATABASE).use {
                it.execSQL("DELETE FROM meta_accounts WHERE id = 0")
            }
            preferences.removeKey(oldSecretKey(0L))
        }
    }

    @Test
    fun version71TextChainAccountIdFailsBeforeAnyPreferenceMutation() {
        val preferences = FaultInjectingEncryptedPreferences().apply {
            putEncryptedString(oldSecretKey(META_ID), encodedVersion69Secrets())
        }
        createVersion71Database(TEXT_CHAIN_ACCOUNT_ID_DATABASE)
        openRawReadWrite(TEXT_CHAIN_ACCOUNT_ID_DATABASE).use {
            it.execSQL(
                "UPDATE chain_accounts SET accountId = ? WHERE metaId = ?",
                arrayOf("not-a-blob", META_ID)
            )
        }

        assertVersion71PreflightFailurePreservesExactState(
            databaseName = TEXT_CHAIN_ACCOUNT_ID_DATABASE,
            preferences = preferences
        ) {
            restoreVersion71ChainAccountId(TEXT_CHAIN_ACCOUNT_ID_DATABASE)
        }
    }

    @Test
    fun version71MissingAddressBookIndexPreservesPreferencesAndVersion() {
        val preferences = FaultInjectingEncryptedPreferences().apply {
            putEncryptedString(oldSecretKey(META_ID), encodedVersion69Secrets())
        }
        createVersion71Database(MISSING_ADDRESS_BOOK_INDEX_DATABASE)
        openRawReadWrite(MISSING_ADDRESS_BOOK_INDEX_DATABASE).use {
            it.execSQL("DROP INDEX index_address_book_address_chainId")
        }

        assertVersion71PreflightFailurePreservesExactState(
            databaseName = MISSING_ADDRESS_BOOK_INDEX_DATABASE,
            preferences = preferences
        ) {
            openRawReadWrite(MISSING_ADDRESS_BOOK_INDEX_DATABASE).use {
                it.execSQL(
                    "CREATE UNIQUE INDEX " +
                        "index_address_book_address_chainId " +
                        "ON address_book(address, chainId)"
                )
            }
        }
    }

    @Test
    fun version71OversizedChainAccountIdFailsBeforeAnyPreferenceMutation() {
        val preferences = FaultInjectingEncryptedPreferences().apply {
            putEncryptedString(oldSecretKey(META_ID), encodedVersion69Secrets())
        }
        createVersion71Database(OVERSIZED_CHAIN_ACCOUNT_ID_DATABASE)
        openRawReadWrite(OVERSIZED_CHAIN_ACCOUNT_ID_DATABASE).use {
            it.execSQL(
                "UPDATE chain_accounts SET accountId = zeroblob(65) " +
                    "WHERE metaId = ?",
                arrayOf(META_ID)
            )
        }

        assertVersion71PreflightFailurePreservesExactState(
            databaseName = OVERSIZED_CHAIN_ACCOUNT_ID_DATABASE,
            preferences = preferences
        ) {
            restoreVersion71ChainAccountId(
                OVERSIZED_CHAIN_ACCOUNT_ID_DATABASE
            )
        }
    }

    @Test
    fun version71InvalidTonRecoveryMarkerFailsBeforeAnyPreferenceMutation() {
        val activeKey = "$META_ID:TON_SECRETS"
        val markerKey = publicIdentityMarker(activeKey)
        val preferences = FaultInjectingEncryptedPreferences().apply {
            putEncryptedString(oldSecretKey(META_ID), encodedVersion69Secrets())
            putEncryptedString(markerKey, "invalid-recovery-protocol-value")
        }
        createVersion71Database(INVALID_ROOT_MARKER_DATABASE)

        assertVersion71PreflightFailurePreservesExactState(
            databaseName = INVALID_ROOT_MARKER_DATABASE,
            preferences = preferences
        ) {
            preferences.removeKey(markerKey)
        }
    }

    @Test
    fun version71InvalidChainRecoveryMarkerFailsBeforeAnyPreferenceMutation() {
        val activeKey =
            "$META_ID:${CHAIN_ACCOUNT_ID.toPlainHexString()}:ACCESS_SECRETS"
        val markerKey = publicIdentityMarker(activeKey)
        val preferences = FaultInjectingEncryptedPreferences().apply {
            putEncryptedString(oldSecretKey(META_ID), encodedVersion69Secrets())
            putEncryptedString(markerKey, "invalid-recovery-protocol-value")
        }
        createVersion71Database(INVALID_CHAIN_MARKER_DATABASE)

        assertVersion71PreflightFailurePreservesExactState(
            databaseName = INVALID_CHAIN_MARKER_DATABASE,
            preferences = preferences
        ) {
            preferences.removeKey(markerKey)
        }
    }

    @Test
    fun malformedSecretIsQuarantinedWhileEveryOtherWalletStillOpens() {
        val preferences = FaultInjectingEncryptedPreferences().apply {
            putEncryptedString(oldSecretKey(META_ID), encodedVersion69Secrets())
            putEncryptedString(oldSecretKey(MALFORMED_META_ID), MALFORMED_SECRET)
        }
        createVersion71Database(MALFORMED_SECRET_DATABASE, includeMalformedWallet = true)

        openProductionDatabase(MALFORMED_SECRET_DATABASE, preferences).useDatabase { database ->
            val db = database.openHelper.writableDatabase
            db.assertMigratedRowsPreserved()
            assertEquals(
                1,
                db.singleInt("SELECT COUNT(*) FROM meta_accounts WHERE id = $MALFORMED_META_ID")
            )
        }

        assertSecretsMoved(preferences, META_ID)
        assertFalse(preferences.hasKey(oldSecretKey(META_ID)))
        assertFalse(preferences.hasKey(oldSecretKey(MALFORMED_META_ID)))
        assertFalse(preferences.hasKey(substrateSecretKey(MALFORMED_META_ID)))
        assertFalse(preferences.hasKey(ethereumSecretKey(MALFORMED_META_ID)))
        assertEquals(
            MALFORMED_SECRET,
            preferences.getDecryptedString(
                quarantineKey(oldSecretKey(MALFORMED_META_ID))
            )
        )
    }

    @Test
    fun locallyUndecryptableSecretIsQuarantinedAndDatabaseStillOpens() {
        val preferences = FaultInjectingEncryptedPreferences().apply {
            // Production EncryptionUtil reports a malformed or unauthenticated
            // payload as empty while its exact ciphertext remains available
            // for quarantine.
            putEncryptedString(oldSecretKey(META_ID), "")
        }
        createVersion71Database(DECRYPTION_FAILURE_DATABASE)

        openProductionDatabase(DECRYPTION_FAILURE_DATABASE, preferences).useDatabase { database ->
            database.openHelper.writableDatabase.assertMigratedRowsPreserved()
        }

        assertFalse(preferences.hasKey(oldSecretKey(META_ID)))
        assertFalse(preferences.hasKey(substrateSecretKey(META_ID)))
        assertFalse(preferences.hasKey(ethereumSecretKey(META_ID)))
        assertEquals(
            "",
            preferences.getDecryptedString(quarantineKey(oldSecretKey(META_ID)))
        )
    }

    @Test
    fun version71PayloadProviderFailurePreservesCiphertextAndRetries() {
        clearRealEncryptionStorage()
        try {
            createVersion71Database(PAYLOAD_PROVIDER_FAILURE_DATABASE)
            val plaintext = encodedVersion69Secrets()
            val normalUtil = EncryptionUtil(context)
            val exactCiphertext = encryptLegacyCiphertext(
                key = normalUtil.getPrerenceAesKey().encoded,
                plaintext = plaintext
            )
            walletPreferences().edit()
                .putString(oldSecretKey(META_ID), exactCiphertext)
                .commit()
            val failingPreferences = realEncryptedPreferences(
                EncryptionUtilTestFactory.withPayloadDecryptFailure(
                    context = context,
                    transformation = AES_CBC_TRANSFORMATION,
                    failure = NoSuchAlgorithmException(
                        "transient legacy payload provider failure"
                    )
                )
            )

            var failedDatabase: AppDatabase? = null
            try {
                assertThrows(WalletSecureStorageUnavailableException::class.java) {
                    failedDatabase = AppDatabase.create(
                        context = context,
                        databaseName = PAYLOAD_PROVIDER_FAILURE_DATABASE,
                        storeV1 = SecretStoreV1Impl(failingPreferences),
                        storeV2 = SecretStoreV2(failingPreferences),
                        encryptedPreferences = failingPreferences,
                        substrateSecretStore =
                            SubstrateSecretStore(failingPreferences),
                        ethereumSecretStore =
                            EthereumSecretStore(failingPreferences)
                    )
                    failedDatabase?.openHelper?.writableDatabase
                }
            } finally {
                failedDatabase?.close()
            }

            assertVersion71RowsStillIntact(PAYLOAD_PROVIDER_FAILURE_DATABASE)
            assertEquals(
                exactCiphertext,
                walletPreferences().getString(oldSecretKey(META_ID), null)
            )
            assertFalse(
                walletPreferences().contains(
                    quarantineKey(oldSecretKey(META_ID))
                )
            )

            val retryPreferences = realEncryptedPreferences(
                EncryptionUtil(context)
            )
            openProductionDatabase(
                PAYLOAD_PROVIDER_FAILURE_DATABASE,
                retryPreferences
            ).useDatabase { database ->
                database.openHelper.writableDatabase
                    .assertMigratedRowsPreserved()
            }
            assertSecretsMoved(retryPreferences, META_ID)
            assertFalse(
                walletPreferences().contains(oldSecretKey(META_ID))
            )
        } finally {
            clearRealEncryptionStorage()
        }
    }

    @Test
    fun publicKeyMismatchIsQuarantinedInsteadOfBindingWrongSigningMaterial() {
        val mismatchedSecret = encodedVersion69Secrets(
            substrateKeypair = jp.co.soramitsu.common.data.Keypair(
                publicKey = MALFORMED_PUBLIC_KEY,
                privateKey = SUBSTRATE_PRIVATE_KEY,
                nonce = SUBSTRATE_NONCE
            )
        )
        val preferences = FaultInjectingEncryptedPreferences().apply {
            putEncryptedString(oldSecretKey(META_ID), mismatchedSecret)
        }
        createVersion71Database(PUBLIC_KEY_MISMATCH_DATABASE)

        openProductionDatabase(PUBLIC_KEY_MISMATCH_DATABASE, preferences).useDatabase { database ->
            database.openHelper.writableDatabase.assertMigratedRowsPreserved()
        }

        assertFalse(preferences.hasKey(oldSecretKey(META_ID)))
        assertFalse(preferences.hasKey(substrateSecretKey(META_ID)))
        assertFalse(preferences.hasKey(ethereumSecretKey(META_ID)))
        assertEquals(
            mismatchedSecret,
            preferences.getDecryptedString(quarantineKey(oldSecretKey(META_ID)))
        )
    }

    @Test
    fun sr25519PrivateKeyMismatchWithUnchangedPublicKeyIsQuarantined() {
        assertSubstratePrivateProofFailure(
            databaseName = SR25519_PRIVATE_MISMATCH_DATABASE,
            cryptoType = "SR25519",
            validKeypair = SUBSTRATE_KEYPAIR,
            mismatchedKeypair = jp.co.soramitsu.common.data.Keypair(
                publicKey = SUBSTRATE_PUBLIC_KEY,
                privateKey = SUBSTRATE_PRIVATE_KEY.clone().apply {
                    this[lastIndex] = (this[lastIndex] + 1).toByte()
                },
                nonce = SUBSTRATE_NONCE
            )
        )
    }

    @Test
    fun ed25519PrivateKeyMismatchWithUnchangedPublicKeyIsQuarantined() {
        assertSubstratePrivateProofFailure(
            databaseName = ED25519_PRIVATE_MISMATCH_DATABASE,
            cryptoType = "ED25519",
            validKeypair = ED25519_KEYPAIR,
            mismatchedKeypair = jp.co.soramitsu.common.data.Keypair(
                publicKey = ED25519_KEYPAIR.publicKey,
                privateKey = ED25519_KEYPAIR.privateKey.clone().apply {
                    this[lastIndex] = (this[lastIndex] + 1).toByte()
                }
            )
        )
    }

    @Test
    fun ecdsaPrivateKeyMismatchWithUnchangedPublicKeyIsQuarantined() {
        assertSubstratePrivateProofFailure(
            databaseName = ECDSA_PRIVATE_MISMATCH_DATABASE,
            cryptoType = "ECDSA",
            validKeypair = ECDSA_KEYPAIR,
            mismatchedKeypair = jp.co.soramitsu.common.data.Keypair(
                publicKey = ECDSA_KEYPAIR.publicKey,
                privateKey = ECDSA_KEYPAIR.privateKey.clone().apply {
                    this[lastIndex] = (this[lastIndex] + 1).toByte()
                }
            )
        )
    }

    @Test
    fun ethereumPrivateKeyMismatchWithUnchangedPublicKeyIsQuarantined() {
        val mismatchedEthereum = jp.co.soramitsu.common.data.Keypair(
            publicKey = ETHEREUM_PUBLIC_KEY,
            privateKey = ETHEREUM_PRIVATE_KEY.clone().apply {
                this[lastIndex] = (this[lastIndex] + 1).toByte()
            }
        )
        val encoded = encodedVersion69Secrets(
            ethereumKeypair = mismatchedEthereum
        )
        val preferences = FaultInjectingEncryptedPreferences().apply {
            putEncryptedString(oldSecretKey(META_ID), encoded)
        }
        createVersion71Database(ETHEREUM_PRIVATE_MISMATCH_DATABASE)

        openProductionDatabase(ETHEREUM_PRIVATE_MISMATCH_DATABASE, preferences)
            .useDatabase { database ->
                assertEquals(77, database.openHelper.writableDatabase.version)
            }

        assertProofFailureQuarantined(preferences, encoded)
    }

    @Test
    fun validSr25519Ed25519AndEcdsaRecoveryMaterialMigrates() {
        listOf(
            Triple(VALID_SR25519_DATABASE, "SR25519", SUBSTRATE_KEYPAIR),
            Triple(VALID_ED25519_DATABASE, "ED25519", ED25519_KEYPAIR),
            Triple(VALID_ECDSA_DATABASE, "ECDSA", ECDSA_KEYPAIR)
        ).forEach { (databaseName, cryptoType, keypair) ->
            val preferences = FaultInjectingEncryptedPreferences().apply {
                putEncryptedString(
                    oldSecretKey(META_ID),
                    encodedVersion69Secrets(substrateKeypair = keypair)
                )
            }
            createVersion71Database(
                databaseName = databaseName,
                primarySubstratePublicKey = keypair.publicKey,
                primarySubstrateCryptoType = cryptoType,
                primarySubstrateAccountId = keypair.publicKey.substrateAccountId()
            )

            openProductionDatabase(databaseName, preferences).useDatabase { database ->
                val db = database.openHelper.writableDatabase
                assertEquals(77, db.version)
                assertArrayEquals(
                    keypair.publicKey,
                    db.singleBlob("SELECT substratePublicKey FROM meta_accounts WHERE id = ?", META_ID)
                )
            }

            assertSubstrateSecretsMoved(
                preferences = preferences,
                metaId = META_ID,
                expectedEntropy = ENTROPY,
                expectedSeed = SEED,
                expectedKeypair = keypair
            )
            assertFalse(preferences.hasKey(oldSecretKey(META_ID)))
        }
    }

    @Test
    fun wrongSubstrateEntropyIsQuarantined() {
        val wrongEntropy = ENTROPY.clone().apply {
            this[lastIndex] = (this[lastIndex] + 1).toByte()
        }
        val encoded = encodedVersion69Secrets(
            entropy = wrongEntropy,
            seed = null,
            includeEthereum = false,
            ethereumDerivationPath = null
        )

        assertLegacySecretQuarantined(
            databaseName = WRONG_SUBSTRATE_ENTROPY_DATABASE,
            encodedLegacySecret = encoded,
            ethereumPublicKey = null,
            ethereumAddress = null
        )
    }

    @Test
    fun wrongSubstrateSeedIsQuarantined() {
        val wrongSeed = SEED.clone().apply {
            this[lastIndex] = (this[lastIndex] + 1).toByte()
        }
        val encoded = encodedVersion69Secrets(
            entropy = null,
            seed = wrongSeed,
            includeEthereum = false,
            ethereumDerivationPath = null
        )

        assertLegacySecretQuarantined(
            databaseName = WRONG_SUBSTRATE_SEED_DATABASE,
            encodedLegacySecret = encoded,
            ethereumPublicKey = null,
            ethereumAddress = null
        )
    }

    @Test
    fun wrongSubstratePasswordIsQuarantined() {
        val encoded = encodedVersion69Secrets(
            seed = null,
            includeEthereum = false,
            substrateDerivationPath = "//hard///wrong-password",
            ethereumDerivationPath = null
        )

        assertLegacySecretQuarantined(
            databaseName = WRONG_SUBSTRATE_PASSWORD_DATABASE,
            encodedLegacySecret = encoded,
            ethereumPublicKey = null,
            ethereumAddress = null
        )
    }

    @Test
    fun wrongSubstrateJunctionIsQuarantined() {
        val encoded = encodedVersion69Secrets(
            includeEthereum = false,
            substrateDerivationPath = "//different-hard///password",
            ethereumDerivationPath = null
        )

        assertLegacySecretQuarantined(
            databaseName = WRONG_SUBSTRATE_JUNCTION_DATABASE,
            encodedLegacySecret = encoded,
            ethereumPublicKey = null,
            ethereumAddress = null
        )
    }

    @Test
    fun wrongEthereumDerivationPathIsQuarantined() {
        val encoded = encodedVersion69Secrets(
            ethereumDerivationPath = "//44//60//0/0/1"
        )

        assertLegacySecretQuarantined(
            databaseName = WRONG_ETHEREUM_PATH_DATABASE,
            encodedLegacySecret = encoded
        )
    }

    @Test
    fun ethereumEntropyThatOnlyMatchesSubstrateIsQuarantined() {
        val differentEntropy = DIFFERENT_ENTROPY
        val differentMnemonic = MnemonicCreator.fromEntropy(differentEntropy)
        val differentSeed = SubstrateSeedFactory.deriveSeed32(
            mnemonicWords = differentMnemonic.words,
            password = DECODED_SUBSTRATE_DERIVATION_PATH.password
        ).seed
        val differentSubstrateKeypair = SubstrateKeypairFactory.generate(
            encryptionType = EncryptionType.SR25519,
            seed = differentSeed,
            junctions = DECODED_SUBSTRATE_DERIVATION_PATH.junctions
        )
        val encoded = encodedVersion69Secrets(
            entropy = differentEntropy,
            seed = differentSeed,
            substrateKeypair = differentSubstrateKeypair,
            ethereumKeypair = ETHEREUM_KEYPAIR
        )

        assertLegacySecretQuarantined(
            databaseName = WRONG_ETHEREUM_ENTROPY_DATABASE,
            encodedLegacySecret = encoded,
            substratePublicKey = differentSubstrateKeypair.publicKey,
            substrateAccountId = differentSubstrateKeypair.publicKey.substrateAccountId()
        )
    }

    @Test
    fun missingEthereumKeypairForPublicIdentityIsQuarantined() {
        val encoded = encodedVersion69Secrets(
            includeEthereum = false,
            ethereumDerivationPath = null
        )

        assertLegacySecretQuarantined(
            databaseName = MISSING_ETHEREUM_KEYPAIR_DATABASE,
            encodedLegacySecret = encoded
        )
    }

    @Test
    fun ethereumKeypairWithoutPublicIdentityIsQuarantined() {
        val encoded = encodedVersion69Secrets()

        assertLegacySecretQuarantined(
            databaseName = UNOWNED_ETHEREUM_KEYPAIR_DATABASE,
            encodedLegacySecret = encoded,
            ethereumPublicKey = null,
            ethereumAddress = null
        )
    }

    @Test
    fun partialPublicEthereumIdentityCommitsRecoveryMarkerAndPreservesLegacyCiphertext() {
        val encoded = encodedVersion69Secrets()
        val preferences = FaultInjectingEncryptedPreferences().apply {
            putEncryptedString(oldSecretKey(META_ID), encoded)
        }
        createVersion71Database(
            databaseName = PARTIAL_ETHEREUM_IDENTITY_DATABASE,
            primaryEthereumAddress = null
        )

        openProductionDatabase(
            PARTIAL_ETHEREUM_IDENTITY_DATABASE,
            preferences
        ).useDatabase {
            assertEquals(77, it.openHelper.writableDatabase.version)
        }
        assertEquals(encoded, preferences.getDecryptedString(oldSecretKey(META_ID)))
        assertFalse(preferences.hasKey(quarantineKey(oldSecretKey(META_ID))))
        assertEquals(
            WalletPublicIdentityRecovery.MARKER_VALUE,
            preferences.getDecryptedString(
                publicIdentityMarker(oldSecretKey(META_ID))
            )
        )
        openProductionDatabase(
            PARTIAL_ETHEREUM_IDENTITY_DATABASE,
            preferences
        ).close()
        assertEquals(encoded, preferences.getDecryptedString(oldSecretKey(META_ID)))
    }

    @Test
    fun unknownSubstrateCryptoTypeCommitsRecoveryMarkerAndPreservesLegacyCiphertext() {
        val encoded = encodedVersion69Secrets()
        val preferences = FaultInjectingEncryptedPreferences().apply {
            putEncryptedString(oldSecretKey(META_ID), encoded)
        }
        createVersion71Database(
            databaseName = UNKNOWN_SUBSTRATE_CRYPTO_DATABASE,
            primarySubstrateCryptoType = "FUTURE_CRYPTO"
        )

        openProductionDatabase(
            UNKNOWN_SUBSTRATE_CRYPTO_DATABASE,
            preferences
        ).useDatabase {
            assertEquals(77, it.openHelper.writableDatabase.version)
        }
        assertEquals(encoded, preferences.getDecryptedString(oldSecretKey(META_ID)))
        assertFalse(preferences.hasKey(quarantineKey(oldSecretKey(META_ID))))
        assertEquals(
            WalletPublicIdentityRecovery.MARKER_VALUE,
            preferences.getDecryptedString(
                publicIdentityMarker(oldSecretKey(META_ID))
            )
        )
        openProductionDatabase(
            UNKNOWN_SUBSTRATE_CRYPTO_DATABASE,
            preferences
        ).close()
        assertEquals(encoded, preferences.getDecryptedString(oldSecretKey(META_ID)))
    }

    @Test
    fun substrateAccountIdMismatchMarksPublicIdentityRecovery() {
        val encoded = encodedVersion69Secrets()

        assertLegacyPublicIdentityRecovery(
            databaseName = SUBSTRATE_ACCOUNT_ID_MISMATCH_DATABASE,
            encodedLegacySecret = encoded,
            substrateAccountId = ByteArray(32) { (it + 101).toByte() }
        )
    }

    @Test
    fun ethereumAddressMismatchMarksPublicIdentityRecovery() {
        val encoded = encodedVersion69Secrets()

        assertLegacyPublicIdentityRecovery(
            databaseName = ETHEREUM_ADDRESS_MISMATCH_DATABASE,
            encodedLegacySecret = encoded,
            ethereumAddress = ByteArray(20) { (it + 51).toByte() }
        )
    }

    @Test
    fun offCurveEthereumPublicKeyMarksRecoveryAndSurvivesTwoLaunches() {
        val encoded = encodedVersion69Secrets()
        val offCurvePublicKey =
            byteArrayOf(0x02) + ByteArray(32) { 0xFF.toByte() }
        val preferences = FaultInjectingEncryptedPreferences().apply {
            putEncryptedString(oldSecretKey(META_ID), encoded)
        }
        createVersion71Database(
            databaseName = OFF_CURVE_ETHEREUM_DATABASE,
            primaryEthereumPublicKey = offCurvePublicKey,
            primaryEthereumAddress = ETHEREUM_ADDRESS
        )
        val markerKey = publicIdentityMarker(oldSecretKey(META_ID))

        repeat(2) {
            openProductionDatabase(
                OFF_CURVE_ETHEREUM_DATABASE,
                preferences
            ).useDatabase { database ->
                assertEquals(77, database.openHelper.writableDatabase.version)
            }
            assertEquals(
                encoded,
                preferences.getDecryptedString(oldSecretKey(META_ID))
            )
            assertFalse(
                preferences.hasKey(quarantineKey(oldSecretKey(META_ID)))
            )
            assertEquals(
                WalletPublicIdentityRecovery.MARKER_VALUE,
                preferences.getDecryptedString(markerKey)
            )
        }
    }

    @Test
    fun ethereumPathWithoutAKeypairIsQuarantined() {
        val encoded = encodedVersion69Secrets(
            includeEthereum = false,
            ethereumDerivationPath = ETHEREUM_DERIVATION_PATH
        )

        assertLegacySecretQuarantined(
            databaseName = ORPHAN_ETHEREUM_PATH_DATABASE,
            encodedLegacySecret = encoded,
            ethereumPublicKey = null,
            ethereumAddress = null
        )
    }

    @Test
    fun oversizedDerivationPathsAreQuarantined() {
        val oversizedPath = "/" + "7".repeat(2_049)
        val substrateEncoded = encodedVersion69Secrets(
            includeEthereum = false,
            substrateDerivationPath = oversizedPath,
            ethereumDerivationPath = null
        )
        assertLegacySecretQuarantined(
            databaseName = OVERSIZED_SUBSTRATE_PATH_DATABASE,
            encodedLegacySecret = substrateEncoded,
            ethereumPublicKey = null,
            ethereumAddress = null
        )

        val ethereumEncoded = encodedVersion69Secrets(
            ethereumDerivationPath = oversizedPath
        )
        assertLegacySecretQuarantined(
            databaseName = OVERSIZED_ETHEREUM_PATH_DATABASE,
            encodedLegacySecret = ethereumEncoded
        )
    }

    @Test
    fun quarantineCommitFailureRollsBackSqlAndLeavesActiveCiphertextUntouched() {
        val preferences = FaultInjectingEncryptedPreferences().apply {
            putEncryptedString(oldSecretKey(META_ID), MALFORMED_SECRET)
            failAlways(
                operation = FailureOperation.BEFORE_PUT,
                key = quarantineKey(oldSecretKey(META_ID))
            )
        }
        createVersion71Database(QUARANTINE_FAILURE_DATABASE)

        assertProductionOpenFails(QUARANTINE_FAILURE_DATABASE, preferences)

        assertVersion71RowsStillIntact(QUARANTINE_FAILURE_DATABASE)
        assertEquals(
            MALFORMED_SECRET,
            preferences.getDecryptedString(oldSecretKey(META_ID))
        )
        assertFalse(preferences.hasKey(quarantineKey(oldSecretKey(META_ID))))

        preferences.clearFailure()
        openProductionDatabase(QUARANTINE_FAILURE_DATABASE, preferences).useDatabase { database ->
            database.openHelper.writableDatabase.assertMigratedRowsPreserved()
        }

        assertFalse(preferences.hasKey(oldSecretKey(META_ID)))
        assertEquals(
            MALFORMED_SECRET,
            preferences.getDecryptedString(quarantineKey(oldSecretKey(META_ID)))
        )
    }

    @Test
    fun committedQuarantineSurvivesSqlRollbackAndAutomaticRetry() {
        val preferences = FaultInjectingEncryptedPreferences().apply {
            putEncryptedString(oldSecretKey(META_ID), MALFORMED_SECRET)
            failOnce(
                operation = FailureOperation.AFTER_REMOVE,
                key = oldSecretKey(META_ID)
            )
        }
        createVersion71Database(QUARANTINE_RETRY_DATABASE)

        openProductionDatabase(QUARANTINE_RETRY_DATABASE, preferences).useDatabase { database ->
            database.openHelper.writableDatabase.assertMigratedRowsPreserved()
        }

        assertEquals(1, preferences.injectedFailureCount())
        assertFalse(preferences.hasKey(oldSecretKey(META_ID)))
        assertEquals(
            MALFORMED_SECRET,
            preferences.getDecryptedString(quarantineKey(oldSecretKey(META_ID)))
        )
    }

    @Test
    fun globalSecureStorageFailureNeverQuarantinesOrAdvancesSchema() {
        val preferences = FaultInjectingEncryptedPreferences().apply {
            putEncryptedString(oldSecretKey(META_ID), encodedVersion69Secrets())
            secureStorageUnavailableKey = oldSecretKey(META_ID)
        }
        createVersion71Database(GLOBAL_STORAGE_FAILURE_DATABASE)

        assertProductionOpenFails(GLOBAL_STORAGE_FAILURE_DATABASE, preferences)

        assertVersion71RowsStillIntact(GLOBAL_STORAGE_FAILURE_DATABASE)
        assertTrue(preferences.hasKey(oldSecretKey(META_ID)))
        assertFalse(preferences.hasKey(quarantineKey(oldSecretKey(META_ID))))
        assertFalse(preferences.hasKey(substrateSecretKey(META_ID)))
        assertFalse(preferences.hasKey(ethereumSecretKey(META_ID)))
    }

    @Test
    fun seedOnlyLegacyWalletMigratesWithoutEntropyAndRetainsSigningMaterial() {
        val preferences = FaultInjectingEncryptedPreferences().apply {
            putEncryptedString(
                oldSecretKey(META_ID),
                encodedVersion69Secrets(entropy = null, seed = SEED)
            )
        }
        createVersion71Database(SEED_ONLY_DATABASE)

        openProductionDatabase(SEED_ONLY_DATABASE, preferences).useDatabase { database ->
            database.openHelper.writableDatabase.assertMigratedRowsPreserved()
        }

        assertSecretsMoved(
            preferences = preferences,
            metaId = META_ID,
            expectedEntropy = null,
            expectedSeed = SEED,
            expectedEthereumDerivationPath = null
        )
        assertFalse(preferences.hasKey(oldSecretKey(META_ID)))
    }

    @Test
    fun keypairOnlyLegacyWalletMigratesWithoutEntropySeedOrEthereumSecrets() {
        val preferences = FaultInjectingEncryptedPreferences().apply {
            putEncryptedString(
                oldSecretKey(META_ID),
                encodedVersion69Secrets(
                    entropy = null,
                    seed = null,
                    includeEthereum = false,
                    substrateDerivationPath = null,
                    ethereumDerivationPath = null
                )
            )
        }
        createVersion71Database(
            databaseName = KEYPAIR_ONLY_DATABASE,
            primaryEthereumPublicKey = null,
            primaryEthereumAddress = null
        )

        openProductionDatabase(KEYPAIR_ONLY_DATABASE, preferences).useDatabase { database ->
            assertEquals(77, database.openHelper.writableDatabase.version)
        }

        assertSubstrateSecretsMoved(
            preferences = preferences,
            metaId = META_ID,
            expectedEntropy = null,
            expectedSeed = null,
            expectedDerivationPath = null
        )
        assertNull(EthereumSecretStore(preferences).get(META_ID))
        assertFalse(preferences.hasKey(oldSecretKey(META_ID)))
    }

    @Test
    fun persistentEthereumWriteFailureRollsBackDatabaseRetainsOldSecretAndSafelyRetries() {
        val preferences = FaultInjectingEncryptedPreferences().apply {
            putEncryptedString(oldSecretKey(META_ID), encodedVersion69Secrets())
            failAlways(
                operation = FailureOperation.BEFORE_PUT,
                key = ethereumSecretKey(META_ID)
            )
        }
        createVersion71Database(ETHEREUM_FAILURE_DATABASE)

        assertProductionOpenFails(ETHEREUM_FAILURE_DATABASE, preferences)

        assertVersion71RowsStillIntact(ETHEREUM_FAILURE_DATABASE)
        assertTrue(preferences.hasKey(oldSecretKey(META_ID)))
        assertTrue(preferences.hasKey(substrateSecretKey(META_ID)))
        assertFalse(preferences.hasKey(ethereumSecretKey(META_ID)))
        assertTrue(preferences.injectedFailureCount() > 0)

        preferences.clearFailure()
        openProductionDatabase(ETHEREUM_FAILURE_DATABASE, preferences).useDatabase { database ->
            database.openHelper.writableDatabase.assertMigratedRowsPreserved()
        }

        assertSecretsMoved(preferences, META_ID)
        assertFalse(preferences.hasKey(oldSecretKey(META_ID)))
    }

    @Test
    fun laterWalletFailureKeepsEveryWalletRecoverableAndRetryCompletesMigration() {
        val preferences = FaultInjectingEncryptedPreferences().apply {
            putEncryptedString(oldSecretKey(META_ID), encodedVersion69Secrets())
            putEncryptedString(
                oldSecretKey(SECOND_VALID_META_ID),
                encodedVersion69Secrets(
                    entropy = null,
                    seed = null,
                    substrateKeypair = SECOND_VALID_SUBSTRATE_KEYPAIR,
                    substrateDerivationPath = null
                )
            )
            failAlways(
                operation = FailureOperation.BEFORE_PUT,
                key = ethereumSecretKey(SECOND_VALID_META_ID)
            )
        }
        createVersion71Database(
            databaseName = MULTI_WALLET_FAILURE_DATABASE,
            includeSecondValidWallet = true
        )

        assertProductionOpenFails(MULTI_WALLET_FAILURE_DATABASE, preferences)

        assertVersion71RowsStillIntact(
            databaseName = MULTI_WALLET_FAILURE_DATABASE,
            additionalMetaId = SECOND_VALID_META_ID
        )
        assertSecretsMoved(preferences, META_ID)
        assertFalse(preferences.hasKey(oldSecretKey(META_ID)))
        assertTrue(preferences.hasKey(oldSecretKey(SECOND_VALID_META_ID)))
        assertTrue(preferences.injectedFailureCount() > 0)

        preferences.clearFailure()
        openProductionDatabase(MULTI_WALLET_FAILURE_DATABASE, preferences).useDatabase { database ->
            database.openHelper.writableDatabase.assertMigratedRowsPreserved()
        }

        assertSecretsMoved(preferences, META_ID)
        assertSecretsMoved(
            preferences,
            SECOND_VALID_META_ID,
            expectedEntropy = null,
            expectedSeed = null,
            expectedSubstrateDerivationPath = null,
            expectedEthereumDerivationPath = null,
            expectedSubstrateKeypair = SECOND_VALID_SUBSTRATE_KEYPAIR
        )
        assertFalse(preferences.hasKey(oldSecretKey(META_ID)))
        assertFalse(preferences.hasKey(oldSecretKey(SECOND_VALID_META_ID)))
    }

    @Test
    fun transientFailureAfterOldSecretRemovalRecoversDuringProductionOpenRetry() {
        val preferences = FaultInjectingEncryptedPreferences().apply {
            putEncryptedString(oldSecretKey(META_ID), encodedVersion69Secrets())
            failOnce(
                operation = FailureOperation.AFTER_REMOVE,
                key = oldSecretKey(META_ID)
            )
        }
        createVersion71Database(CLEAR_FAILURE_DATABASE)

        // Android's SQLite opener retries after the injected transient failure.
        // The retry sees no legacy value, but both new stores are already
        // complete, so the SQL transaction can safely finish independently.
        openProductionDatabase(CLEAR_FAILURE_DATABASE, preferences).useDatabase { database ->
            database.openHelper.writableDatabase.assertMigratedRowsPreserved()
        }

        assertEquals(1, preferences.injectedFailureCount())
        assertFalse(preferences.hasKey(oldSecretKey(META_ID)))
        assertSecretsMoved(preferences, META_ID)
    }

    private fun createVersion71Database(
        databaseName: String,
        includeMalformedWallet: Boolean = false,
        includeSecondValidWallet: Boolean = false,
        primarySubstratePublicKey: ByteArray = SUBSTRATE_PUBLIC_KEY,
        primarySubstrateCryptoType: String = "SR25519",
        primarySubstrateAccountId: ByteArray = SUBSTRATE_ACCOUNT_ID,
        primaryEthereumPublicKey: ByteArray? = ETHEREUM_PUBLIC_KEY,
        primaryEthereumAddress: ByteArray? = ETHEREUM_ADDRESS
    ) {
        ReleasedV374DatabaseFixture.install(context, databaseName)
        val callback = object :
            SupportSQLiteOpenHelper.Callback(ReleasedV374DatabaseFixture.DATABASE_VERSION) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                error("The frozen 3.7.4 fixture was not installed")
            }

            override fun onUpgrade(
                db: SupportSQLiteDatabase,
                oldVersion: Int,
                newVersion: Int
            ) {
                error(
                    "Unexpected fixture setup migration from $oldVersion to $newVersion"
                )
            }
        }
        val openHelper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(callback)
                .build()
        )

        try {
            openHelper.writableDatabase.apply {
                setForeignKeyConstraintsEnabled(true)
                assertFrozenVersion71Identity()
                insertVersion71Rows(
                    includeMalformedWallet = includeMalformedWallet,
                    includeSecondValidWallet = includeSecondValidWallet,
                    primarySubstratePublicKey = primarySubstratePublicKey,
                    primarySubstrateCryptoType = primarySubstrateCryptoType,
                    primarySubstrateAccountId = primarySubstrateAccountId,
                    primaryEthereumPublicKey = primaryEthereumPublicKey,
                    primaryEthereumAddress = primaryEthereumAddress
                )
                assertEquals(0, singleInt("SELECT COUNT(*) FROM pragma_foreign_key_check"))
            }
        } finally {
            openHelper.close()
        }
    }

    private fun SupportSQLiteDatabase.assertFrozenVersion71Identity() {
        assertEquals(ReleasedV374DatabaseFixture.DATABASE_VERSION, version)
        assertEquals(
            ReleasedV374DatabaseFixture.ROOM_IDENTITY_HASH,
            singleString(
                "SELECT identity_hash FROM room_master_table WHERE id = 42"
            )
        )
        assertEquals(
            ReleasedV374DatabaseFixture.EXPECTED_ROOM_TABLE_COUNT,
            singleInt(
                """
                SELECT COUNT(*) FROM sqlite_master
                WHERE type = 'table'
                  AND name NOT IN ('android_metadata', 'sqlite_sequence')
                """.trimIndent()
            )
        )
        assertEquals(
            ReleasedV374DatabaseFixture.EXPECTED_EXPLICIT_INDEX_COUNT,
            singleInt(
                """
                SELECT COUNT(*) FROM sqlite_master
                WHERE type = 'index' AND sql IS NOT NULL
                """.trimIndent()
            )
        )
    }


    private fun SupportSQLiteDatabase.insertVersion71Rows(
        includeMalformedWallet: Boolean,
        includeSecondValidWallet: Boolean,
        primarySubstratePublicKey: ByteArray,
        primarySubstrateCryptoType: String,
        primarySubstrateAccountId: ByteArray,
        primaryEthereumPublicKey: ByteArray?,
        primaryEthereumAddress: ByteArray?
    ) {
        execSQL(
            """
            INSERT INTO meta_accounts(
                id,
                substratePublicKey,
                substrateCryptoType,
                substrateAccountId,
                ethereumPublicKey,
                ethereumAddress,
                name,
                isSelected,
                position,
                isBackedUp,
                googleBackupAddress,
                initialized
            ) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any?>(
                META_ID,
                primarySubstratePublicKey,
                primarySubstrateCryptoType,
                primarySubstrateAccountId,
                primaryEthereumPublicKey,
                primaryEthereumAddress,
                ADVERSARIAL_WALLET_NAME,
                1,
                17,
                1,
                "backup+'\";\uD83D\uDD10@example.invalid",
                1
            )
        )
        if (includeMalformedWallet) {
            execSQL(
                """
                INSERT INTO meta_accounts(
                    id,
                    substratePublicKey,
                    substrateCryptoType,
                    substrateAccountId,
                    name,
                    isSelected,
                    position,
                    isBackedUp,
                    initialized
                ) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any>(
                    MALFORMED_META_ID,
                    MALFORMED_PUBLIC_KEY,
                    "ED25519",
                    MALFORMED_ACCOUNT_ID,
                    "Malformed secret wallet",
                    0,
                    18,
                    0,
                    1
                )
            )
        }
        if (includeSecondValidWallet) {
            execSQL(
                """
                INSERT INTO meta_accounts(
                    id,
                    substratePublicKey,
                    substrateCryptoType,
                    substrateAccountId,
                    ethereumPublicKey,
                    ethereumAddress,
                    name,
                    isSelected,
                    position,
                    isBackedUp,
                    initialized
                ) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any>(
                    SECOND_VALID_META_ID,
                    SECOND_VALID_PUBLIC_KEY,
                    "SR25519",
                    SECOND_VALID_ACCOUNT_ID,
                    ETHEREUM_PUBLIC_KEY,
                    ETHEREUM_ADDRESS,
                    "Second valid wallet",
                    0,
                    18,
                    1,
                    1
                )
            )
        }
        execSQL(
            """
            INSERT INTO chains(
                id,
                name,
                icon,
                prefix,
                isEthereumBased,
                isTestNet,
                hasCrowdloans,
                supportStakingPool,
                isEthereumChain,
                isChainlinkProvider,
                supportNft,
                isUsesAppId,
                remoteAssetsSource
            ) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any>(
                CHAIN_ID,
                ADVERSARIAL_CHAIN_NAME,
                "chain-icon",
                42,
                1,
                0,
                1,
                1,
                0,
                1,
                1,
                0,
                REMOTE_ASSET_SOURCE
            )
        )
        execSQL(
            """
            INSERT INTO chain_assets(
                id,
                name,
                symbol,
                chainId,
                icon,
                priceId,
                staking,
                precision,
                purchaseProviders,
                isUtility,
                type,
                currencyId,
                existentialDeposit,
                color,
                isNative,
                ethereumType,
                priceProvider
            ) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any?>(
                ASSET_ID,
                "Asset ' \" ; -- 雪",
                "FIX",
                CHAIN_ID,
                "asset-icon",
                PRICE_ID,
                "ALLOWED",
                18,
                "[\"fixture-provider\"]",
                1,
                "ERC20",
                "{\"Token\":\"FIX\"}",
                "123456789",
                "#00FF00",
                0,
                "legacy-ethereum-type",
                PRICE_PROVIDER
            )
        )
        execSQL(
            """
            INSERT INTO assets(
                id,
                chainId,
                accountId,
                metaId,
                tokenPriceId,
                freeInPlanks,
                reservedInPlanks,
                miscFrozenInPlanks,
                feeFrozenInPlanks,
                bondedInPlanks,
                redeemableInPlanks,
                unbondingInPlanks,
                sortIndex,
                enabled,
                markedNotNeed,
                chainAccountName,
                status
            ) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any?>(
                ASSET_ID,
                CHAIN_ID,
                ASSET_ACCOUNT_ID,
                META_ID,
                PRICE_ID,
                "999999999999999999999999999999",
                "7",
                "6",
                "5",
                "4",
                "3",
                "2",
                ASSET_SORT_INDEX,
                0,
                1,
                "Account ' \" ; -- \u0000",
                "SYNCED"
            )
        )
        execSQL(
            """
            INSERT INTO chain_accounts(
                metaId,
                chainId,
                publicKey,
                accountId,
                cryptoType,
                name,
                initialized
            ) VALUES(?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any>(
                META_ID,
                CHAIN_ID,
                CHAIN_ACCOUNT_PUBLIC_KEY,
                CHAIN_ACCOUNT_ID,
                "SR25519",
                "Derived ' \" ; --",
                1
            )
        )
        execSQL(
            "INSERT INTO favorite_chains(metaId, chainId, isFavorite) VALUES(?, ?, ?)",
            arrayOf<Any>(META_ID, CHAIN_ID, 1)
        )
        execSQL(
            """
            INSERT INTO nomis_wallet_score(
                metaId,
                score,
                updated,
                nativeBalanceUsd,
                holdTokensUsd,
                walletAgeInMonths,
                totalTransactions,
                rejectedTransactions,
                avgTransactionTimeInHours,
                maxTransactionTimeInHours,
                minTransactionTimeInHours,
                scoredAt
            ) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any>(
                META_ID,
                91,
                Long.MAX_VALUE,
                "123.45",
                "67.89",
                120,
                9999,
                2,
                1.25,
                48.5,
                0.01,
                "2099-12-31T23:59:59Z"
            )
        )
        execSQL(
            """
            INSERT INTO chain_nodes(chainId, url, name, isActive, isDefault)
            VALUES(?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any>(CHAIN_ID, NODE_URL, "Node ' \" ; --", 1, 0)
        )
        execSQL(
            """
            INSERT INTO chain_explorers(chainId, type, types, url)
            VALUES(?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any>(CHAIN_ID, "SUBSCAN", "[\"SUBSCAN\"]", EXPLORER_URL)
        )
        execSQL(
            """
            INSERT INTO address_book(address, name, chainId, created)
            VALUES(?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any>("fixture-address", "Recipient ' \" ; -- 雪", CHAIN_ID, Long.MAX_VALUE)
        )
        execSQL(
            """
            INSERT INTO token_price(priceId, fiatRate, fiatSymbol, recentRateChange)
            VALUES(?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any?>(PRICE_ID, "123.45", null, "-0.01")
        )
    }

    private fun openProductionDatabase(
        databaseName: String,
        preferences: EncryptedPreferences
    ): AppDatabase = AppDatabase.create(
        context = context,
        databaseName = databaseName,
        storeV1 = SecretStoreV1Impl(preferences),
        storeV2 = SecretStoreV2(preferences),
        encryptedPreferences = preferences,
        substrateSecretStore = SubstrateSecretStore(preferences),
        ethereumSecretStore = EthereumSecretStore(preferences)
    ).also {
        it.openHelper.writableDatabase
    }

    private inline fun <T> AppDatabase.useDatabase(block: (AppDatabase) -> T): T =
        try {
            block(this)
        } finally {
            close()
        }

    private fun assertProductionOpenFails(
        databaseName: String,
        preferences: FaultInjectingEncryptedPreferences
    ) {
        var database: AppDatabase? = null
        var failure: Throwable? = null
        try {
            try {
                database = AppDatabase.create(
                    context = context,
                    databaseName = databaseName,
                    storeV1 = SecretStoreV1Impl(preferences),
                    storeV2 = SecretStoreV2(preferences),
                    encryptedPreferences = preferences,
                    substrateSecretStore = SubstrateSecretStore(preferences),
                    ethereumSecretStore = EthereumSecretStore(preferences)
                )
                database?.openHelper?.writableDatabase
            } catch (caught: Throwable) {
                failure = caught
            }

            assertNotNull(
                "Expected injected secret-store failure; observed operations: " +
                    preferences.operationTrace(),
                failure
            )
        } finally {
            database?.close()
        }
    }

    private fun realEncryptedPreferences(
        encryptionUtil: EncryptionUtil
    ): EncryptedPreferences {
        return EncryptedPreferencesImpl(
            preferences = PreferencesImpl(walletPreferences()),
            encryptionUtil = encryptionUtil
        )
    }

    private fun walletPreferences() = context.getSharedPreferences(
        SHARED_PREFERENCES_FILE,
        Context.MODE_PRIVATE
    )

    private fun keyPreferences() = context.getSharedPreferences(
        KEY_ALIAS,
        Context.MODE_PRIVATE
    )

    private fun clearRealEncryptionStorage() {
        walletPreferences().edit().clear().commit()
        keyPreferences().edit().clear().commit()
        val keyStore = KeyStore.getInstance(KEY_STORE_PROVIDER).apply {
            load(null)
        }
        if (keyStore.containsAlias(KEY_ALIAS)) {
            keyStore.deleteEntry(KEY_ALIAS)
        }
    }

    private fun encryptLegacyCiphertext(
        key: ByteArray,
        plaintext: String
    ): String {
        val iv = ByteArray(LEGACY_IV_BYTES) { index -> (index + 1).toByte() }
        val cipher = Cipher.getInstance(AES_CBC_TRANSFORMATION)
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key, AES_ALGORITHM),
            IvParameterSpec(iv)
        )
        return Base64.encodeToString(
            iv + cipher.doFinal(plaintext.toByteArray()),
            Base64.NO_WRAP
        )
    }

    private fun assertVersion71RowsStillIntact(
        databaseName: String,
        additionalMetaId: Long? = null
    ) {
        openRawReadOnly(databaseName).use { db ->
            assertEquals(71, db.version)
            assertEquals(1, db.singleInt("SELECT COUNT(*) FROM meta_accounts WHERE id = ?", META_ID))
            if (additionalMetaId != null) {
                assertEquals(
                    1,
                    db.singleInt("SELECT COUNT(*) FROM meta_accounts WHERE id = ?", additionalMetaId)
                )
            }
            assertEquals(1, db.singleInt("SELECT COUNT(*) FROM chains WHERE id = ?", CHAIN_ID))
            assertEquals(
                1,
                db.singleInt("SELECT COUNT(*) FROM chain_assets WHERE chainId = ?", CHAIN_ID)
            )
            assertEquals(1, db.singleInt("SELECT COUNT(*) FROM assets WHERE metaId = ?", META_ID))
            assertEquals(
                1,
                db.singleInt("SELECT COUNT(*) FROM chain_accounts WHERE metaId = ?", META_ID)
            )
            assertEquals(
                1,
                db.singleInt("SELECT COUNT(*) FROM favorite_chains WHERE metaId = ?", META_ID)
            )
            assertEquals(
                1,
                db.singleInt("SELECT COUNT(*) FROM nomis_wallet_score WHERE metaId = ?", META_ID)
            )
            assertFalse(db.columnExists("meta_accounts", "tonPublicKey"))
            assertFalse(db.columnExists("chains", "ecosystem"))
            assertTrue(db.columnExists("chain_assets", "ethereumType"))
        }
    }

    private fun assertVersion71PreflightFailurePreservesExactState(
        databaseName: String,
        preferences: FaultInjectingEncryptedPreferences,
        repair: () -> Unit
    ) {
        val exactBefore = preferences.rawSnapshot()
        repeat(2) {
            assertProductionOpenFails(databaseName, preferences)
            openRawReadOnly(databaseName).use { database ->
                assertEquals(71, database.version)
            }
            assertEquals(exactBefore, preferences.rawSnapshot())
        }

        repair()
        openProductionDatabase(databaseName, preferences).useDatabase {
            assertEquals(77, it.openHelper.writableDatabase.version)
        }
        val exactAfterRecovery = preferences.rawSnapshot()
        openProductionDatabase(databaseName, preferences).useDatabase {
            assertEquals(77, it.openHelper.writableDatabase.version)
        }
        assertEquals(exactAfterRecovery, preferences.rawSnapshot())
    }

    private fun restoreVersion71ChainAccountId(databaseName: String) {
        openRawReadWrite(databaseName).use {
            it.execSQL(
                "UPDATE chain_accounts SET accountId = ? WHERE metaId = ?",
                arrayOf(CHAIN_ACCOUNT_ID, META_ID)
            )
        }
    }

    private fun assertSubstratePrivateProofFailure(
        databaseName: String,
        cryptoType: String,
        validKeypair: Keypair,
        mismatchedKeypair: Keypair
    ) {
        val encoded = encodedVersion69Secrets(substrateKeypair = mismatchedKeypair)
        val preferences = FaultInjectingEncryptedPreferences().apply {
            putEncryptedString(oldSecretKey(META_ID), encoded)
        }
        createVersion71Database(
            databaseName = databaseName,
            primarySubstratePublicKey = validKeypair.publicKey,
            primarySubstrateCryptoType = cryptoType,
            primarySubstrateAccountId = validKeypair.publicKey.substrateAccountId()
        )

        openProductionDatabase(databaseName, preferences).useDatabase { database ->
            val db = database.openHelper.writableDatabase
            assertEquals(77, db.version)
            assertArrayEquals(
                validKeypair.publicKey,
                db.singleBlob("SELECT substratePublicKey FROM meta_accounts WHERE id = ?", META_ID)
            )
        }

        assertProofFailureQuarantined(preferences, encoded)
    }

    private fun assertLegacySecretQuarantined(
        databaseName: String,
        encodedLegacySecret: String,
        substratePublicKey: ByteArray = SUBSTRATE_PUBLIC_KEY,
        substrateCryptoType: String = "SR25519",
        substrateAccountId: ByteArray = substratePublicKey.substrateAccountId(),
        ethereumPublicKey: ByteArray? = ETHEREUM_PUBLIC_KEY,
        ethereumAddress: ByteArray? = ETHEREUM_ADDRESS
    ) {
        val preferences = FaultInjectingEncryptedPreferences().apply {
            putEncryptedString(oldSecretKey(META_ID), encodedLegacySecret)
        }
        createVersion71Database(
            databaseName = databaseName,
            primarySubstratePublicKey = substratePublicKey,
            primarySubstrateCryptoType = substrateCryptoType,
            primarySubstrateAccountId = substrateAccountId,
            primaryEthereumPublicKey = ethereumPublicKey,
            primaryEthereumAddress = ethereumAddress
        )

        openProductionDatabase(databaseName, preferences).useDatabase { database ->
            assertEquals(77, database.openHelper.writableDatabase.version)
        }

        assertProofFailureQuarantined(preferences, encodedLegacySecret)
    }

    private fun assertLegacyPublicIdentityRecovery(
        databaseName: String,
        encodedLegacySecret: String,
        substratePublicKey: ByteArray = SUBSTRATE_PUBLIC_KEY,
        substrateCryptoType: String = "SR25519",
        substrateAccountId: ByteArray = substratePublicKey.substrateAccountId(),
        ethereumPublicKey: ByteArray? = ETHEREUM_PUBLIC_KEY,
        ethereumAddress: ByteArray? = ETHEREUM_ADDRESS
    ) {
        val preferences = FaultInjectingEncryptedPreferences().apply {
            putEncryptedString(oldSecretKey(META_ID), encodedLegacySecret)
        }
        createVersion71Database(
            databaseName = databaseName,
            primarySubstratePublicKey = substratePublicKey,
            primarySubstrateCryptoType = substrateCryptoType,
            primarySubstrateAccountId = substrateAccountId,
            primaryEthereumPublicKey = ethereumPublicKey,
            primaryEthereumAddress = ethereumAddress
        )

        openProductionDatabase(databaseName, preferences).useDatabase { database ->
            assertEquals(77, database.openHelper.writableDatabase.version)
        }

        assertEquals(
            encodedLegacySecret,
            preferences.getDecryptedString(oldSecretKey(META_ID))
        )
        assertFalse(
            preferences.hasKey(quarantineKey(oldSecretKey(META_ID)))
        )
        assertEquals(
            WalletPublicIdentityRecovery.MARKER_VALUE,
            preferences.getDecryptedString(
                publicIdentityMarker(oldSecretKey(META_ID))
            )
        )
    }

    private fun assertProofFailureQuarantined(
        preferences: EncryptedPreferences,
        encodedLegacySecret: String
    ) {
        assertFalse(preferences.hasKey(oldSecretKey(META_ID)))
        assertFalse(preferences.hasKey(substrateSecretKey(META_ID)))
        assertFalse(preferences.hasKey(ethereumSecretKey(META_ID)))
        assertEquals(
            encodedLegacySecret,
            preferences.getDecryptedString(quarantineKey(oldSecretKey(META_ID)))
        )
    }

    private fun SupportSQLiteDatabase.assertMigratedRowsPreserved() {
        assertEquals(77, version)
        assertEquals(
            ADVERSARIAL_WALLET_NAME,
            singleString("SELECT name FROM meta_accounts WHERE id = ?", META_ID)
        )
        assertArrayEquals(
            SUBSTRATE_PUBLIC_KEY,
            singleBlob("SELECT substratePublicKey FROM meta_accounts WHERE id = ?", META_ID)
        )
        assertEquals(
            "SR25519",
            singleString("SELECT substrateCryptoType FROM meta_accounts WHERE id = ?", META_ID)
        )
        assertArrayEquals(
            SUBSTRATE_ACCOUNT_ID,
            singleBlob("SELECT substrateAccountId FROM meta_accounts WHERE id = ?", META_ID)
        )
        assertArrayEquals(
            ETHEREUM_PUBLIC_KEY,
            singleBlob("SELECT ethereumPublicKey FROM meta_accounts WHERE id = ?", META_ID)
        )
        assertArrayEquals(
            ETHEREUM_ADDRESS,
            singleBlob("SELECT ethereumAddress FROM meta_accounts WHERE id = ?", META_ID)
        )
        assertNull(singleNullableBlob("SELECT tonPublicKey FROM meta_accounts WHERE id = ?", META_ID))

        assertEquals(
            ADVERSARIAL_CHAIN_NAME,
            singleString("SELECT name FROM chains WHERE id = ?", CHAIN_ID)
        )
        assertEquals(
            REMOTE_ASSET_SOURCE,
            singleString("SELECT remoteAssetsSource FROM chains WHERE id = ?", CHAIN_ID)
        )
        assertEquals(
            "EthereumBased",
            singleString("SELECT ecosystem FROM chains WHERE id = ?", CHAIN_ID)
        )
        assertFalse(columnExists("chain_assets", "ethereumType"))
        assertEquals(
            PRICE_PROVIDER,
            singleString(
                "SELECT priceProvider FROM chain_assets WHERE chainId = ? AND id = ?",
                CHAIN_ID,
                ASSET_ID
            )
        )

        assertArrayEquals(
            ASSET_ACCOUNT_ID,
            singleBlob("SELECT accountId FROM assets WHERE metaId = ?", META_ID)
        )
        assertEquals(
            "999999999999999999999999999999",
            singleString("SELECT freeInPlanks FROM assets WHERE metaId = ?", META_ID)
        )
        assertEquals(
            ASSET_SORT_INDEX,
            singleInt("SELECT sortIndex FROM assets WHERE metaId = ?", META_ID)
        )
        assertEquals(0, singleInt("SELECT enabled FROM assets WHERE metaId = ?", META_ID))
        assertEquals(1, singleInt("SELECT markedNotNeed FROM assets WHERE metaId = ?", META_ID))

        assertEquals(
            1,
            singleInt("SELECT COUNT(*) FROM chain_accounts WHERE metaId = ?", META_ID)
        )
        assertArrayEquals(
            CHAIN_ACCOUNT_ID,
            singleBlob("SELECT accountId FROM chain_accounts WHERE metaId = ?", META_ID)
        )
        assertEquals(
            1,
            singleInt("SELECT isFavorite FROM favorite_chains WHERE metaId = ?", META_ID)
        )
        assertEquals(
            91,
            singleInt("SELECT score FROM nomis_wallet_score WHERE metaId = ?", META_ID)
        )
        assertEquals(1, singleInt("SELECT COUNT(*) FROM chain_nodes WHERE url = ?", NODE_URL))
        assertEquals(
            1,
            singleInt("SELECT COUNT(*) FROM chain_explorers WHERE url = ?", EXPLORER_URL)
        )
        assertEquals(
            1,
            singleInt("SELECT COUNT(*) FROM address_book WHERE chainId = ?", CHAIN_ID)
        )
        query("PRAGMA foreign_key_check").use { cursor ->
            assertEquals("Migration introduced foreign-key violations", 0, cursor.count)
        }
    }

    private fun assertSecretsMoved(
        preferences: EncryptedPreferences,
        metaId: Long,
        expectedEntropy: ByteArray? = ENTROPY,
        expectedSeed: ByteArray? = SEED,
        expectedSubstrateDerivationPath: String? = SUBSTRATE_DERIVATION_PATH,
        expectedEthereumDerivationPath: String? = ETHEREUM_DERIVATION_PATH,
        expectedEthereumKeypair: Keypair = ETHEREUM_KEYPAIR,
        expectedSubstrateKeypair: Keypair = SUBSTRATE_KEYPAIR
    ) {
        assertSubstrateSecretsMoved(
            preferences = preferences,
            metaId = metaId,
            expectedEntropy = expectedEntropy,
            expectedSeed = expectedSeed,
            expectedDerivationPath = expectedSubstrateDerivationPath,
            expectedKeypair = expectedSubstrateKeypair
        )

        val ethereum = requireNotNull(EthereumSecretStore(preferences).get(metaId))
        assertNullableByteArrayEquals(expectedEntropy, ethereum[EthereumSecrets.Entropy])
        assertArrayEquals(expectedEthereumKeypair.privateKey, ethereum[EthereumSecrets.Seed])
        assertEquals(
            expectedEthereumDerivationPath,
            ethereum[EthereumSecrets.EthereumDerivationPath]
        )
        val ethereumKeypair = ethereum[EthereumSecrets.EthereumKeypair]
        assertArrayEquals(
            expectedEthereumKeypair.publicKey,
            ethereumKeypair[KeyPairSchema.PublicKey]
        )
        assertArrayEquals(
            expectedEthereumKeypair.privateKey,
            ethereumKeypair[KeyPairSchema.PrivateKey]
        )
    }

    private fun assertSubstrateSecretsMoved(
        preferences: EncryptedPreferences,
        metaId: Long,
        expectedEntropy: ByteArray?,
        expectedSeed: ByteArray?,
        expectedDerivationPath: String? = SUBSTRATE_DERIVATION_PATH,
        expectedKeypair: Keypair = SUBSTRATE_KEYPAIR
    ) {
        val substrate = requireNotNull(SubstrateSecretStore(preferences).get(metaId))
        assertNullableByteArrayEquals(expectedEntropy, substrate[SubstrateSecrets.Entropy])
        assertNullableByteArrayEquals(expectedSeed, substrate[SubstrateSecrets.Seed])
        assertEquals(
            expectedDerivationPath,
            substrate[SubstrateSecrets.SubstrateDerivationPath]
        )
        val substrateKeypair = substrate[SubstrateSecrets.SubstrateKeypair]
        assertArrayEquals(expectedKeypair.publicKey, substrateKeypair[KeyPairSchema.PublicKey])
        assertArrayEquals(expectedKeypair.privateKey, substrateKeypair[KeyPairSchema.PrivateKey])
        assertArrayEquals(
            (expectedKeypair as? Sr25519Keypair)?.nonce,
            substrateKeypair[KeyPairSchema.Nonce]
        )
    }

    private fun assertNullableByteArrayEquals(expected: ByteArray?, actual: ByteArray?) {
        if (expected == null) {
            assertNull(actual)
        } else {
            assertArrayEquals(expected, actual)
        }
    }

    private fun encodedVersion69Secrets(
        entropy: ByteArray? = ENTROPY,
        seed: ByteArray? = SEED,
        includeEthereum: Boolean = true,
        substrateKeypair: Keypair = SUBSTRATE_KEYPAIR,
        ethereumKeypair: Keypair = ETHEREUM_KEYPAIR,
        substrateDerivationPath: String? = SUBSTRATE_DERIVATION_PATH,
        ethereumDerivationPath: String? = ETHEREUM_DERIVATION_PATH
    ): String {
        val secrets = TonMigration.MetaAccountSecretsV69 { struct ->
            struct[Entropy] = entropy
            struct[Seed] = seed
            struct[SubstrateKeypair] = KeyPairSchema { keypair ->
                keypair[PrivateKey] = substrateKeypair.privateKey
                keypair[PublicKey] = substrateKeypair.publicKey
                keypair[Nonce] = (substrateKeypair as? Sr25519Keypair)?.nonce
            }
            struct[SubstrateDerivationPath] = substrateDerivationPath
            struct[EthereumKeypair] = if (includeEthereum) {
                KeyPairSchema { keypair ->
                    keypair[PrivateKey] = ethereumKeypair.privateKey
                    keypair[PublicKey] = ethereumKeypair.publicKey
                    keypair[Nonce] = null
                }
            } else {
                null
            }
            struct[EthereumDerivationPath] = ethereumDerivationPath
        }

        return secrets.toHexString()
    }

    private fun SupportSQLiteDatabase.singleInt(sql: String, vararg bindArgs: Any?): Int =
        query(sql, bindArgs).use { cursor ->
            check(cursor.moveToFirst())
            cursor.getInt(0)
        }

    private fun SupportSQLiteDatabase.singleString(sql: String, vararg bindArgs: Any?): String =
        query(sql, bindArgs).use { cursor ->
            check(cursor.moveToFirst())
            cursor.getString(0)
        }

    private fun SupportSQLiteDatabase.singleBlob(sql: String, vararg bindArgs: Any?): ByteArray =
        query(sql, bindArgs).use { cursor ->
            check(cursor.moveToFirst())
            cursor.getBlob(0)
        }

    private fun SupportSQLiteDatabase.singleNullableBlob(
        sql: String,
        vararg bindArgs: Any?
    ): ByteArray? =
        query(sql, bindArgs).use { cursor ->
            check(cursor.moveToFirst())
            if (cursor.isNull(0)) null else cursor.getBlob(0)
        }

    private fun SupportSQLiteDatabase.columnExists(tableName: String, columnName: String): Boolean =
        query("PRAGMA table_info(`$tableName`)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex) == columnName) return@use true
            }
            false
        }

    private fun openRawReadOnly(databaseName: String): SQLiteDatabase =
        SQLiteDatabase.openDatabase(
            context.getDatabasePath(databaseName).path,
            null,
            SQLiteDatabase.OPEN_READONLY
        )

    private fun openRawReadWrite(databaseName: String): SQLiteDatabase =
        SQLiteDatabase.openDatabase(
            context.getDatabasePath(databaseName).path,
            null,
            SQLiteDatabase.OPEN_READWRITE
        )

    private fun SQLiteDatabase.singleInt(sql: String, vararg bindArgs: Any): Int =
        rawQuery(sql, bindArgs.map(Any::toString).toTypedArray()).use { cursor ->
            check(cursor.moveToFirst())
            cursor.getInt(0)
        }

    private fun SQLiteDatabase.columnExists(tableName: String, columnName: String): Boolean =
        rawQuery("PRAGMA table_info(`$tableName`)", null).use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex) == columnName) return@use true
            }
            false
        }

    private enum class FailureOperation {
        BEFORE_PUT,
        AFTER_REMOVE
    }

    private data class ConfiguredFailure(
        val operation: FailureOperation,
        val key: String,
        val persistent: Boolean
    )

    private class FaultInjectingEncryptedPreferences : EncryptedPreferences {
        private val lock = Any()
        private val values = mutableMapOf<String, String>()
        private val operations = mutableListOf<String>()
        private var configuredFailure: ConfiguredFailure? = null
        private var mutationBeforeSnapshotBoundReplacement:
            (FaultInjectingEncryptedPreferences.() -> Unit)? = null
        private var repeatSnapshotBoundReplacementMutation = false
        private var failureCount = 0
        var secureStorageUnavailableKey: String? = null

        fun mutateBeforeNextSnapshotBoundReplacement(
            mutation: FaultInjectingEncryptedPreferences.() -> Unit
        ) = synchronized(lock) {
            mutationBeforeSnapshotBoundReplacement = mutation
            repeatSnapshotBoundReplacementMutation = false
        }

        fun mutateBeforeEverySnapshotBoundReplacement(
            mutation: FaultInjectingEncryptedPreferences.() -> Unit
        ) = synchronized(lock) {
            mutationBeforeSnapshotBoundReplacement = mutation
            repeatSnapshotBoundReplacementMutation = true
        }

        fun clearSnapshotBoundReplacementInterception() =
            synchronized(lock) {
                mutationBeforeSnapshotBoundReplacement = null
                repeatSnapshotBoundReplacementMutation = false
            }

        fun failOnce(operation: FailureOperation, key: String) = synchronized(lock) {
            configuredFailure = ConfiguredFailure(operation, key, persistent = false)
        }

        fun failAlways(operation: FailureOperation, key: String) = synchronized(lock) {
            configuredFailure = ConfiguredFailure(operation, key, persistent = true)
        }

        fun clearFailure() = synchronized(lock) {
            configuredFailure = null
        }

        override fun putEncryptedString(field: String, value: String) = synchronized(lock) {
            operations += "put:$field"
            throwIfConfigured(FailureOperation.BEFORE_PUT, field)
            values[field] = value
        }

        override fun getDecryptedString(field: String): String? = synchronized(lock) {
            operations += "get:$field"
            if (field == secureStorageUnavailableKey) {
                throw WalletSecureStorageUnavailableException(
                    "Injected global secure-storage failure"
                )
            }
            values[field]
        }

        override fun hasKey(field: String): Boolean = synchronized(lock) {
            operations += "has:$field"
            field in values
        }

        override fun hasKeyWithPrefix(prefix: String): Boolean =
            synchronized(lock) {
                values.keys.any { it.startsWith(prefix) }
            }

        override fun keysWithPrefixes(
            prefixes: Set<String>,
            maxResultCount: Int,
            maxKeyBytes: Int,
            maxTotalKeyBytes: Int,
            failOnOversizedMatch: Boolean
        ): Set<String> = synchronized(lock) {
            boundedTestPreferenceKeys(
                keys = values.keys,
                prefixes = prefixes,
                maxResultCount = maxResultCount,
                maxKeyBytes = maxKeyBytes,
                maxTotalKeyBytes = maxTotalKeyBytes,
                failOnOversizedMatch = failOnOversizedMatch
            )
        }

        override fun removeKey(field: String) = synchronized(lock) {
            operations += "remove:$field"
            values.remove(field)
            throwIfConfigured(FailureOperation.AFTER_REMOVE, field)
        }

        override fun replaceEncryptedStringsDurably(
            valuesToPut: Map<String, String>,
            keysToRemove: Set<String>
        ) {
            require(valuesToPut.keys.intersect(keysToRemove).isEmpty())
            valuesToPut.forEach(::putEncryptedString)
            keysToRemove.forEach(::removeKey)
        }

        override fun replaceEncryptedStringsDurablyIfStatesMatch(
            expectedStates:
            Map<String, EncryptedPreferenceSnapshot?>,
            valuesToPut: Map<String, String>,
            keysToRemove: Set<String>,
            snapshotMoves: List<EncryptedPreferenceSnapshotMove>
        ): Boolean = synchronized(lock) {
            operations += "cas"
            mutationBeforeSnapshotBoundReplacement?.let { mutation ->
                if (!repeatSnapshotBoundReplacementMutation) {
                    mutationBeforeSnapshotBoundReplacement = null
                }
                mutation(this)
            }
            super<EncryptedPreferences>
                .replaceEncryptedStringsDurablyIfStatesMatch(
                    expectedStates = expectedStates,
                    valuesToPut = valuesToPut,
                    keysToRemove = keysToRemove,
                    snapshotMoves = snapshotMoves
                )
        }

        override fun quarantineEncryptedStringDurably(
            sourceKey: String,
            quarantineKey: String,
            expectedSnapshot: EncryptedPreferenceSnapshot
        ): Boolean = synchronized(lock) {
            val sourceExists = sourceKey in values
            if (!sourceExists) {
                check(quarantineKey in values)
                return@synchronized expectedSnapshot
                    .matchesUnencryptedStorageValue(
                        values.getValue(quarantineKey)
                    )
            }

            val sourceValue = values.getValue(sourceKey)
            if (
                !expectedSnapshot.matchesUnencryptedStorageValue(sourceValue)
            ) {
                return@synchronized false
            }
            val existingQuarantine = values[quarantineKey]
            check(existingQuarantine == null || existingQuarantine == sourceValue)
            replaceEncryptedStringsDurably(
                valuesToPut = mapOf(quarantineKey to sourceValue),
                keysToRemove = setOf(sourceKey)
            )
            true
        }

        override fun requireDurableStorageHealthy() = Unit

        fun operationTrace(): List<String> = synchronized(lock) {
            operations.toList()
        }

        fun rawSnapshot(): Map<String, String> = synchronized(lock) {
            values.toMap()
        }

        fun injectedFailureCount(): Int = synchronized(lock) {
            failureCount
        }

        private fun throwIfConfigured(operation: FailureOperation, key: String) {
            val failure = configuredFailure
            if (failure?.operation == operation && failure.key == key) {
                failureCount += 1
                if (!failure.persistent) {
                    configuredFailure = null
                }
                throw InjectedSecretStoreFailure("$operation:$key")
            }
        }
    }

    private class InjectedSecretStoreFailure(message: String) : RuntimeException(message)

    private companion object {
        const val PRESERVATION_DATABASE = "ton-migration-preservation"
        const val MALFORMED_SECRET_DATABASE = "ton-migration-malformed-secret"
        const val DECRYPTION_FAILURE_DATABASE = "ton-migration-decryption-failure"
        const val PUBLIC_KEY_MISMATCH_DATABASE = "ton-migration-public-key-mismatch"
        const val SR25519_PRIVATE_MISMATCH_DATABASE = "ton-migration-sr25519-private-mismatch"
        const val ED25519_PRIVATE_MISMATCH_DATABASE = "ton-migration-ed25519-private-mismatch"
        const val ECDSA_PRIVATE_MISMATCH_DATABASE = "ton-migration-ecdsa-private-mismatch"
        const val ETHEREUM_PRIVATE_MISMATCH_DATABASE = "ton-migration-ethereum-private-mismatch"
        const val VALID_SR25519_DATABASE = "ton-migration-valid-sr25519"
        const val VALID_ED25519_DATABASE = "ton-migration-valid-ed25519"
        const val VALID_ECDSA_DATABASE = "ton-migration-valid-ecdsa"
        const val WRONG_SUBSTRATE_ENTROPY_DATABASE = "ton-migration-wrong-substrate-entropy"
        const val WRONG_SUBSTRATE_SEED_DATABASE = "ton-migration-wrong-substrate-seed"
        const val WRONG_SUBSTRATE_PASSWORD_DATABASE = "ton-migration-wrong-substrate-password"
        const val WRONG_SUBSTRATE_JUNCTION_DATABASE = "ton-migration-wrong-substrate-junction"
        const val WRONG_ETHEREUM_PATH_DATABASE = "ton-migration-wrong-ethereum-path"
        const val WRONG_ETHEREUM_ENTROPY_DATABASE = "ton-migration-wrong-ethereum-entropy"
        const val MISSING_ETHEREUM_KEYPAIR_DATABASE = "ton-migration-missing-ethereum-keypair"
        const val UNOWNED_ETHEREUM_KEYPAIR_DATABASE = "ton-migration-unowned-ethereum-keypair"
        const val PARTIAL_ETHEREUM_IDENTITY_DATABASE = "ton-migration-partial-ethereum-identity"
        const val UNKNOWN_SUBSTRATE_CRYPTO_DATABASE =
            "ton-migration-unknown-substrate-crypto"
        const val SUBSTRATE_ACCOUNT_ID_MISMATCH_DATABASE =
            "ton-migration-substrate-account-id-mismatch"
        const val ETHEREUM_ADDRESS_MISMATCH_DATABASE =
            "ton-migration-ethereum-address-mismatch"
        const val OFF_CURVE_ETHEREUM_DATABASE =
            "ton-migration-off-curve-ethereum"
        const val ORPHAN_ETHEREUM_PATH_DATABASE = "ton-migration-orphan-ethereum-path"
        const val OVERSIZED_SUBSTRATE_PATH_DATABASE = "ton-migration-oversized-substrate-path"
        const val OVERSIZED_ETHEREUM_PATH_DATABASE = "ton-migration-oversized-ethereum-path"
        const val QUARANTINE_FAILURE_DATABASE = "ton-migration-quarantine-failure"
        const val QUARANTINE_RETRY_DATABASE = "ton-migration-quarantine-retry"
        const val GLOBAL_STORAGE_FAILURE_DATABASE = "ton-migration-global-storage-failure"
        const val SEED_ONLY_DATABASE = "ton-migration-seed-only"
        const val KEYPAIR_ONLY_DATABASE = "ton-migration-keypair-only"
        const val ETHEREUM_FAILURE_DATABASE = "ton-migration-ethereum-failure"
        const val MULTI_WALLET_FAILURE_DATABASE = "ton-migration-multi-wallet-failure"
        const val CLEAR_FAILURE_DATABASE = "ton-migration-clear-failure"
        const val PAYLOAD_PROVIDER_FAILURE_DATABASE =
            "ton-migration-payload-provider-failure"
        const val NON_POSITIVE_META_ID_DATABASE =
            "ton-migration-non-positive-meta-id"
        const val TEXT_CHAIN_ACCOUNT_ID_DATABASE =
            "ton-migration-text-chain-account-id"
        const val MISSING_ADDRESS_BOOK_INDEX_DATABASE =
            "ton-migration-missing-address-book-index"
        const val OVERSIZED_CHAIN_ACCOUNT_ID_DATABASE =
            "ton-migration-oversized-chain-account-id"
        const val INVALID_ROOT_MARKER_DATABASE =
            "ton-migration-invalid-root-marker"
        const val INVALID_CHAIN_MARKER_DATABASE =
            "ton-migration-invalid-chain-marker"
        const val STALE_LEGACY_SNAPSHOT_DATABASE =
            "ton-migration-stale-legacy-snapshot"
        const val CONCURRENT_TARGET_DATABASE =
            "ton-migration-concurrent-target"
        const val EXACT_TARGET_MISSING_SIBLING_DATABASE =
            "ton-migration-exact-target-missing-sibling"

        const val META_ID = 71L
        const val MALFORMED_META_ID = 72L
        const val SECOND_VALID_META_ID = 73L
        const val CHAIN_ID = "fixture-chain-'\";--"
        const val ASSET_ID = "fixture-asset"
        const val PRICE_ID = "fixture-price"
        const val PRICE_PROVIDER = "coingecko"
        const val REMOTE_ASSET_SOURCE = "https://invalid.example/'\";--"
        const val NODE_URL = "wss://invalid.example/'\";--"
        const val EXPLORER_URL = "https://invalid.example/explorer/'\";--"
        const val ASSET_SORT_INDEX = 2_147_483_646
        const val ADVERSARIAL_WALLET_NAME = "Wallet ' \" ; -- \u0000 雪 \uD83D\uDD10"
        const val ADVERSARIAL_CHAIN_NAME = "Chain ' \" ; DROP TABLE chains; -- \uD83D\uDEE1"
        const val MALFORMED_SECRET = "not-hex-\u0000-\uD83D\uDD10"
        const val SUBSTRATE_DERIVATION_PATH = "//hard///password"
        const val ETHEREUM_DERIVATION_PATH = "//44//60//0/0/0"
        const val KEY_STORE_PROVIDER = "AndroidKeyStore"
        const val KEY_ALIAS = "key_alias"
        const val AES_ALGORITHM = "AES"
        const val AES_CBC_TRANSFORMATION = "AES/CBC/PKCS5Padding"
        const val LEGACY_IV_BYTES = 16

        val ENTROPY = ByteArray(16) { (it * 7 + 3).toByte() }
        val DIFFERENT_ENTROPY = ByteArray(16) { (it * 11 + 5).toByte() }
        val MNEMONIC = MnemonicCreator.fromEntropy(ENTROPY)
        val DECODED_SUBSTRATE_DERIVATION_PATH =
            SubstrateJunctionDecoder.decode(SUBSTRATE_DERIVATION_PATH)
        val SEED = SubstrateSeedFactory.deriveSeed32(
            mnemonicWords = MNEMONIC.words,
            password = DECODED_SUBSTRATE_DERIVATION_PATH.password
        ).seed
        val SUBSTRATE_KEYPAIR = SubstrateKeypairFactory.generate(
            encryptionType = EncryptionType.SR25519,
            seed = SEED,
            junctions = DECODED_SUBSTRATE_DERIVATION_PATH.junctions
        )
        val SUBSTRATE_PUBLIC_KEY = SUBSTRATE_KEYPAIR.publicKey
        val SUBSTRATE_PRIVATE_KEY = SUBSTRATE_KEYPAIR.privateKey
        val SUBSTRATE_NONCE = (SUBSTRATE_KEYPAIR as Sr25519Keypair).nonce
        val ED25519_KEYPAIR = SubstrateKeypairFactory.generate(
            encryptionType = EncryptionType.ED25519,
            seed = SEED,
            junctions = DECODED_SUBSTRATE_DERIVATION_PATH.junctions
        )
        val ECDSA_KEYPAIR = SubstrateKeypairFactory.generate(
            encryptionType = EncryptionType.ECDSA,
            seed = SEED,
            junctions = DECODED_SUBSTRATE_DERIVATION_PATH.junctions
        )
        val SUBSTRATE_ACCOUNT_ID = SUBSTRATE_PUBLIC_KEY.substrateAccountId()
        val DECODED_ETHEREUM_DERIVATION_PATH =
            BIP32JunctionDecoder.decode(ETHEREUM_DERIVATION_PATH)
        val ETHEREUM_SEED = EthereumSeedFactory.deriveSeed32(
            mnemonicWords = MNEMONIC.words,
            password = DECODED_ETHEREUM_DERIVATION_PATH.password
        ).seed
        val ETHEREUM_KEYPAIR = EthereumKeypairFactory.generate(
            seed = ETHEREUM_SEED,
            junctions = DECODED_ETHEREUM_DERIVATION_PATH.junctions
        )
        val ETHEREUM_PRIVATE_KEY = ETHEREUM_KEYPAIR.privateKey
        val ETHEREUM_PUBLIC_KEY = ETHEREUM_KEYPAIR.publicKey
        val ETHEREUM_ADDRESS = ETHEREUM_PUBLIC_KEY.ethereumAddressFromPublicKey()
        val ASSET_ACCOUNT_ID = byteArrayOf(0, 1, 2, 0x7F, 0x80.toByte(), 0xFF.toByte())
        val CHAIN_ACCOUNT_PUBLIC_KEY = ByteArray(32) { (it + 5).toByte() }
        val CHAIN_ACCOUNT_ID = ByteArray(32) { (it + 7).toByte() }
        val MALFORMED_PUBLIC_KEY = ByteArray(32) { (it + 9).toByte() }
        val MALFORMED_ACCOUNT_ID = ByteArray(32) { (it + 10).toByte() }
        val SECOND_VALID_SUBSTRATE_KEYPAIR = SubstrateKeypairFactory.generate(
            encryptionType = EncryptionType.SR25519,
            seed = ByteArray(32) { (it + 33).toByte() },
            junctions = emptyList()
        )
        val SECOND_VALID_PUBLIC_KEY = SECOND_VALID_SUBSTRATE_KEYPAIR.publicKey
        val SECOND_VALID_ACCOUNT_ID = SECOND_VALID_PUBLIC_KEY.substrateAccountId()

        val DATABASE_NAMES = listOf(
            PRESERVATION_DATABASE,
            MALFORMED_SECRET_DATABASE,
            DECRYPTION_FAILURE_DATABASE,
            PUBLIC_KEY_MISMATCH_DATABASE,
            SR25519_PRIVATE_MISMATCH_DATABASE,
            ED25519_PRIVATE_MISMATCH_DATABASE,
            ECDSA_PRIVATE_MISMATCH_DATABASE,
            ETHEREUM_PRIVATE_MISMATCH_DATABASE,
            VALID_SR25519_DATABASE,
            VALID_ED25519_DATABASE,
            VALID_ECDSA_DATABASE,
            WRONG_SUBSTRATE_ENTROPY_DATABASE,
            WRONG_SUBSTRATE_SEED_DATABASE,
            WRONG_SUBSTRATE_PASSWORD_DATABASE,
            WRONG_SUBSTRATE_JUNCTION_DATABASE,
            WRONG_ETHEREUM_PATH_DATABASE,
            WRONG_ETHEREUM_ENTROPY_DATABASE,
            MISSING_ETHEREUM_KEYPAIR_DATABASE,
            UNOWNED_ETHEREUM_KEYPAIR_DATABASE,
            PARTIAL_ETHEREUM_IDENTITY_DATABASE,
            UNKNOWN_SUBSTRATE_CRYPTO_DATABASE,
            SUBSTRATE_ACCOUNT_ID_MISMATCH_DATABASE,
            ETHEREUM_ADDRESS_MISMATCH_DATABASE,
            OFF_CURVE_ETHEREUM_DATABASE,
            ORPHAN_ETHEREUM_PATH_DATABASE,
            OVERSIZED_SUBSTRATE_PATH_DATABASE,
            OVERSIZED_ETHEREUM_PATH_DATABASE,
            QUARANTINE_FAILURE_DATABASE,
            QUARANTINE_RETRY_DATABASE,
            GLOBAL_STORAGE_FAILURE_DATABASE,
            SEED_ONLY_DATABASE,
            KEYPAIR_ONLY_DATABASE,
            ETHEREUM_FAILURE_DATABASE,
            MULTI_WALLET_FAILURE_DATABASE,
            CLEAR_FAILURE_DATABASE,
            PAYLOAD_PROVIDER_FAILURE_DATABASE,
            NON_POSITIVE_META_ID_DATABASE,
            TEXT_CHAIN_ACCOUNT_ID_DATABASE,
            MISSING_ADDRESS_BOOK_INDEX_DATABASE,
            OVERSIZED_CHAIN_ACCOUNT_ID_DATABASE,
            INVALID_ROOT_MARKER_DATABASE,
            INVALID_CHAIN_MARKER_DATABASE,
            STALE_LEGACY_SNAPSHOT_DATABASE,
            CONCURRENT_TARGET_DATABASE,
            EXACT_TARGET_MISSING_SIBLING_DATABASE
        )

        fun oldSecretKey(metaId: Long) = "$metaId:ACCESS_SECRETS"
        fun publicIdentityMarker(activeKey: String) =
            WalletPublicIdentityRecovery.keyFor(META_ID, activeKey)
        fun substrateSecretKey(metaId: Long) = "$metaId:SUBSTRATE_SECRETS"
        fun ethereumSecretKey(metaId: Long) = "$metaId:ETHEREUM_SECRETS"
        fun quarantineKey(activeKey: String) = WalletSecretQuarantine.keyFor(activeKey)
    }
}
