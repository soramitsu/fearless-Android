package jp.co.soramitsu.coredb.migrations

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import jp.co.soramitsu.common.data.secrets.v1.Keypair
import jp.co.soramitsu.common.data.secrets.v1.SecretStoreV1Impl
import jp.co.soramitsu.common.data.secrets.v2.ChainAccountSecrets
import jp.co.soramitsu.common.data.secrets.v2.ChainAccountSecretValidation
import jp.co.soramitsu.common.data.secrets.v2.ChainAccountSecretValidator
import jp.co.soramitsu.common.data.secrets.v2.KeyPairSchema
import jp.co.soramitsu.common.data.secrets.v2.SecretStoreV2
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecretStore
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecretStore
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferenceSnapshot
import jp.co.soramitsu.common.data.storage.encrypt.WalletPublicIdentityIntegrityException
import jp.co.soramitsu.common.data.storage.encrypt.WalletPublicIdentityRecovery
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretQuarantine
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecureStorageUnavailableException
import jp.co.soramitsu.common.utils.deriveSeed32
import jp.co.soramitsu.common.utils.invoke
import jp.co.soramitsu.common.utils.substrateAccountId
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.coredb.AppDatabase
import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.junction.SubstrateJunctionDecoder
import jp.co.soramitsu.fearless_utils.encrypt.keypair.Keypair as FearlessKeypair
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.Sr25519Keypair
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.mnemonic.MnemonicCreator
import jp.co.soramitsu.fearless_utils.encrypt.seed.substrate.SubstrateSeedFactory
import jp.co.soramitsu.fearless_utils.extensions.toHexString as toPlainHexString
import jp.co.soramitsu.fearless_utils.scale.toHexString
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ChainAccountSecretIntegrityMigrationTest {

    private val context: Context =
        InstrumentationRegistry.getInstrumentation().targetContext
    private val createdDatabases = mutableSetOf<String>()

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @After
    fun deleteTestDatabases() {
        createdDatabases.forEach(context::deleteDatabase)
        createdDatabases.clear()
    }

    @Test
    fun validSr25519ChainSecretSurvivesTwoLaunches() {
        assertValidChainSecret(CryptoType.SR25519, "chain-valid-sr25519", 1)
    }

    @Test
    fun validEd25519ChainSecretSurvivesTwoLaunches() {
        assertValidChainSecret(CryptoType.ED25519, "chain-valid-ed25519", 2)
    }

    @Test
    fun validEcdsaChainSecretSurvivesTwoLaunches() {
        assertValidChainSecret(CryptoType.ECDSA, "chain-valid-ecdsa", 3)
    }

    @Test
    fun sr25519SignerProviderFailureRollsBackAndHealthyRetrySucceeds() {
        assertCryptographyFailureRollsBack(
            cryptoType = CryptoType.SR25519,
            databaseName = SR_PROVIDER_FAILURE_DATABASE,
            variant = 4
        )
    }

    @Test
    fun ed25519SignerProviderFailureRollsBackAndHealthyRetrySucceeds() {
        assertCryptographyFailureRollsBack(
            cryptoType = CryptoType.ED25519,
            databaseName = ED_PROVIDER_FAILURE_DATABASE,
            variant = 5
        )
    }

    @Test
    fun ecdsaDeriverProviderFailureRollsBackAndHealthyRetrySucceeds() {
        assertCryptographyFailureRollsBack(
            cryptoType = CryptoType.ECDSA,
            databaseName = ECDSA_PROVIDER_FAILURE_DATABASE,
            variant = 6
        )
    }

    @Test
    fun samePublicWrongPrivateSr25519IsQuarantinedExactly() {
        assertWrongPrivateIsQuarantined(
            CryptoType.SR25519,
            "chain-wrong-private-sr25519",
            11
        )
    }

    @Test
    fun samePublicWrongPrivateEd25519IsQuarantinedExactly() {
        assertWrongPrivateIsQuarantined(
            CryptoType.ED25519,
            "chain-wrong-private-ed25519",
            12
        )
    }

    @Test
    fun samePublicWrongPrivateEcdsaIsQuarantinedExactly() {
        assertWrongPrivateIsQuarantined(
            CryptoType.ECDSA,
            "chain-wrong-private-ecdsa",
            13
        )
    }

    @Test
    fun publicKeyMismatchIsQuarantinedExactly() {
        val expected = recoveryFixture(CryptoType.ED25519, 21)
        val other = recoveryFixture(CryptoType.ED25519, 22)
        createVersion76Database(
            databaseName = PUBLIC_MISMATCH_DATABASE,
            publicKey = expected.keypair.publicKey,
            accountId = expected.keypair.publicKey.substrateAccountId(),
            cryptoType = CryptoType.ED25519
        )
        val activeKey =
            chainSecretKey(expected.keypair.publicKey.substrateAccountId())
        val backing = SecretBacking().apply {
            values[activeKey] = other.encoded
        }

        assertEquals(
            77,
            openProductionDatabase(
                PUBLIC_MISMATCH_DATABASE,
                TestPreferences(backing)
            )
        )

        assertFalse(activeKey in backing.values)
        assertEquals(
            other.encoded,
            backing.values[WalletSecretQuarantine.keyFor(activeKey)]
        )
    }

    @Test
    fun accountIdMismatchMarksIdentityRecoveryAndPreservesCiphertext() {
        val fixture = recoveryFixture(CryptoType.ECDSA, 23)
        val mismatchedAccountId = ByteArray(32) { (it * 3 + 1).toByte() }
        createVersion76Database(
            databaseName = ACCOUNT_ID_MISMATCH_DATABASE,
            publicKey = fixture.keypair.publicKey,
            accountId = mismatchedAccountId,
            cryptoType = CryptoType.ECDSA
        )
        val activeKey = chainSecretKey(mismatchedAccountId)
        val backing = SecretBacking().apply {
            values[activeKey] = fixture.encoded
        }

        assertEquals(
            77,
            openProductionDatabase(
                ACCOUNT_ID_MISMATCH_DATABASE,
                TestPreferences(backing)
            )
        )

        assertEquals(fixture.encoded, backing.values[activeKey])
        assertFalse(WalletSecretQuarantine.keyFor(activeKey) in backing.values)
        assertEquals(
            WalletPublicIdentityRecovery.MARKER_VALUE,
            backing.values[publicIdentityMarker(activeKey)]
        )
    }

    @Test
    fun malformedCiphertextMovesExactlyOnceAndRelaunchIsIdempotent() {
        val fixture = recoveryFixture(CryptoType.ECDSA, 24)
        val accountId = fixture.keypair.publicKey.substrateAccountId()
        createVersion76Database(
            databaseName = MALFORMED_DATABASE,
            publicKey = fixture.keypair.publicKey,
            accountId = accountId,
            cryptoType = CryptoType.ECDSA
        )
        val activeKey = chainSecretKey(accountId)
        val quarantineKey = WalletSecretQuarantine.keyFor(activeKey)
        val malformed = "not-scale-\u0000-\uD83D\uDD10"
        val backing = SecretBacking().apply {
            values[activeKey] = malformed
        }

        assertEquals(
            77,
            openProductionDatabase(MALFORMED_DATABASE, TestPreferences(backing))
        )
        assertFalse(activeKey in backing.values)
        assertEquals(malformed, backing.values[quarantineKey])
        val afterFirstLaunch = backing.values.toMap()

        assertEquals(
            77,
            openProductionDatabase(MALFORMED_DATABASE, TestPreferences(backing))
        )
        assertEquals(afterFirstLaunch, backing.values)
    }

    @Test
    fun genericProviderReadFailureRollsBackAndHealthyRetryPreservesSecret() {
        val fixture = recoveryFixture(CryptoType.ECDSA, 25)
        val accountId = fixture.keypair.publicKey.substrateAccountId()
        createVersion76Database(
            databaseName = GENERIC_READ_FAILURE_DATABASE,
            publicKey = fixture.keypair.publicKey,
            accountId = accountId,
            cryptoType = CryptoType.ECDSA
        )
        val activeKey = chainSecretKey(accountId)
        val backing = SecretBacking().apply {
            values[activeKey] = fixture.encoded
        }
        val failingPreferences = TestPreferences(backing).apply {
            genericReadFailureKey = activeKey
        }

        assertThrows(RuntimeException::class.java) {
            openProductionDatabase(
                GENERIC_READ_FAILURE_DATABASE,
                failingPreferences
            )
        }

        assertEquals(76, rawDatabaseVersion(GENERIC_READ_FAILURE_DATABASE))
        assertEquals(fixture.encoded, backing.values[activeKey])
        assertFalse(WalletSecretQuarantine.keyFor(activeKey) in backing.values)
        assertEquals(
            77,
            openProductionDatabase(
                GENERIC_READ_FAILURE_DATABASE,
                TestPreferences(backing)
            )
        )
        assertEquals(fixture.encoded, backing.values[activeKey])
    }

    @Test
    fun secureStorageReadFailureRollsBackWithoutQuarantine() {
        val fixture = recoveryFixture(CryptoType.ECDSA, 26)
        val accountId = fixture.keypair.publicKey.substrateAccountId()
        createVersion76Database(
            databaseName = SECURE_READ_FAILURE_DATABASE,
            publicKey = fixture.keypair.publicKey,
            accountId = accountId,
            cryptoType = CryptoType.ECDSA
        )
        val activeKey = chainSecretKey(accountId)
        val backing = SecretBacking().apply {
            values[activeKey] = fixture.encoded
        }
        val failingPreferences = TestPreferences(backing).apply {
            secureReadFailureKey = activeKey
        }

        assertThrows(WalletSecureStorageUnavailableException::class.java) {
            openProductionDatabase(
                SECURE_READ_FAILURE_DATABASE,
                failingPreferences
            )
        }

        assertEquals(76, rawDatabaseVersion(SECURE_READ_FAILURE_DATABASE))
        assertEquals(fixture.encoded, backing.values[activeKey])
        assertFalse(WalletSecretQuarantine.keyFor(activeKey) in backing.values)
    }

    @Test
    fun committedQuarantineWithSqlRollbackRetriesIdempotently() {
        val fixture = recoveryFixture(CryptoType.ECDSA, 27)
        val accountId = fixture.keypair.publicKey.substrateAccountId()
        createVersion76Database(
            databaseName = QUARANTINE_RETRY_DATABASE,
            publicKey = fixture.keypair.publicKey,
            accountId = accountId,
            cryptoType = CryptoType.ECDSA
        )
        val activeKey = chainSecretKey(accountId)
        val quarantineKey = WalletSecretQuarantine.keyFor(activeKey)
        val malformed = "invalid-chain-secret"
        val backing = SecretBacking().apply {
            values[activeKey] = malformed
        }
        val failedPreferences = TestPreferences(backing).apply {
            failAfterQuarantineKey = activeKey
        }

        assertThrows(RuntimeException::class.java) {
            openProductionDatabase(
                QUARANTINE_RETRY_DATABASE,
                failedPreferences
            )
        }

        assertEquals(76, rawDatabaseVersion(QUARANTINE_RETRY_DATABASE))
        assertFalse(activeKey in backing.values)
        assertEquals(malformed, backing.values[quarantineKey])
        val committedQuarantine = backing.values.toMap()

        assertEquals(
            77,
            openProductionDatabase(
                QUARANTINE_RETRY_DATABASE,
                TestPreferences(backing)
            )
        )
        assertEquals(committedQuarantine, backing.values)
    }

    @Test
    fun keypairOnlyChainSecretKeepsSigningKeyAndSanitizesUnboundPath() {
        val keypair = directKeypair(CryptoType.ECDSA, 31)
        val encoded = ChainAccountSecrets(
            keyPair = keypair,
            derivationPath = "//unbound"
        ).toHexString()
        val accountId = keypair.publicKey.substrateAccountId()
        createVersion76Database(
            databaseName = SANITIZE_DATABASE,
            publicKey = keypair.publicKey,
            accountId = accountId,
            cryptoType = CryptoType.ECDSA
        )
        val activeKey = chainSecretKey(accountId)
        val backing = SecretBacking().apply {
            values[activeKey] = encoded
        }

        assertEquals(
            77,
            openProductionDatabase(SANITIZE_DATABASE, TestPreferences(backing))
        )

        val sanitized =
            ChainAccountSecrets.read(backing.values.getValue(activeKey))
        assertNull(sanitized[ChainAccountSecrets.DerivationPath])
        assertArrayEquals(
            keypair.privateKey,
            sanitized[ChainAccountSecrets.Keypair][KeyPairSchema.PrivateKey]
        )
        assertFalse(WalletSecretQuarantine.keyFor(activeKey) in backing.values)
    }

    @Test
    fun absentChainSecretDoesNotCreateQuarantine() {
        val fixture = recoveryFixture(CryptoType.ECDSA, 32)
        val accountId = fixture.keypair.publicKey.substrateAccountId()
        createVersion76Database(
            databaseName = ABSENT_SECRET_DATABASE,
            publicKey = fixture.keypair.publicKey,
            accountId = accountId,
            cryptoType = CryptoType.ECDSA
        )
        val activeKey = chainSecretKey(accountId)
        val backing = SecretBacking()

        assertEquals(
            77,
            openProductionDatabase(
                ABSENT_SECRET_DATABASE,
                TestPreferences(backing)
            )
        )

        assertFalse(activeKey in backing.values)
        assertFalse(WalletSecretQuarantine.keyFor(activeKey) in backing.values)
    }

    @Test
    fun staleMalformedChainRowsWithoutSecretsOpenWithoutRecoveryMarkers() {
        val fixture = recoveryFixture(CryptoType.ECDSA, 132)
        val accountId = fixture.keypair.publicKey.substrateAccountId()

        createVersion76Database(
            databaseName = ABSENT_UNKNOWN_CRYPTO_DATABASE,
            publicKey = fixture.keypair.publicKey,
            accountId = accountId,
            cryptoType = CryptoType.ECDSA
        )
        updateChainCryptoType(
            ABSENT_UNKNOWN_CRYPTO_DATABASE,
            "FUTURE_CRYPTO"
        )
        val unknownBacking = SecretBacking()
        assertEquals(
            77,
            openProductionDatabase(
                ABSENT_UNKNOWN_CRYPTO_DATABASE,
                TestPreferences(unknownBacking)
            )
        )
        assertTrue(unknownBacking.values.isEmpty())

        createVersion76Database(
            databaseName = ABSENT_NULL_CRYPTO_DATABASE,
            publicKey = fixture.keypair.publicKey,
            accountId = accountId,
            cryptoType = CryptoType.ECDSA
        )
        makeChainCryptoTypeNullableAndNull(ABSENT_NULL_CRYPTO_DATABASE)
        val nullBacking = SecretBacking()
        assertEquals(
            77,
            openProductionDatabase(
                ABSENT_NULL_CRYPTO_DATABASE,
                TestPreferences(nullBacking)
            )
        )
        assertTrue(nullBacking.values.isEmpty())

        val emptyAccountId = ByteArray(0)
        createVersion76Database(
            databaseName = ABSENT_EMPTY_ACCOUNT_ID_DATABASE,
            publicKey = fixture.keypair.publicKey,
            accountId = emptyAccountId,
            cryptoType = CryptoType.ECDSA
        )
        val emptyBacking = SecretBacking()
        assertEquals(
            77,
            openProductionDatabase(
                ABSENT_EMPTY_ACCOUNT_ID_DATABASE,
                TestPreferences(emptyBacking)
            )
        )
        assertTrue(emptyBacking.values.isEmpty())
        assertFalse(
            publicIdentityMarker(chainSecretKey(emptyAccountId)) in
                emptyBacking.values
        )
    }

    @Test
    fun unknownCryptoTypeWithActiveSecretMarksRecoveryWithoutQuarantine() {
        val fixture = recoveryFixture(CryptoType.ECDSA, 33)
        val accountId = fixture.keypair.publicKey.substrateAccountId()
        createVersion76Database(
            databaseName = UNKNOWN_CRYPTO_DATABASE,
            publicKey = fixture.keypair.publicKey,
            accountId = accountId,
            cryptoType = CryptoType.ECDSA
        )
        updateChainCryptoType(UNKNOWN_CRYPTO_DATABASE, "FUTURE_CRYPTO")
        val activeKey = chainSecretKey(accountId)
        val backing = SecretBacking().apply {
            values[activeKey] = fixture.encoded
        }

        assertEquals(
            77,
            openProductionDatabase(
                UNKNOWN_CRYPTO_DATABASE,
                TestPreferences(backing)
            )
        )

        assertEquals(fixture.encoded, backing.values[activeKey])
        assertFalse(WalletSecretQuarantine.keyFor(activeKey) in backing.values)
        assertEquals(
            WalletPublicIdentityRecovery.MARKER_VALUE,
            backing.values[publicIdentityMarker(activeKey)]
        )
    }

    @Test
    fun nullCryptoTypeWithActiveSecretMarksRecoveryWithoutQuarantine() {
        val fixture = recoveryFixture(CryptoType.ECDSA, 34)
        val accountId = fixture.keypair.publicKey.substrateAccountId()
        createVersion76Database(
            databaseName = NULL_CRYPTO_DATABASE,
            publicKey = fixture.keypair.publicKey,
            accountId = accountId,
            cryptoType = CryptoType.ECDSA
        )
        makeChainCryptoTypeNullableAndNull(NULL_CRYPTO_DATABASE)
        val activeKey = chainSecretKey(accountId)
        val backing = SecretBacking().apply {
            values[activeKey] = fixture.encoded
        }

        assertEquals(
            77,
            openProductionDatabase(
                NULL_CRYPTO_DATABASE,
                TestPreferences(backing)
            )
        )

        assertEquals(fixture.encoded, backing.values[activeKey])
        assertFalse(WalletSecretQuarantine.keyFor(activeKey) in backing.values)
        assertEquals(
            WalletPublicIdentityRecovery.MARKER_VALUE,
            backing.values[publicIdentityMarker(activeKey)]
        )
    }

    @Test
    fun duplicateRowsSharingOneValidSecretRemainIdempotent() {
        val fixture = recoveryFixture(CryptoType.ECDSA, 35)
        val accountId = fixture.keypair.publicKey.substrateAccountId()
        createVersion76Database(
            databaseName = DUPLICATE_VALID_DATABASE,
            publicKey = fixture.keypair.publicKey,
            accountId = accountId,
            cryptoType = CryptoType.ECDSA
        )
        insertDuplicateChainAccount(
            databaseName = DUPLICATE_VALID_DATABASE,
            publicKey = fixture.keypair.publicKey,
            accountId = accountId,
            cryptoType = CryptoType.ECDSA
        )
        val activeKey = chainSecretKey(accountId)
        val backing = SecretBacking().apply {
            values[activeKey] = fixture.encoded
        }

        assertEquals(
            77,
            openProductionDatabase(
                DUPLICATE_VALID_DATABASE,
                TestPreferences(backing)
            )
        )
        assertEquals(fixture.encoded, backing.values[activeKey])
        assertFalse(WalletSecretQuarantine.keyFor(activeKey) in backing.values)
    }

    @Test
    fun conflictingDuplicateRowsMarkIdentityRecoveryAndPreserveSharedSecret() {
        val fixture = recoveryFixture(CryptoType.ECDSA, 36)
        val conflicting = directKeypair(CryptoType.ECDSA, 37)
        val accountId = fixture.keypair.publicKey.substrateAccountId()
        createVersion76Database(
            databaseName = DUPLICATE_CONFLICT_DATABASE,
            publicKey = fixture.keypair.publicKey,
            accountId = accountId,
            cryptoType = CryptoType.ECDSA
        )
        insertDuplicateChainAccount(
            databaseName = DUPLICATE_CONFLICT_DATABASE,
            publicKey = conflicting.publicKey,
            accountId = accountId,
            cryptoType = CryptoType.ECDSA
        )
        val activeKey = chainSecretKey(accountId)
        val backing = SecretBacking().apply {
            values[activeKey] = fixture.encoded
        }

        assertEquals(
            77,
            openProductionDatabase(
                DUPLICATE_CONFLICT_DATABASE,
                TestPreferences(backing)
            )
        )

        assertEquals(fixture.encoded, backing.values[activeKey])
        assertFalse(WalletSecretQuarantine.keyFor(activeKey) in backing.values)
        assertEquals(
            WalletPublicIdentityRecovery.MARKER_VALUE,
            backing.values[publicIdentityMarker(activeKey)]
        )
    }

    @Test
    fun oversizedPrivateKeyIsQuarantinedExactly() {
        val expected = directKeypair(CryptoType.ECDSA, 38)
        val encoded = ChainAccountSecrets(
            keyPair = Keypair(
                publicKey = expected.publicKey,
                privateKey = ByteArray(33) { (it + 1).toByte() }
            )
        ).toHexString()

        assertEncodedSecretIsQuarantined(
            databaseName = OVERSIZED_PRIVATE_KEY_DATABASE,
            publicKey = expected.publicKey,
            accountId = expected.publicKey.substrateAccountId(),
            cryptoType = CryptoType.ECDSA,
            encoded = encoded
        )
    }

    @Test
    fun undersizedPrivateKeyIsQuarantinedExactly() {
        val expected = directKeypair(CryptoType.ECDSA, 42)
        val encoded = ChainAccountSecrets(
            keyPair = Keypair(
                publicKey = expected.publicKey,
                privateKey = ByteArray(31) { (it + 1).toByte() }
            )
        ).toHexString()

        assertEncodedSecretIsQuarantined(
            databaseName = UNDERSIZED_PRIVATE_KEY_DATABASE,
            publicKey = expected.publicKey,
            accountId = expected.publicKey.substrateAccountId(),
            cryptoType = CryptoType.ECDSA,
            encoded = encoded
        )
    }

    @Test
    fun oversizedPublicKeyIsQuarantinedExactly() {
        val original = directKeypair(CryptoType.ECDSA, 43)
        val oversizedPublicKey = original.publicKey + byteArrayOf(0x01)
        val encoded = ChainAccountSecrets(
            keyPair = Keypair(
                publicKey = oversizedPublicKey,
                privateKey = original.privateKey
            )
        ).toHexString()

        assertEncodedSecretIsQuarantined(
            databaseName = OVERSIZED_PUBLIC_KEY_DATABASE,
            publicKey = original.publicKey,
            accountId = original.publicKey.substrateAccountId(),
            cryptoType = CryptoType.ECDSA,
            encoded = encoded
        )
    }

    @Test
    fun attackerSizedDatabaseAccountIdFailsBeforeBlobOrPreferenceAccess() {
        val fixture = recoveryFixture(CryptoType.ECDSA, 46)
        val accountId = fixture.keypair.publicKey.substrateAccountId()
        createVersion76Database(
            databaseName = OVERSIZED_DATABASE_ACCOUNT_ID_DATABASE,
            publicKey = fixture.keypair.publicKey,
            accountId = accountId,
            cryptoType = CryptoType.ECDSA
        )
        replaceChainAccountIdWithZeroBlob(
            databaseName = OVERSIZED_DATABASE_ACCOUNT_ID_DATABASE,
            byteCount = ATTACKER_DATABASE_BLOB_BYTES
        )
        val backing = SecretBacking().apply {
            values["unrelated"] = "preserve"
        }
        val preferences = TestPreferences(backing)

        val failure = assertThrows(RuntimeException::class.java) {
            openProductionDatabase(
                OVERSIZED_DATABASE_ACCOUNT_ID_DATABASE,
                preferences
            )
        }

        assertTrue(
            generateSequence<Throwable>(failure) { it.cause }.any {
                it is WalletPublicIdentityIntegrityException
            }
        )
        assertEquals(76, rawDatabaseVersion(OVERSIZED_DATABASE_ACCOUNT_ID_DATABASE))
        assertTrue(preferences.inspectedKeys.isEmpty())
        assertEquals(0, preferences.mutationCount)
        assertEquals(mapOf("unrelated" to "preserve"), backing.values)
    }

    @Test
    fun textDatabaseAccountIdFailsBeforePreferenceAccess() {
        val fixture = recoveryFixture(CryptoType.ECDSA, 48)
        val accountId = fixture.keypair.publicKey.substrateAccountId()
        createVersion76Database(
            databaseName = TEXT_DATABASE_ACCOUNT_ID_DATABASE,
            publicKey = fixture.keypair.publicKey,
            accountId = accountId,
            cryptoType = CryptoType.ECDSA
        )
        openRawDatabase(TEXT_DATABASE_ACCOUNT_ID_DATABASE).use { database ->
            database.execSQL(
                "UPDATE chain_accounts SET accountId = ?",
                arrayOf("\u00E9".repeat(32))
            )
        }
        val backing = SecretBacking().apply {
            values["unrelated"] = "preserve"
        }
        val preferences = TestPreferences(backing)

        val failure = assertThrows(RuntimeException::class.java) {
            openProductionDatabase(
                TEXT_DATABASE_ACCOUNT_ID_DATABASE,
                preferences
            )
        }

        assertTrue(
            generateSequence<Throwable>(failure) { it.cause }.any {
                it is WalletPublicIdentityIntegrityException
            }
        )
        assertEquals(76, rawDatabaseVersion(TEXT_DATABASE_ACCOUNT_ID_DATABASE))
        assertTrue(preferences.inspectedKeys.isEmpty())
        assertEquals(0, preferences.mutationCount)
        assertEquals(mapOf("unrelated" to "preserve"), backing.values)
    }

    @Test
    fun nonPositiveChainWalletIdFailsBeforePreferenceAccess() {
        val fixture = recoveryFixture(CryptoType.ECDSA, 50)
        val accountId = fixture.keypair.publicKey.substrateAccountId()
        createVersion76Database(
            databaseName = INVALID_CHAIN_META_ID_DATABASE,
            publicKey = fixture.keypair.publicKey,
            accountId = accountId,
            cryptoType = CryptoType.ECDSA
        )
        openRawDatabase(INVALID_CHAIN_META_ID_DATABASE).use { database ->
            database.execSQL("PRAGMA foreign_keys = OFF")
            database.execSQL(
                "UPDATE chain_accounts SET metaId = -1"
            )
        }
        val backing = SecretBacking().apply {
            values["unrelated"] = "preserve"
        }
        val preferences = TestPreferences(backing)

        val failure = assertThrows(RuntimeException::class.java) {
            openProductionDatabase(
                INVALID_CHAIN_META_ID_DATABASE,
                preferences
            )
        }

        assertTrue(
            generateSequence<Throwable>(failure) { it.cause }.any {
                it is WalletPublicIdentityIntegrityException
            }
        )
        assertEquals(76, rawDatabaseVersion(INVALID_CHAIN_META_ID_DATABASE))
        assertTrue(preferences.inspectedKeys.isEmpty())
        assertEquals(0, preferences.mutationCount)
        assertEquals(mapOf("unrelated" to "preserve"), backing.values)
    }

    @Test
    fun attackerSizedDatabasePublicKeyMarksRecoveryAndPreservesSecret() {
        val fixture = recoveryFixture(CryptoType.ECDSA, 47)
        val accountId = fixture.keypair.publicKey.substrateAccountId()
        createVersion76Database(
            databaseName = OVERSIZED_DATABASE_PUBLIC_KEY_DATABASE,
            publicKey = ByteArray(65_536) { (it + 3).toByte() },
            accountId = accountId,
            cryptoType = CryptoType.ECDSA
        )
        val activeKey = chainSecretKey(accountId)
        val backing = SecretBacking().apply {
            values[activeKey] = fixture.encoded
        }

        assertEquals(
            77,
            openProductionDatabase(
                OVERSIZED_DATABASE_PUBLIC_KEY_DATABASE,
                TestPreferences(backing)
            )
        )

        assertEquals(fixture.encoded, backing.values[activeKey])
        assertFalse(WalletSecretQuarantine.keyFor(activeKey) in backing.values)
        assertEquals(
            WalletPublicIdentityRecovery.MARKER_VALUE,
            backing.values[publicIdentityMarker(activeKey)]
        )
    }

    @Test
    fun textPublicKeyAndMalformedPayloadMarkRecoveryWithoutQuarantine() {
        val fixture = recoveryFixture(CryptoType.ECDSA, 49)
        val accountId = fixture.keypair.publicKey.substrateAccountId()
        createVersion76Database(
            databaseName = TEXT_DATABASE_PUBLIC_KEY_DATABASE,
            publicKey = fixture.keypair.publicKey,
            accountId = accountId,
            cryptoType = CryptoType.ECDSA
        )
        openRawDatabase(TEXT_DATABASE_PUBLIC_KEY_DATABASE).use { database ->
            database.execSQL(
                "UPDATE chain_accounts SET publicKey = ?",
                arrayOf("\u00E9".repeat(33))
            )
        }
        val activeKey = chainSecretKey(accountId)
        val malformed = "malformed-chain-before-identity-\u0000"
        val backing = SecretBacking().apply {
            values[activeKey] = malformed
        }

        assertEquals(
            77,
            openProductionDatabase(
                TEXT_DATABASE_PUBLIC_KEY_DATABASE,
                TestPreferences(backing)
            )
        )

        assertEquals(malformed, backing.values[activeKey])
        assertFalse(WalletSecretQuarantine.keyFor(activeKey) in backing.values)
        assertEquals(
            WalletPublicIdentityRecovery.MARKER_VALUE,
            backing.values[publicIdentityMarker(activeKey)]
        )
    }

    @Test
    fun malformedSr25519NonceIsQuarantinedExactly() {
        val expected = directKeypair(CryptoType.SR25519, 44)
        val encoded = ChainAccountSecrets(
            keyPair = Keypair(
                publicKey = expected.publicKey,
                privateKey = expected.privateKey,
                nonce = ByteArray(31) { (it + 3).toByte() }
            )
        ).toHexString()

        assertEncodedSecretIsQuarantined(
            databaseName = MALFORMED_SR_NONCE_DATABASE,
            publicKey = expected.publicKey,
            accountId = expected.publicKey.substrateAccountId(),
            cryptoType = CryptoType.SR25519,
            encoded = encoded
        )
    }

    @Test
    fun unexpectedEd25519NonceIsQuarantinedExactly() {
        val expected = directKeypair(CryptoType.ED25519, 45)
        val encoded = ChainAccountSecrets(
            keyPair = Keypair(
                publicKey = expected.publicKey,
                privateKey = expected.privateKey,
                nonce = ByteArray(32) { (it + 5).toByte() }
            )
        ).toHexString()

        assertEncodedSecretIsQuarantined(
            databaseName = UNEXPECTED_ED_NONCE_DATABASE,
            publicKey = expected.publicKey,
            accountId = expected.publicKey.substrateAccountId(),
            cryptoType = CryptoType.ED25519,
            encoded = encoded
        )
    }

    @Test
    fun payloadBeyondDecodeBoundIsQuarantinedExactly() {
        val expected = directKeypair(CryptoType.ECDSA, 39)
        val encoded = "0".repeat(MAX_PLAINTEXT_CHARS + 1)

        assertEncodedSecretIsQuarantined(
            databaseName = OVERSIZED_PAYLOAD_DATABASE,
            publicKey = expected.publicKey,
            accountId = expected.publicKey.substrateAccountId(),
            cryptoType = CryptoType.ECDSA,
            encoded = encoded
        )
    }

    @Test
    fun malformedRecoveryMaterialIsQuarantinedExactly() {
        val expected = directKeypair(CryptoType.ECDSA, 40)
        val encoded = ChainAccountSecrets(
            keyPair = expected,
            entropy = ByteArray(17) { (it + 1).toByte() },
            seed = ByteArray(31) { (it + 2).toByte() },
            derivationPath = CHAIN_PATH
        ).toHexString()

        assertEncodedSecretIsQuarantined(
            databaseName = MALFORMED_RECOVERY_DATABASE,
            publicKey = expected.publicKey,
            accountId = expected.publicKey.substrateAccountId(),
            cryptoType = CryptoType.ECDSA,
            encoded = encoded
        )
    }

    @Test
    fun oversizedRecoveryPathIsQuarantinedExactly() {
        val fixture = recoveryFixture(CryptoType.ECDSA, 41)
        val decoded = ChainAccountSecrets.read(fixture.encoded)
        val encoded = ChainAccountSecrets(
            keyPair = fixture.keypair,
            entropy = decoded[ChainAccountSecrets.Entropy],
            seed = decoded[ChainAccountSecrets.Seed],
            derivationPath = "/".repeat(MAX_DERIVATION_PATH_CHARS + 1)
        ).toHexString()

        assertEncodedSecretIsQuarantined(
            databaseName = OVERSIZED_PATH_DATABASE,
            publicKey = fixture.keypair.publicKey,
            accountId = fixture.keypair.publicKey.substrateAccountId(),
            cryptoType = CryptoType.ECDSA,
            encoded = encoded
        )
    }

    private fun assertValidChainSecret(
        cryptoType: CryptoType,
        databaseName: String,
        variant: Int
    ) {
        val fixture = recoveryFixture(cryptoType, variant)
        val accountId = fixture.keypair.publicKey.substrateAccountId()
        createVersion76Database(
            databaseName = databaseName,
            publicKey = fixture.keypair.publicKey,
            accountId = accountId,
            cryptoType = cryptoType
        )
        val activeKey = chainSecretKey(accountId)
        val backing = SecretBacking().apply {
            values[activeKey] = fixture.encoded
        }

        assertEquals(
            77,
            openProductionDatabase(databaseName, TestPreferences(backing))
        )
        val afterFirstLaunch = backing.values.toMap()
        assertEquals(
            77,
            openProductionDatabase(databaseName, TestPreferences(backing))
        )

        assertEquals(afterFirstLaunch, backing.values)
        assertEquals(fixture.encoded, backing.values[activeKey])
        assertFalse(WalletSecretQuarantine.keyFor(activeKey) in backing.values)
    }

    private fun assertCryptographyFailureRollsBack(
        cryptoType: CryptoType,
        databaseName: String,
        variant: Int
    ) {
        val fixture = recoveryFixture(cryptoType, variant)
        val accountId = fixture.keypair.publicKey.substrateAccountId()
        createVersion76Database(
            databaseName = databaseName,
            publicKey = fixture.keypair.publicKey,
            accountId = accountId,
            cryptoType = cryptoType
        )
        val activeKey = chainSecretKey(accountId)
        val backing = SecretBacking().apply {
            values[activeKey] = fixture.encoded
        }
        val providerFailure = WalletSecureStorageUnavailableException(
            "Injected $cryptoType cryptography provider failure"
        )
        val failingValidation = ChainAccountSecretValidation {
                _, _, _, actualCryptoType ->
            assertEquals(cryptoType, actualCryptoType)
            throw providerFailure
        }

        val thrown = assertThrows(
            WalletSecureStorageUnavailableException::class.java
        ) {
            openProductionDatabase(
                databaseName = databaseName,
                preferences = TestPreferences(backing),
                chainAccountSecretValidation = failingValidation
            )
        }

        assertSame(providerFailure, thrown)
        assertEquals(76, rawDatabaseVersion(databaseName))
        assertEquals(fixture.encoded, backing.values[activeKey])
        assertFalse(WalletSecretQuarantine.keyFor(activeKey) in backing.values)

        assertEquals(
            77,
            openProductionDatabase(
                databaseName = databaseName,
                preferences = TestPreferences(backing)
            )
        )
        assertEquals(fixture.encoded, backing.values[activeKey])
        assertFalse(WalletSecretQuarantine.keyFor(activeKey) in backing.values)
    }

    private fun assertWrongPrivateIsQuarantined(
        cryptoType: CryptoType,
        databaseName: String,
        variant: Int
    ) {
        val expected = directKeypair(cryptoType, variant)
        val other = directKeypair(cryptoType, variant + 1)
        val encoded = ChainAccountSecrets { secrets ->
            secrets[Entropy] = null
            secrets[Seed] = null
            secrets[Keypair] = KeyPairSchema { keypair ->
                keypair[PublicKey] = expected.publicKey
                keypair[PrivateKey] = other.privateKey
                keypair[Nonce] = (expected as? Sr25519Keypair)?.nonce
            }
            secrets[DerivationPath] = null
        }.toHexString()
        val accountId = expected.publicKey.substrateAccountId()
        createVersion76Database(
            databaseName = databaseName,
            publicKey = expected.publicKey,
            accountId = accountId,
            cryptoType = cryptoType
        )
        val activeKey = chainSecretKey(accountId)
        val backing = SecretBacking().apply {
            values[activeKey] = encoded
        }

        assertEquals(
            77,
            openProductionDatabase(databaseName, TestPreferences(backing))
        )

        assertFalse(activeKey in backing.values)
        assertEquals(
            encoded,
            backing.values[WalletSecretQuarantine.keyFor(activeKey)]
        )
    }

    private fun assertEncodedSecretIsQuarantined(
        databaseName: String,
        publicKey: ByteArray,
        accountId: ByteArray,
        cryptoType: CryptoType,
        encoded: String
    ) {
        createVersion76Database(
            databaseName = databaseName,
            publicKey = publicKey,
            accountId = accountId,
            cryptoType = cryptoType
        )
        val activeKey = chainSecretKey(accountId)
        val backing = SecretBacking().apply {
            values[activeKey] = encoded
        }

        assertEquals(
            77,
            openProductionDatabase(databaseName, TestPreferences(backing))
        )
        assertFalse(activeKey in backing.values)
        assertEquals(
            encoded,
            backing.values[WalletSecretQuarantine.keyFor(activeKey)]
        )
    }

    private fun createVersion76Database(
        databaseName: String,
        publicKey: ByteArray,
        accountId: ByteArray,
        cryptoType: CryptoType
    ) {
        createdDatabases += databaseName
        helper.createDatabase(databaseName, 76).apply {
            execSQL(
                """
                INSERT INTO meta_accounts(
                    id,
                    name,
                    isSelected,
                    position,
                    isBackedUp,
                    googleBackupAddress,
                    initialized
                ) VALUES(?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(META_ID, "Chain owner", 1, 0, 1, null, 1)
            )
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
                    ecosystem
                ) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any>(
                    CHAIN_ID,
                    "Chain fixture",
                    "fixture-icon",
                    42,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    "SUBSTRATE"
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
                    publicKey,
                    accountId,
                    cryptoType.name,
                    "Chain signing account",
                    1
                )
            )
            close()
        }
    }

    private fun updateChainCryptoType(
        databaseName: String,
        cryptoType: String
    ) {
        openRawDatabase(databaseName).use { database ->
            database.execSQL(
                "UPDATE chain_accounts SET cryptoType = ?",
                arrayOf(cryptoType)
            )
        }
    }

    private fun replaceChainAccountIdWithZeroBlob(
        databaseName: String,
        byteCount: Int
    ) {
        openRawDatabase(databaseName).use { database ->
            database.execSQL(
                "UPDATE chain_accounts SET accountId = zeroblob(?)",
                arrayOf(byteCount)
            )
        }
    }

    private fun makeChainCryptoTypeNullableAndNull(databaseName: String) {
        openRawDatabase(databaseName).use { database ->
            database.execSQL("PRAGMA foreign_keys = OFF")
            database.beginTransaction()
            try {
                database.execSQL(
                    "ALTER TABLE chain_accounts RENAME TO chain_accounts_original"
                )
                database.execSQL(
                    """
                    CREATE TABLE chain_accounts (
                        metaId INTEGER NOT NULL,
                        chainId TEXT NOT NULL,
                        publicKey BLOB NOT NULL,
                        accountId BLOB NOT NULL,
                        cryptoType TEXT,
                        name TEXT NOT NULL,
                        initialized INTEGER NOT NULL,
                        PRIMARY KEY(metaId, chainId),
                        FOREIGN KEY(chainId) REFERENCES chains(id)
                            ON UPDATE NO ACTION
                            ON DELETE NO ACTION
                            DEFERRABLE INITIALLY DEFERRED,
                        FOREIGN KEY(metaId) REFERENCES meta_accounts(id)
                            ON UPDATE NO ACTION
                            ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    INSERT INTO chain_accounts(
                        metaId,
                        chainId,
                        publicKey,
                        accountId,
                        cryptoType,
                        name,
                        initialized
                    )
                    SELECT
                        metaId,
                        chainId,
                        publicKey,
                        accountId,
                        NULL,
                        name,
                        initialized
                    FROM chain_accounts_original
                    """.trimIndent()
                )
                database.execSQL("DROP TABLE chain_accounts_original")
                database.execSQL(
                    "CREATE INDEX index_chain_accounts_chainId " +
                        "ON chain_accounts(chainId)"
                )
                database.execSQL(
                    "CREATE INDEX index_chain_accounts_metaId " +
                        "ON chain_accounts(metaId)"
                )
                database.execSQL(
                    "CREATE INDEX index_chain_accounts_accountId " +
                        "ON chain_accounts(accountId)"
                )
                database.setTransactionSuccessful()
            } finally {
                database.endTransaction()
            }

            val nullableCreateSql = readChainAccountsCreateSql(database)
            val nullableCryptoTypeDefinition = "cryptoType TEXT,"
            check(
                nullableCreateSql.indexOf(nullableCryptoTypeDefinition) >= 0 &&
                    nullableCreateSql.indexOf(nullableCryptoTypeDefinition) ==
                    nullableCreateSql.lastIndexOf(nullableCryptoTypeDefinition)
            ) {
                "Expected exactly one nullable cryptoType definition in chain_accounts"
            }
            val canonicalCreateSql = nullableCreateSql.replace(
                nullableCryptoTypeDefinition,
                "cryptoType TEXT NOT NULL,"
            )

            database.execSQL("PRAGMA writable_schema = ON")
            try {
                database.execSQL(
                    """
                    UPDATE sqlite_master
                    SET sql = ?
                    WHERE type = 'table'
                        AND name = 'chain_accounts'
                        AND sql = ?
                    """.trimIndent(),
                    arrayOf(canonicalCreateSql, nullableCreateSql)
                )
                check(readChainAccountsCreateSql(database) == canonicalCreateSql) {
                    "Failed to restore the canonical chain_accounts schema"
                }
            } finally {
                database.execSQL("PRAGMA writable_schema = OFF")
            }
        }
    }

    private fun readChainAccountsCreateSql(database: SQLiteDatabase): String {
        database.rawQuery(
            """
            SELECT sql
            FROM sqlite_master
            WHERE type = 'table' AND name = 'chain_accounts'
            """.trimIndent(),
            null
        ).use { cursor ->
            check(cursor.moveToFirst()) {
                "chain_accounts is missing from sqlite_master"
            }
            val createSql = cursor.getString(0)
            check(!cursor.moveToNext()) {
                "Expected exactly one chain_accounts row in sqlite_master"
            }
            return createSql
        }
    }

    private fun insertDuplicateChainAccount(
        databaseName: String,
        publicKey: ByteArray,
        accountId: ByteArray,
        cryptoType: CryptoType
    ) {
        openRawDatabase(databaseName).use { database ->
            database.execSQL(
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
                    ecosystem
                ) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any>(
                    DUPLICATE_CHAIN_ID,
                    "Duplicate chain fixture",
                    "duplicate-fixture-icon",
                    43,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    "SUBSTRATE"
                )
            )
            database.execSQL(
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
                    DUPLICATE_CHAIN_ID,
                    publicKey,
                    accountId,
                    cryptoType.name,
                    "Duplicate signing account",
                    1
                )
            )
        }
    }

    private fun openRawDatabase(databaseName: String): SQLiteDatabase {
        return SQLiteDatabase.openDatabase(
            context.getDatabasePath(databaseName).path,
            null,
            SQLiteDatabase.OPEN_READWRITE
        )
    }

    private fun openProductionDatabase(
        databaseName: String,
        preferences: EncryptedPreferences,
        chainAccountSecretValidation: ChainAccountSecretValidation =
            ChainAccountSecretValidator
    ): Int {
        var database: AppDatabase? = null
        try {
            database = AppDatabase.create(
                context = context,
                databaseName = databaseName,
                storeV1 = SecretStoreV1Impl(preferences),
                storeV2 = SecretStoreV2(preferences),
                encryptedPreferences = preferences,
                substrateSecretStore = SubstrateSecretStore(preferences),
                ethereumSecretStore = EthereumSecretStore(preferences),
                chainAccountSecretValidation = chainAccountSecretValidation
            )
            return database.openHelper.writableDatabase.version
        } finally {
            database?.close()
        }
    }

    private fun rawDatabaseVersion(databaseName: String): Int {
        return SQLiteDatabase.openDatabase(
            context.getDatabasePath(databaseName).path,
            null,
            SQLiteDatabase.OPEN_READONLY
        ).use(SQLiteDatabase::getVersion)
    }

    private fun recoveryFixture(
        cryptoType: CryptoType,
        variant: Int
    ): ChainFixture {
        val entropy = ByteArray(16) { index ->
            (index * 7 + variant + 1).toByte()
        }
        val mnemonic = MnemonicCreator.fromEntropy(entropy)
        val decodedPath = SubstrateJunctionDecoder.decode(CHAIN_PATH)
        val seed = SubstrateSeedFactory.deriveSeed32(
            mnemonicWords = mnemonic.words,
            password = decodedPath.password
        ).seed
        val keypair = SubstrateKeypairFactory.generate(
            encryptionType = cryptoType.toEncryptionType(),
            seed = seed,
            junctions = decodedPath.junctions
        )
        return ChainFixture(
            keypair = keypair,
            encoded = ChainAccountSecrets(
                keyPair = keypair,
                entropy = entropy,
                seed = seed,
                derivationPath = CHAIN_PATH
            ).toHexString()
        )
    }

    private fun directKeypair(
        cryptoType: CryptoType,
        variant: Int
    ): FearlessKeypair {
        return SubstrateKeypairFactory.generate(
            encryptionType = cryptoType.toEncryptionType(),
            seed = ByteArray(32) { index ->
                (index * 11 + variant + 1).toByte()
            },
            junctions = emptyList()
        )
    }

    private fun CryptoType.toEncryptionType(): EncryptionType = when (this) {
        CryptoType.SR25519 -> EncryptionType.SR25519
        CryptoType.ED25519 -> EncryptionType.ED25519
        CryptoType.ECDSA -> EncryptionType.ECDSA
    }

    private data class ChainFixture(
        val keypair: FearlessKeypair,
        val encoded: String
    )

    private class SecretBacking {
        val values = linkedMapOf<String, String>()
    }

    private class TestPreferences(
        private val backing: SecretBacking
    ) : EncryptedPreferences {

        val inspectedKeys = mutableListOf<String>()
        var mutationCount = 0
        var genericReadFailureKey: String? = null
        var secureReadFailureKey: String? = null
        var failAfterQuarantineKey: String? = null
        private var durableStorageHealthy = true

        override fun putEncryptedString(field: String, value: String) {
            replaceEncryptedStringsDurably(
                valuesToPut = mapOf(field to value),
                keysToRemove = emptySet()
            )
        }

        override fun getDecryptedString(field: String): String? {
            inspectedKeys += field
            if (field == secureReadFailureKey) {
                throw WalletSecureStorageUnavailableException(
                    "Injected secure-storage read failure"
                )
            }
            if (field == genericReadFailureKey) {
                throw RuntimeException("Injected provider read failure")
            }
            return backing.values[field]
        }

        override fun hasKey(field: String): Boolean {
            inspectedKeys += field
            return field in backing.values
        }

        override fun removeKey(field: String) {
            mutationCount += 1
            backing.values.remove(field)
        }

        override fun replaceEncryptedStringsDurably(
            valuesToPut: Map<String, String>,
            keysToRemove: Set<String>
        ) {
            mutationCount += 1
            requireDurableStorageHealthy()
            backing.values.putAll(valuesToPut)
            keysToRemove.forEach(backing.values::remove)
        }

        override fun quarantineEncryptedStringDurably(
            sourceKey: String,
            quarantineKey: String,
            expectedSnapshot: EncryptedPreferenceSnapshot
        ): Boolean {
            mutationCount += 1
            requireDurableStorageHealthy()
            val source = backing.values[sourceKey]
            if (source == null) {
                check(quarantineKey in backing.values)
                return expectedSnapshot.matchesUnencryptedStorageValue(
                    checkNotNull(backing.values[quarantineKey])
                )
            }
            if (!expectedSnapshot.matchesUnencryptedStorageValue(source)) {
                return false
            }
            val existing = backing.values[quarantineKey]
            check(existing == null || existing == source)
            backing.values[quarantineKey] = source
            backing.values.remove(sourceKey)
            if (sourceKey == failAfterQuarantineKey) {
                durableStorageHealthy = false
                throw RuntimeException("Injected failure after quarantine commit")
            }
            return true
        }

        override fun requireDurableStorageHealthy() {
            check(durableStorageHealthy) {
                "Injected durable storage health latch"
            }
        }
    }

    private companion object {
        const val META_ID = 77L
        const val CHAIN_ID = "chain-integrity-fixture"
        const val DUPLICATE_CHAIN_ID = "chain-integrity-fixture-duplicate"
        const val CHAIN_PATH = "//chain///password"
        const val MAX_PLAINTEXT_CHARS = 1_048_576
        const val MAX_DERIVATION_PATH_CHARS = 2_048
        const val ATTACKER_DATABASE_BLOB_BYTES = 67_108_864
        const val PUBLIC_MISMATCH_DATABASE = "chain-public-mismatch"
        const val SR_PROVIDER_FAILURE_DATABASE = "chain-sr-provider-failure"
        const val ED_PROVIDER_FAILURE_DATABASE = "chain-ed-provider-failure"
        const val ECDSA_PROVIDER_FAILURE_DATABASE = "chain-ecdsa-provider-failure"
        const val ACCOUNT_ID_MISMATCH_DATABASE = "chain-account-id-mismatch"
        const val MALFORMED_DATABASE = "chain-malformed"
        const val GENERIC_READ_FAILURE_DATABASE = "chain-generic-read-failure"
        const val SECURE_READ_FAILURE_DATABASE = "chain-secure-read-failure"
        const val QUARANTINE_RETRY_DATABASE = "chain-quarantine-retry"
        const val SANITIZE_DATABASE = "chain-sanitize"
        const val ABSENT_SECRET_DATABASE = "chain-absent-secret"
        const val ABSENT_UNKNOWN_CRYPTO_DATABASE =
            "chain-absent-unknown-crypto"
        const val ABSENT_NULL_CRYPTO_DATABASE =
            "chain-absent-null-crypto"
        const val ABSENT_EMPTY_ACCOUNT_ID_DATABASE =
            "chain-absent-empty-account-id"
        const val UNKNOWN_CRYPTO_DATABASE = "chain-unknown-crypto"
        const val NULL_CRYPTO_DATABASE = "chain-null-crypto"
        const val DUPLICATE_VALID_DATABASE = "chain-duplicate-valid"
        const val DUPLICATE_CONFLICT_DATABASE = "chain-duplicate-conflict"
        const val OVERSIZED_PRIVATE_KEY_DATABASE = "chain-oversized-private"
        const val UNDERSIZED_PRIVATE_KEY_DATABASE = "chain-undersized-private"
        const val OVERSIZED_PUBLIC_KEY_DATABASE = "chain-oversized-public"
        const val OVERSIZED_DATABASE_ACCOUNT_ID_DATABASE =
            "chain-oversized-db-account-id"
        const val OVERSIZED_DATABASE_PUBLIC_KEY_DATABASE =
            "chain-oversized-db-public-key"
        const val TEXT_DATABASE_ACCOUNT_ID_DATABASE =
            "chain-text-db-account-id"
        const val TEXT_DATABASE_PUBLIC_KEY_DATABASE =
            "chain-text-db-public-key"
        const val INVALID_CHAIN_META_ID_DATABASE =
            "chain-invalid-meta-id"
        const val MALFORMED_SR_NONCE_DATABASE = "chain-malformed-sr-nonce"
        const val UNEXPECTED_ED_NONCE_DATABASE = "chain-unexpected-ed-nonce"
        const val OVERSIZED_PAYLOAD_DATABASE = "chain-oversized-payload"
        const val MALFORMED_RECOVERY_DATABASE = "chain-malformed-recovery"
        const val OVERSIZED_PATH_DATABASE = "chain-oversized-path"

        fun chainSecretKey(accountId: ByteArray) =
            "$META_ID:${accountId.toPlainHexString()}:ACCESS_SECRETS"

        fun publicIdentityMarker(activeKey: String) =
            WalletPublicIdentityRecovery.keyFor(META_ID, activeKey)
    }
}
