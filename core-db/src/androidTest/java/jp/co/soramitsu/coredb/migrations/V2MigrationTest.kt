package jp.co.soramitsu.coredb.migrations

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.migration.Migration
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import jp.co.soramitsu.common.data.secrets.legacy.LegacyWalletV04Secrets
import jp.co.soramitsu.common.data.secrets.v1.SecretStoreV1
import jp.co.soramitsu.common.data.secrets.v1.SecretStoreV1Impl
import jp.co.soramitsu.common.data.secrets.v2.KeyPairSchema
import jp.co.soramitsu.common.data.secrets.v2.MetaAccountSecrets
import jp.co.soramitsu.common.data.secrets.v2.SecretStoreV2
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecretStore
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecrets
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecretStore
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecrets
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferenceSnapshot
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferenceSnapshotMove
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.common.data.storage.encrypt.WalletPublicIdentityIntegrityException
import jp.co.soramitsu.common.data.storage.encrypt.WalletPublicIdentityRecovery
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretConcurrentMutationException
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretQuarantine
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecureStorageUnavailableException
import jp.co.soramitsu.common.utils.DEFAULT_DERIVATION_PATH
import jp.co.soramitsu.common.utils.deriveSeed32
import jp.co.soramitsu.common.utils.ethereumAddressFromPublicKey
import jp.co.soramitsu.common.utils.invoke
import jp.co.soramitsu.common.utils.map
import jp.co.soramitsu.common.utils.substrateAccountId
import jp.co.soramitsu.core.crypto.mapCryptoTypeToEncryption
import jp.co.soramitsu.core.crypto.mapEncryptionToCryptoType
import jp.co.soramitsu.core.model.SecuritySource
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.coredb.AppDatabase
import jp.co.soramitsu.coredb.model.MetaAccountLocal
import jp.co.soramitsu.coredb.model.MetaAccountLocal.Table.Column
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
import jp.co.soramitsu.fearless_utils.scale.EncodableStruct
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAddress
import jp.co.soramitsu.testshared.HashMapEncryptedPreferences
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private const val MNEMONIC_WORDS = "bottom drive obey lake curtain smoke basket hold race lonely fit walk"
private val MNEMONIC = MnemonicCreator.fromWords(MNEMONIC_WORDS)

private const val DERIVATION_PATH = "//test"
private val DECODED_SUBSTRATE_DERIVATION_PATH =
    SubstrateJunctionDecoder.decode(DERIVATION_PATH)
private val SUBSTRATE_SEED = SubstrateSeedFactory.deriveSeed32(
    MNEMONIC_WORDS,
    password = DECODED_SUBSTRATE_DERIVATION_PATH.password
).seed
private val CRYPTO_TYPE = EncryptionType.SR25519
private val SUBSTRATE_KEYPAIR = SubstrateKeypairFactory.generate(
    CRYPTO_TYPE,
    seed = SUBSTRATE_SEED,
    junctions = DECODED_SUBSTRATE_DERIVATION_PATH.junctions
)

private val ETHEREUM_DERIVATION_PATH = BIP32JunctionDecoder.DEFAULT_DERIVATION_PATH
private val DECODED_ETHEREUM_DERIVATION_PATH = BIP32JunctionDecoder.decode(ETHEREUM_DERIVATION_PATH)
private val ETHEREUM_SEED = EthereumSeedFactory.deriveSeed32(
    mnemonicWords = MNEMONIC_WORDS,
    password = DECODED_ETHEREUM_DERIVATION_PATH.password
).seed
private val ETHEREUM_KEYPAIR = EthereumKeypairFactory.generate(
    seed = ETHEREUM_SEED,
    junctions = DECODED_ETHEREUM_DERIVATION_PATH.junctions
)

private const val NAME = "TEST"

class V2MigrationTest {

    private val TEST_DB = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        "jp.co.soramitsu.core_db.AppDatabase",
        FrameworkSQLiteOpenHelperFactory()
    )

    private val encryptedPreferences = HashMapEncryptedPreferences()
    private val storeV1 = SecretStoreV1Impl(encryptedPreferences)
    private val storeV2 = SecretStoreV2(encryptedPreferences)

    private val migration = V2Migration(storeV1, encryptedPreferences)

    @Test
    fun shouldMigrateWithMnemonic() = runBlocking {
        performSingleAccountTest(
            insertionType = Type.MNEMONIC,
            withEthereum = true,
            withEntropy = true,
            withSeed = true,
            withDerivationPath = true
        )
    }

    @Test
    fun shouldMigrateWithSeed() = runBlocking {
        performSingleAccountTest(
            insertionType = Type.SEED,
            withEthereum = false,
            withEntropy = false,
            withSeed = true,
            withDerivationPath = true
        )
    }

    @Test
    fun shouldMigrateWithCreate() = runBlocking {
        performSingleAccountTest(
            insertionType = Type.CREATE,
            withEthereum = true,
            withEntropy = true,
            withSeed = true,
            withDerivationPath = true
        )
    }

    @Test
    fun shouldMigrateWithJson() = runBlocking {
        performSingleAccountTest(
            insertionType = Type.JSON,
            withEthereum = false,
            withEntropy = false,
            withSeed = false,
            withDerivationPath = false
        )
    }

    @Test
    fun shouldMigrateWithKeypair() = runBlocking {
        performSingleAccountTest(
            insertionType = Type.KEYPAIR,
            withEthereum = false,
            withEntropy = false,
            withSeed = false,
            withDerivationPath = false
        )
    }

    @Test
    fun releasedV04SplitSecretsMigrateDirectlyAndRemainPreserved() = runBlocking {
        val address = SUBSTRATE_KEYPAIR.publicKey.toAddress(0)
        val exactOldValues = insertV04Secrets(
            preferences = encryptedPreferences,
            address = address,
            keypair = SUBSTRATE_KEYPAIR,
            seed = SUBSTRATE_SEED,
            entropy = MNEMONIC.entropy,
            derivationPath = DERIVATION_PATH
        )

        val database = performMigration {
            insertAccount(
                publicKey = SUBSTRATE_KEYPAIR.publicKey,
                encryptionType = CRYPTO_TYPE
            )
        }

        try {
            val metaAccount = database.getMetaAccounts().single()
            assertCorrectMetaAccount(
                metaAccountLocal = metaAccount,
                selected = true,
                withEthereum = true
            )
            assertCorrectSecrets(
                metaAccountSecrets =
                    storeV2.getMetaAccountSecrets(metaAccount.id),
                withEntropy = true,
                withSeed = true,
                withEthereum = true,
                withDerivationPath = true
            )
            exactOldValues.forEach { (key, value) ->
                assertEquals(
                    "Released preference must remain byte-for-byte available",
                    value,
                    encryptedPreferences.getDecryptedString(key)
                )
            }
            assertFalse(
                encryptedPreferences.hasKey(
                    legacyV04QuarantineKey(SUBSTRATE_KEYPAIR.publicKey)
                )
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun mismatchedV04PrivateKeyIsQuarantinedWhileNextWalletMigrates() = runBlocking {
        val expectedBadKeypair = distinctSubstrateKeypair(83)
        val mismatchedKeypair = distinctSubstrateKeypair(91)
        val validSeed = ByteArray(32) { index -> (index + 113).toByte() }
        val validKeypair = SubstrateKeypairFactory.generate(
            encryptionType = CRYPTO_TYPE,
            seed = validSeed,
            junctions = DECODED_SUBSTRATE_DERIVATION_PATH.junctions
        )
        val badAddress = expectedBadKeypair.publicKey.toAddress(0)
        val oldValues = insertV04Secrets(
            preferences = encryptedPreferences,
            address = badAddress,
            keypair = mismatchedKeypair,
            seed = null,
            entropy = null,
            derivationPath = null
        )
        val badPrivateKey = "private_$badAddress"
        storeV1.saveSecuritySource(
            validKeypair.publicKey.toAddress(0),
            SecuritySource.Specified.Seed(
                seed = validSeed,
                keypair = validKeypair,
                derivationPath = DERIVATION_PATH
            )
        )

        val database = performMigration {
            insertAccount(
                publicKey = expectedBadKeypair.publicKey,
                encryptionType = CRYPTO_TYPE,
                position = 0,
                username = "released-v04"
            )
            insertAccount(
                publicKey = validKeypair.publicKey,
                encryptionType = CRYPTO_TYPE,
                position = 1,
                username = "current-v1"
            )
        }

        try {
            val accounts = database.getMetaAccounts()
                .sortedBy(MetaAccountLocal::position)
            assertEquals(2, accounts.size)
            assertNull(storeV2.getMetaAccountSecrets(accounts[0].id))
            assertNotNull(storeV2.getMetaAccountSecrets(accounts[1].id))
            assertFalse(encryptedPreferences.hasKey(badPrivateKey))
            assertEquals(
                oldValues.getValue(badPrivateKey),
                encryptedPreferences.getDecryptedString(
                    legacyV04QuarantineKey(expectedBadKeypair.publicKey)
                )
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun corruptV1ConversionFallsBackToHealthyV04WithoutFalseRecoveryMarker() = runBlocking {
        val address = SUBSTRATE_KEYPAIR.publicKey.toAddress(0)
        val legacyV1Key = legacyV1SecretKey(address)
        encryptedPreferences.putEncryptedString(legacyV1Key, MALFORMED_SECRET)
        val releasedValues = insertV04Secrets(
            preferences = encryptedPreferences,
            address = address,
            keypair = SUBSTRATE_KEYPAIR,
            seed = SUBSTRATE_SEED,
            entropy = MNEMONIC.entropy,
            derivationPath = DERIVATION_PATH
        )

        val database = performMigration {
            insertAccount(
                publicKey = SUBSTRATE_KEYPAIR.publicKey,
                encryptionType = CRYPTO_TYPE
            )
        }

        try {
            val account = database.getMetaAccounts().single()
            assertNotNull(storeV2.getMetaAccountSecrets(account.id))
            assertFalse(encryptedPreferences.hasKey(legacyV1Key))
            assertEquals(
                MALFORMED_SECRET,
                encryptedPreferences.getDecryptedString(
                    WalletSecretQuarantine.keyFor(legacyV1Key)
                )
            )
            assertFalse(
                encryptedPreferences.hasKey(
                    legacyV1QuarantineKey(SUBSTRATE_KEYPAIR.publicKey)
                )
            )
            releasedValues.forEach { (key, exactValue) ->
                assertEquals(
                    exactValue,
                    encryptedPreferences.getDecryptedString(key)
                )
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun corruptV1AndV04PayloadsAreBothPreservedWithoutMigrationFailure() = runBlocking {
        val expectedKeypair = distinctSubstrateKeypair(127)
        val mismatchedV04Keypair = distinctSubstrateKeypair(149)
        val address = expectedKeypair.publicKey.toAddress(0)
        val legacyV1Key = legacyV1SecretKey(address)
        encryptedPreferences.putEncryptedString(legacyV1Key, MALFORMED_SECRET)
        val releasedValues = insertV04Secrets(
            preferences = encryptedPreferences,
            address = address,
            keypair = mismatchedV04Keypair,
            seed = null,
            entropy = null,
            derivationPath = null
        )
        val releasedPrivateKey = "private_$address"

        val database = performMigration {
            insertAccount(
                publicKey = expectedKeypair.publicKey,
                encryptionType = CRYPTO_TYPE
            )
        }

        try {
            val account = database.getMetaAccounts().single()
            assertNull(storeV2.getMetaAccountSecrets(account.id))
            assertFalse(encryptedPreferences.hasKey(legacyV1Key))
            assertFalse(encryptedPreferences.hasKey(releasedPrivateKey))
            assertEquals(
                MALFORMED_SECRET,
                encryptedPreferences.getDecryptedString(
                    WalletSecretQuarantine.keyFor(legacyV1Key)
                )
            )
            assertEquals(
                releasedValues.getValue(releasedPrivateKey),
                encryptedPreferences.getDecryptedString(
                    legacyV04QuarantineKey(expectedKeypair.publicKey)
                )
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun corruptV04FragmentsSurviveCommittedPreferenceWriteAndSqlRollback() =
        runBlocking {
            val preferences = FailingDurableEncryptedPreferences()
            val expectedKeypair = distinctSubstrateKeypair(167)
            val mismatchedKeypair = distinctSubstrateKeypair(179)
            val address = expectedKeypair.publicKey.toAddress(0)
            val releasedValues = insertV04Secrets(
                preferences = preferences,
                address = address,
                keypair = mismatchedKeypair,
                seed = ByteArray(32) { (it + 31).toByte() },
                entropy = ByteArray(16) { (it + 47).toByte() },
                derivationPath = DERIVATION_PATH
            )
            val privateKey = LegacyWalletV04Secrets.privateKey(address)
            val database = helper.createDatabase(TEST_DB, 28).apply {
                insertAccount(
                    publicKey = expectedKeypair.publicKey,
                    encryptionType = CRYPTO_TYPE
                )
            }
            preferences.failAfterDurableWrite = true

            try {
                database.runMigrationTransactionExpectingFailure(
                    migration = V2Migration(
                        SecretStoreV1Impl(preferences),
                        preferences
                    ),
                    expectedFailure = InjectedDurableWriteFailure::class.java
                )

                assertEquals(28, database.version)
                assertEquals(
                    0,
                    database.singleInt("SELECT COUNT(*) FROM meta_accounts")
                )
                releasedValues.keys.forEach { key ->
                    assertFalse(preferences.hasKey(key))
                }
                assertEquals(
                    releasedValues.getValue(privateKey),
                    preferences.getDecryptedString(
                        legacyV04QuarantineKey(expectedKeypair.publicKey)
                    )
                )
                releasedValues
                    .filterKeys { it != privateKey }
                    .forEach { (key, exactValue) ->
                        assertEquals(
                            exactValue,
                            preferences.getDecryptedString(
                                WalletSecretQuarantine.keyFor(key)
                            )
                        )
                    }
                assertFalse(preferences.hasKey("1:ACCESS_SECRETS"))
                assertEquals(1, preferences.durableWriteCount)

                preferences.failAfterDurableWrite = false
                database.runMigrationTransaction(
                    V2Migration(
                        SecretStoreV1Impl(preferences),
                        preferences
                    )
                )

                assertEquals(
                    1,
                    database.singleInt("SELECT COUNT(*) FROM meta_accounts")
                )
                assertFalse(preferences.hasKey("1:ACCESS_SECRETS"))
                assertEquals(1, preferences.durableWriteCount)
                releasedValues
                    .filterKeys { it != privateKey }
                    .forEach { (key, exactValue) ->
                        assertEquals(
                            exactValue,
                            preferences.getDecryptedString(
                                WalletSecretQuarantine.keyFor(key)
                            )
                        )
                    }
            } finally {
                database.close()
            }
        }

    @Test
    fun readOnlyAccountIsPreservedAndASelectedAccountAlwaysExists() = runBlocking {
        storeV1.insertSecrets(Type.SEED)

        val database = performMigration {
            insertAccount(
                publicKey = WATCH_ONLY_PUBLIC_KEY,
                encryptionType = CRYPTO_TYPE,
                position = 0,
                username = WATCH_ONLY_NAME
            )
            insertAccount(
                publicKey = SUBSTRATE_KEYPAIR.publicKey,
                encryptionType = CRYPTO_TYPE,
                position = 1,
                username = NAME
            )
        }

        try {
            val accounts = database.getMetaAccounts().sortedBy(MetaAccountLocal::position)
            assertEquals(2, accounts.size)

            val watchOnly = accounts[0]
            assertEquals(WATCH_ONLY_NAME, watchOnly.name)
            assertArrayEquals(WATCH_ONLY_PUBLIC_KEY, watchOnly.substratePublicKey)
            assertTrue(watchOnly.isSelected)
            assertNull(storeV2.getMetaAccountSecrets(watchOnly.id))

            val signing = accounts[1]
            assertEquals(NAME, signing.name)
            assertFalse(signing.isSelected)
            assertNotNull(storeV2.getMetaAccountSecrets(signing.id))
            assertEquals(1, accounts.count(MetaAccountLocal::isSelected))
        } finally {
            database.close()
        }
    }

    @Test
    fun unreadableV1SecretsAreQuarantinedAndEveryWalletStillOpens() = runBlocking {
        val preferences = FailingDurableEncryptedPreferences()
        val oldStore = SecretStoreV1Impl(preferences)
        val version2Store = SecretStoreV2(preferences)
        oldStore.insertSecrets(Type.SEED)

        val emptySecretAddress = EMPTY_SECRET_PUBLIC_KEY.toAddress(0)
        val malformedSecretAddress = MALFORMED_SECRET_PUBLIC_KEY.toAddress(0)
        preferences.putEncryptedString(legacyV1SecretKey(emptySecretAddress), "")
        preferences.putEncryptedString(
            legacyV1SecretKey(malformedSecretAddress),
            "not-scale-\u0000-\uD83D\uDD10"
        )

        val database = helper.createDatabase(TEST_DB, 28).apply {
            insertAccount(
                publicKey = EMPTY_SECRET_PUBLIC_KEY,
                encryptionType = CRYPTO_TYPE,
                position = 0,
                username = EMPTY_SECRET_NAME
            )
            insertAccount(
                publicKey = MALFORMED_SECRET_PUBLIC_KEY,
                encryptionType = CRYPTO_TYPE,
                position = 1,
                username = MALFORMED_SECRET_NAME
            )
            insertAccount(
                publicKey = SUBSTRATE_KEYPAIR.publicKey,
                encryptionType = CRYPTO_TYPE,
                position = 2,
                username = NAME
            )
        }

        try {
            database.runMigrationTransaction(V2Migration(oldStore, preferences))

            assertEquals(3, database.singleInt("SELECT COUNT(*) FROM users"))
            assertEquals(3, database.singleInt("SELECT COUNT(*) FROM meta_accounts"))

            val accounts = database.getMetaAccounts().sortedBy(MetaAccountLocal::position)
            assertEquals(3, accounts.size)
            assertArrayEquals(EMPTY_SECRET_PUBLIC_KEY, accounts[0].substratePublicKey)
            assertArrayEquals(MALFORMED_SECRET_PUBLIC_KEY, accounts[1].substratePublicKey)
            assertArrayEquals(SUBSTRATE_KEYPAIR.publicKey, accounts[2].substratePublicKey)
            assertTrue(accounts[0].isSelected)
            assertEquals(1, accounts.count(MetaAccountLocal::isSelected))
            assertNull(version2Store.getMetaAccountSecrets(accounts[0].id))
            assertNull(version2Store.getMetaAccountSecrets(accounts[1].id))
            assertNotNull(version2Store.getMetaAccountSecrets(accounts[2].id))
            assertFalse(preferences.hasKey(legacyV1SecretKey(emptySecretAddress)))
            assertFalse(preferences.hasKey(legacyV1SecretKey(malformedSecretAddress)))
            assertEquals(
                "",
                preferences.getDecryptedString(
                    legacyV1QuarantineKey(EMPTY_SECRET_PUBLIC_KEY, metaId = 1)
                )
            )
            assertEquals(
                "not-scale-\u0000-\uD83D\uDD10",
                preferences.getDecryptedString(
                    legacyV1QuarantineKey(
                        MALFORMED_SECRET_PUBLIC_KEY,
                        metaId = 2
                    )
                )
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun invalidMnemonicPathAndPrivateKeyAreIsolatedWhileNextWalletMigrates() = runBlocking {
        val invalidMnemonicKeypair = distinctSubstrateKeypair(11)
        val invalidPathKeypair = distinctSubstrateKeypair(29)
        val invalidPrivateKeypair = distinctSubstrateKeypair(47)
        val validSeed = ByteArray(32) { 71 }
        val validKeypair = SubstrateKeypairFactory.generate(
            encryptionType = CRYPTO_TYPE,
            seed = validSeed,
            junctions = DECODED_SUBSTRATE_DERIVATION_PATH.junctions
        )

        val sources = listOf(
            invalidMnemonicKeypair to SecuritySource.Specified.Mnemonic(
                seed = ByteArray(32) { 11 },
                keypair = invalidMnemonicKeypair,
                mnemonic = "not a valid fearless mnemonic checksum",
                derivationPath = DERIVATION_PATH
            ),
            invalidPathKeypair to SecuritySource.Specified.Seed(
                seed = ByteArray(32) { 29 },
                keypair = invalidPathKeypair,
                derivationPath = "not/rooted"
            ),
            invalidPrivateKeypair to SecuritySource.Unspecified(
                keypair = jp.co.soramitsu.common.data.secrets.v1.Keypair(
                    publicKey = invalidPrivateKeypair.publicKey,
                    privateKey = ByteArray(31) { 47 },
                    nonce = (invalidPrivateKeypair as Sr25519Keypair).nonce
                )
            ),
            validKeypair to SecuritySource.Specified.Seed(
                seed = validSeed,
                keypair = validKeypair,
                derivationPath = DERIVATION_PATH
            )
        )

        sources.forEach { (keypair, source) ->
            storeV1.saveSecuritySource(keypair.publicKey.toAddress(0), source)
        }
        val originalLegacyPayloads = sources.associate { (keypair, _) ->
            val activeKey = legacyV1SecretKey(keypair.publicKey.toAddress(0))
            activeKey to checkNotNull(encryptedPreferences.getDecryptedString(activeKey))
        }

        val database = helper.createDatabase(TEST_DB, 28).apply {
            sources.forEachIndexed { position, (keypair, _) ->
                insertAccount(
                    publicKey = keypair.publicKey,
                    encryptionType = CRYPTO_TYPE,
                    position = position,
                    username = "wallet-$position"
                )
            }
            runMigrationTransaction(migration)
        }

        try {
            val migrated = database.getMetaAccounts().sortedBy(MetaAccountLocal::position)
            assertEquals(4, migrated.size)
            sources.forEachIndexed { index, (keypair, _) ->
                assertArrayEquals(keypair.publicKey, migrated[index].substratePublicKey)
            }
            assertTrue(migrated.first().isSelected)

            migrated.take(3).forEachIndexed { index, account ->
                val publicKey = sources[index].first.publicKey
                val activeKey = legacyV1SecretKey(publicKey.toAddress(0))
                assertNull(storeV2.getMetaAccountSecrets(account.id))
                assertFalse(encryptedPreferences.hasKey(activeKey))
                assertEquals(
                    originalLegacyPayloads.getValue(activeKey),
                    encryptedPreferences.getDecryptedString(
                        legacyV1QuarantineKey(publicKey, account.id)
                    )
                )
            }

            val validAccount = migrated.last()
            val validActiveKey = legacyV1SecretKey(validKeypair.publicKey.toAddress(0))
            assertNotNull(storeV2.getMetaAccountSecrets(validAccount.id))
            assertEquals(
                originalLegacyPayloads.getValue(validActiveKey),
                encryptedPreferences.getDecryptedString(validActiveKey)
            )
            assertFalse(
                encryptedPreferences.hasKey(
                    legacyV1QuarantineKey(
                        validKeypair.publicKey,
                        validAccount.id
                    )
                )
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun quarantineCommitFailureRollsBackSqlAndPreservesTheActiveCiphertext() = runBlocking {
        val preferences = FailingDurableEncryptedPreferences()
        val oldStore = SecretStoreV1Impl(preferences)
        val corruptAddress = MALFORMED_SECRET_PUBLIC_KEY.toAddress(0)
        val activeKey = legacyV1SecretKey(corruptAddress)
        preferences.putEncryptedString(activeKey, MALFORMED_SECRET)

        val database = helper.createDatabase(TEST_DB, 28).apply {
            insertAccount(
                publicKey = MALFORMED_SECRET_PUBLIC_KEY,
                encryptionType = CRYPTO_TYPE,
                username = MALFORMED_SECRET_NAME
            )
        }

        try {
            preferences.failDurableWrites = true
            database.runMigrationTransactionExpectingFailure(
                V2Migration(oldStore, preferences)
            )

            assertEquals(0, database.singleInt("SELECT COUNT(*) FROM meta_accounts"))
            assertEquals(MALFORMED_SECRET, preferences.getDecryptedString(activeKey))
            assertFalse(
                preferences.hasKey(
                    legacyV1QuarantineKey(MALFORMED_SECRET_PUBLIC_KEY)
                )
            )

            preferences.failDurableWrites = false
            database.runMigrationTransaction(V2Migration(oldStore, preferences))

            assertEquals(1, database.singleInt("SELECT COUNT(*) FROM meta_accounts"))
            assertFalse(preferences.hasKey(activeKey))
            assertEquals(
                MALFORMED_SECRET,
                preferences.getDecryptedString(
                    legacyV1QuarantineKey(MALFORMED_SECRET_PUBLIC_KEY)
                )
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun committedQuarantineSurvivesSqlRollbackAndRetryIsIdempotent() = runBlocking {
        val preferences = FailingDurableEncryptedPreferences()
        val oldStore = SecretStoreV1Impl(preferences)
        val corruptAddress = MALFORMED_SECRET_PUBLIC_KEY.toAddress(0)
        val activeKey = legacyV1SecretKey(corruptAddress)
        preferences.putEncryptedString(activeKey, MALFORMED_SECRET)

        val database = helper.createDatabase(TEST_DB, 28).apply {
            insertAccount(
                publicKey = MALFORMED_SECRET_PUBLIC_KEY,
                encryptionType = CRYPTO_TYPE,
                username = MALFORMED_SECRET_NAME
            )
        }

        try {
            preferences.failAfterDurableWrite = true
            database.runMigrationTransactionExpectingFailure(
                V2Migration(oldStore, preferences)
            )

            assertEquals(0, database.singleInt("SELECT COUNT(*) FROM meta_accounts"))
            assertFalse(preferences.hasKey(activeKey))
            assertFalse(
                preferences.hasKey(
                    WalletSecretQuarantine.keyFor(activeKey)
                )
            )
            assertEquals(
                MALFORMED_SECRET,
                preferences.getDecryptedString(
                    legacyV1QuarantineKey(MALFORMED_SECRET_PUBLIC_KEY)
                )
            )

            preferences.failAfterDurableWrite = false
            database.runMigrationTransaction(V2Migration(oldStore, preferences))

            val account = database.getMetaAccounts().single()
            assertArrayEquals(MALFORMED_SECRET_PUBLIC_KEY, account.substratePublicKey)
            assertNull(SecretStoreV2(preferences).getMetaAccountSecrets(account.id))
            assertEquals(
                MALFORMED_SECRET,
                preferences.getDecryptedString(
                    legacyV1QuarantineKey(MALFORMED_SECRET_PUBLIC_KEY)
                )
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun globalSecureStorageFailureNeverQuarantinesAnIndividualWallet() = runBlocking {
        val preferences = FailingDurableEncryptedPreferences()
        val oldStore = SecretStoreV1Impl(preferences)
        oldStore.insertSecrets(Type.SEED)
        val activeKey = legacyV1SecretKey(SUBSTRATE_KEYPAIR.publicKey.toAddress(0))
        preferences.secureStorageUnavailableKey = activeKey

        val database = helper.createDatabase(TEST_DB, 28).apply {
            insertAccount(publicKey = SUBSTRATE_KEYPAIR.publicKey, CRYPTO_TYPE)
        }

        try {
            database.runMigrationTransactionExpectingFailure(
                migration = V2Migration(oldStore, preferences),
                expectedFailure = WalletSecureStorageUnavailableException::class.java
            )

            assertEquals(0, database.singleInt("SELECT COUNT(*) FROM meta_accounts"))
            assertTrue(preferences.hasKey(activeKey))
            assertFalse(
                preferences.hasKey(
                    legacyV1QuarantineKey(SUBSTRATE_KEYPAIR.publicKey)
                )
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun durableSecretWriteFailureRollsBackDatabasePreservesV1AndSafelyRetries() = runBlocking {
        val preferences = FailingDurableEncryptedPreferences()
        val oldStore = SecretStoreV1Impl(preferences)
        oldStore.insertSecrets(Type.SEED)

        val database = helper.createDatabase(TEST_DB, 28).apply {
            insertAccount(publicKey = SUBSTRATE_KEYPAIR.publicKey, CRYPTO_TYPE)
        }

        try {
            preferences.failDurableWrites = true
            database.runMigrationTransactionExpectingFailure(
                V2Migration(oldStore, preferences)
            )

            assertEquals(1, database.singleInt("SELECT COUNT(*) FROM users"))
            assertEquals(0, database.singleInt("SELECT COUNT(*) FROM meta_accounts"))
            assertNotNull(
                oldStore.getSecuritySource(SUBSTRATE_KEYPAIR.publicKey.toAddress(0))
            )
            assertNull(preferences.getDecryptedString("1:ACCESS_SECRETS"))

            // A fresh process can retry after the transient storage failure.
            preferences.failDurableWrites = false
            database.runMigrationTransaction(V2Migration(oldStore, preferences))

            assertEquals(1, database.singleInt("SELECT COUNT(*) FROM users"))
            assertEquals(1, database.singleInt("SELECT COUNT(*) FROM meta_accounts"))
            assertNotNull(SecretStoreV2(preferences).getMetaAccountSecrets(1))
        } finally {
            database.close()
        }
    }

    @Test
    fun ethereumSecretCommittedBeforeSqlRollbackIsRecoveredOnRetry() = runBlocking {
        val preferences = FailingDurableEncryptedPreferences()
        val version2Store = SecretStoreV2(preferences)
        val database = createVersion31Database(preferences)

        try {
            val metaId = database.getMetaAccounts().single().id
            val oldEthereumKeypair = EthereumKeypairFactory.generate(
                seed = ETHEREUM_SEED,
                junctions = emptyList()
            )
            val oldSecrets = MetaAccountSecrets(
                substrateKeyPair = SUBSTRATE_KEYPAIR,
                entropy = MNEMONIC.entropy,
                seed = SUBSTRATE_SEED,
                substrateDerivationPath = DERIVATION_PATH,
                ethereumKeypair = oldEthereumKeypair,
                ethereumDerivationPath = "m/44'/60'/0'/0"
            )
            version2Store.putMetaAccountSecrets(metaId, oldSecrets)
            database.updateEthereumPublicKey(metaId, oldEthereumKeypair.publicKey)

            preferences.failAfterDurableWrite = true
            database.runMigrationTransactionExpectingFailure(
                EthereumDerivationPathMigration(preferences)
            )

            // The external preference commit survived, while Room rolled SQL
            // back. This is the exact process-death/retry boundary.
            assertArrayEquals(
                ETHEREUM_KEYPAIR.publicKey,
                requireNotNull(
                    version2Store.getMetaAccountSecrets(metaId)
                )[MetaAccountSecrets.EthereumKeypair]?.get(KeyPairSchema.PublicKey)
            )
            assertArrayEquals(
                oldEthereumKeypair.publicKey,
                database.singleBlob(
                    "SELECT ethereumPublicKey FROM meta_accounts WHERE id = ?",
                    metaId
                )
            )

            val activeSecretKey = "$metaId:ACCESS_SECRETS"
            val correctedSecretBeforeRetry =
                preferences.rawSnapshot().getValue(activeSecretKey)
            val durableWritesBeforeRetry = preferences.durableWriteCount
            preferences.failAfterDurableWrite = false
            database.runMigrationTransaction(
                EthereumDerivationPathMigration(preferences)
            )

            assertEquals(
                "An exact corrected-secret retry must not rewrite secure storage",
                durableWritesBeforeRetry,
                preferences.durableWriteCount
            )
            assertEquals(
                correctedSecretBeforeRetry,
                preferences.rawSnapshot().getValue(activeSecretKey)
            )
            assertArrayEquals(
                ETHEREUM_KEYPAIR.publicKey,
                database.singleBlob(
                    "SELECT ethereumPublicKey FROM meta_accounts WHERE id = ?",
                    metaId
                )
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun db31ExactInjectedAssetRowLimitAllowsEthereumCorrection() =
        runBlocking {
            val preferences = FailingDurableEncryptedPreferences()
            val version2Store = SecretStoreV2(preferences)
            val database = createVersion31Database(preferences)
            val metaId = database.getMetaAccounts().single().id
            val oldEthereumKeypair = EthereumKeypairFactory.generate(
                seed = ETHEREUM_SEED,
                junctions = emptyList()
            )
            val oldEthereumAddress =
                oldEthereumKeypair.publicKey.ethereumAddressFromPublicKey()
            version2Store.putMetaAccountSecrets(
                metaId = metaId,
                secrets = MetaAccountSecrets(
                    substrateKeyPair = SUBSTRATE_KEYPAIR,
                    entropy = MNEMONIC.entropy,
                    seed = SUBSTRATE_SEED,
                    substrateDerivationPath = DERIVATION_PATH,
                    ethereumKeypair = oldEthereumKeypair,
                    ethereumDerivationPath = "m/44'/60'/0'/0"
                )
            )
            database.updateEthereumPublicKey(
                metaId = metaId,
                publicKey = oldEthereumKeypair.publicKey
            )
            database.insertVersion31Asset(
                metaId = metaId,
                accountId = oldEthereumAddress
            )
            val preferenceInspectionsBefore =
                preferences.preferenceInspectionCount

            try {
                database.runMigrationTransaction(
                    EthereumDerivationPathMigration(
                        encryptedPreferences = preferences,
                        limits = EthereumDerivationPathMigrationLimits(
                            maxAssetRows = 1
                        )
                    )
                )

                assertTrue(
                    preferences.preferenceInspectionCount >
                        preferenceInspectionsBefore
                )
                assertEquals(
                    1,
                    database.singleInt("SELECT COUNT(*) FROM assets")
                )
                assertArrayEquals(
                    ETHEREUM_KEYPAIR.publicKey.ethereumAddressFromPublicKey(),
                    database.singleBlob(
                        "SELECT accountId FROM assets WHERE metaId = ?",
                        metaId
                    )
                )
            } finally {
                database.close()
            }
        }

    @Test
    fun db31AssetRowLimitPlusOneFailsBeforePreferenceAccessOrDurableWrite() =
        runBlocking {
            val preferences = FailingDurableEncryptedPreferences()
            val database = createVersion31Database(preferences)
            val metaId = database.getMetaAccounts().single().id
            val accountId = ByteArray(20) { (it + 101).toByte() }
            database.insertVersion31Asset(
                metaId = metaId,
                accountId = accountId,
                tokenSymbol = "LIMIT-0"
            )
            database.insertVersion31Asset(
                metaId = metaId,
                accountId = accountId,
                tokenSymbol = "LIMIT-1"
            )
            val exactBefore = preferences.rawSnapshot()
            val preferenceInspectionsBefore =
                preferences.preferenceInspectionCount
            val durableWritesBefore = preferences.durableWriteCount

            try {
                database.runMigrationTransactionExpectingFailure(
                    migration = EthereumDerivationPathMigration(
                        encryptedPreferences = preferences,
                        limits = EthereumDerivationPathMigrationLimits(
                            maxAssetRows = 1
                        )
                    ),
                    expectedFailure =
                        WalletPublicIdentityIntegrityException::class.java
                )

                assertEquals(31, database.version)
                assertEquals(exactBefore, preferences.rawSnapshot())
                assertEquals(
                    preferenceInspectionsBefore,
                    preferences.preferenceInspectionCount
                )
                assertEquals(
                    durableWritesBefore,
                    preferences.durableWriteCount
                )
                assertEquals(
                    2,
                    database.singleInt("SELECT COUNT(*) FROM assets")
                )
            } finally {
                database.close()
            }
        }

    @Test
    fun db31WrongAssetWalletIndexFailsBeforePreferenceAccessAndRetries() =
        runBlocking {
            val preferences = FailingDurableEncryptedPreferences()
            val fixture = createDb31EthereumCorrectionFixture(preferences)
            val database = fixture.database
            database.execSQL("DROP INDEX index_assets_metaId")
            database.execSQL(
                "CREATE INDEX index_assets_metaId ON assets(accountId)"
            )
            val exactPreferencesBefore = preferences.rawSnapshot()
            val preferenceInspectionsBefore =
                preferences.preferenceInspectionCount
            val durableWritesBefore = preferences.durableWriteCount

            try {
                database.runMigrationTransactionExpectingFailure(
                    migration = EthereumDerivationPathMigration(preferences),
                    expectedFailure =
                        WalletPublicIdentityIntegrityException::class.java
                )

                assertEquals(31, database.version)
                assertEquals(exactPreferencesBefore, preferences.rawSnapshot())
                assertEquals(
                    preferenceInspectionsBefore,
                    preferences.preferenceInspectionCount
                )
                assertEquals(
                    durableWritesBefore,
                    preferences.durableWriteCount
                )
                fixture.assertDatabaseStillUsesOldEthereumIdentity()

                database.execSQL("DROP INDEX index_assets_metaId")
                database.execSQL(
                    "CREATE INDEX index_assets_metaId ON assets(metaId)"
                )
                database.runMigrationTransaction(
                    EthereumDerivationPathMigration(preferences)
                )

                fixture.assertDatabaseUsesCorrectedEthereumIdentity()
            } finally {
                database.close()
            }
        }

    @Test
    fun db31UnrelatedIndexColumnAndDefaultChangesFailBeforePreferenceAccess() =
        runBlocking {
            val corruptions:
                List<(SupportSQLiteDatabase) -> Unit> = listOf(
                    { database ->
                        database.execSQL(
                            "DROP INDEX index_chain_runtimes_chainId"
                        )
                        database.execSQL(
                            "CREATE INDEX index_chain_runtimes_chainId " +
                                "ON chain_runtimes(remoteVersion)"
                        )
                    },
                    { database ->
                        database.execSQL(
                            "ALTER TABLE operations " +
                                "ADD COLUMN unexpectedPayload TEXT"
                        )
                    },
                    { database ->
                        database.replaceChainNodesWithWrongDefault()
                    }
                )

            corruptions.forEach { corrupt ->
                val preferences = FailingDurableEncryptedPreferences()
                val database = createVersion31Database(preferences)
                try {
                    corrupt(database)
                    val exactPreferencesBefore = preferences.rawSnapshot()
                    val preferenceInspectionsBefore =
                        preferences.preferenceInspectionCount
                    val durableWritesBefore = preferences.durableWriteCount

                    database.runMigrationTransactionExpectingFailure(
                        migration =
                            EthereumDerivationPathMigration(preferences),
                        expectedFailure =
                            WalletPublicIdentityIntegrityException::class.java
                    )

                    assertEquals(31, database.version)
                    assertEquals(
                        exactPreferencesBefore,
                        preferences.rawSnapshot()
                    )
                    assertEquals(
                        preferenceInspectionsBefore,
                        preferences.preferenceInspectionCount
                    )
                    assertEquals(
                        durableWritesBefore,
                        preferences.durableWriteCount
                    )
                    assertEquals(
                        1,
                        database.singleInt(
                            "SELECT COUNT(*) FROM meta_accounts"
                        )
                    )
                } finally {
                    database.close()
                }
            }
        }

    @Test
    fun db31WriteBlockingCheckFailsBeforePreferenceAccess() =
        runBlocking {
            val preferences = FailingDurableEncryptedPreferences()
            val fixture = createDb31EthereumCorrectionFixture(preferences)
            val database = fixture.database
            database.replaceAssetsWithBlockingCheck(
                allowedAccountId = fixture.oldEthereumAddress
            )
            val exactPreferencesBefore = preferences.rawSnapshot()
            val preferenceInspectionsBefore =
                preferences.preferenceInspectionCount
            val durableWritesBefore = preferences.durableWriteCount

            try {
                database.runMigrationTransactionExpectingFailure(
                    migration = EthereumDerivationPathMigration(preferences),
                    expectedFailure =
                        WalletPublicIdentityIntegrityException::class.java
                )

                assertEquals(31, database.version)
                assertEquals(exactPreferencesBefore, preferences.rawSnapshot())
                assertEquals(
                    preferenceInspectionsBefore,
                    preferences.preferenceInspectionCount
                )
                assertEquals(
                    durableWritesBefore,
                    preferences.durableWriteCount
                )
                fixture.assertDatabaseStillUsesOldEthereumIdentity()
            } finally {
                database.close()
            }
        }

    @Test
    fun db31SuppressedWalletIdentityUpdateRollsBackAndRetries() =
        runBlocking {
            val preferences = FailingDurableEncryptedPreferences()
            val fixture = createDb31EthereumCorrectionFixture(preferences)
            val database = fixture.database
            val exactPreferencesBefore = preferences.rawSnapshot()
            val oldCiphertext = exactPreferencesBefore.getValue(
                fixture.activeSecretKey
            )
            val durableWritesBefore = preferences.durableWriteCount
            val suppressedUpdater =
                Db31WalletIdentityUpdater { _, _, _, _ -> 0 }

            try {
                database.runMigrationTransactionExpectingFailure(
                    migration = EthereumDerivationPathMigration(
                        encryptedPreferences = preferences,
                        walletIdentityUpdater = suppressedUpdater
                    ),
                    expectedFailure =
                        WalletSecretConcurrentMutationException::class.java
                )

                assertEquals(31, database.version)
                assertEquals(
                    durableWritesBefore + 1,
                    preferences.durableWriteCount
                )
                assertFalse(
                    oldCiphertext ==
                        preferences.rawSnapshot().getValue(
                            fixture.activeSecretKey
                        )
                )
                assertArrayEquals(
                    ETHEREUM_KEYPAIR.publicKey,
                    requireNotNull(
                        SecretStoreV2(preferences)
                            .getMetaAccountSecrets(fixture.metaId)
                    )[MetaAccountSecrets.EthereumKeypair]
                        ?.get(KeyPairSchema.PublicKey)
                )
                fixture.assertDatabaseStillUsesOldEthereumIdentity()

                val correctedPreferences = preferences.rawSnapshot()
                val durableWritesBeforeRetry = preferences.durableWriteCount
                database.runMigrationTransaction(
                    EthereumDerivationPathMigration(preferences)
                )

                assertEquals(
                    correctedPreferences,
                    preferences.rawSnapshot()
                )
                assertEquals(
                    durableWritesBeforeRetry,
                    preferences.durableWriteCount
                )
                fixture.assertDatabaseUsesCorrectedEthereumIdentity()
            } finally {
                database.close()
            }
        }

    @Test
    fun db31ConcurrentAccessSecretMutationRejectsStaleCorrectionAndRetries() =
        runBlocking {
            val preferences = FailingDurableEncryptedPreferences()
            val version2Store = SecretStoreV2(preferences)
            val database = createVersion31Database(preferences)
            val metaId = database.getMetaAccounts().single().id
            val activeSecretKey = "$metaId:ACCESS_SECRETS"
            val recoveryMarkerKey = WalletPublicIdentityRecovery.keyFor(
                metaId = metaId,
                activeSecretKey = activeSecretKey
            )
            val oldEthereumKeypair = EthereumKeypairFactory.generate(
                seed = ETHEREUM_SEED,
                junctions = emptyList()
            )
            version2Store.putMetaAccountSecrets(
                metaId = metaId,
                secrets = MetaAccountSecrets(
                    substrateKeyPair = SUBSTRATE_KEYPAIR,
                    entropy = MNEMONIC.entropy,
                    seed = SUBSTRATE_SEED,
                    substrateDerivationPath = DERIVATION_PATH,
                    ethereumKeypair = oldEthereumKeypair,
                    ethereumDerivationPath = "m/44'/60'/0'/0"
                )
            )
            database.updateEthereumPublicKey(
                metaId = metaId,
                publicKey = oldEthereumKeypair.publicKey
            )
            val historicalSecret =
                preferences.rawSnapshot().getValue(activeSecretKey)
            val durableWritesBeforeRace = preferences.durableWriteCount
            preferences.mutateOnceBeforeSnapshotBoundReplacement(
                sourceField = activeSecretKey,
                replacement = MALFORMED_SECRET
            )

            try {
                database.runMigrationTransactionExpectingFailure(
                    migration = EthereumDerivationPathMigration(preferences),
                    expectedFailure =
                        WalletSecretConcurrentMutationException::class.java
                )

                assertEquals(31, database.version)
                assertEquals(
                    MALFORMED_SECRET,
                    preferences.getDecryptedString(activeSecretKey)
                )
                assertEquals(
                    durableWritesBeforeRace,
                    preferences.durableWriteCount
                )
                assertFalse(
                    preferences.hasKey(
                        WalletSecretQuarantine.keyFor(activeSecretKey)
                    )
                )
                assertFalse(preferences.hasKey(recoveryMarkerKey))
                assertArrayEquals(
                    oldEthereumKeypair.publicKey,
                    database.singleBlob(
                        "SELECT ethereumPublicKey FROM meta_accounts WHERE id = ?",
                        metaId
                    )
                )

                preferences.putEncryptedString(
                    activeSecretKey,
                    historicalSecret
                )
                database.runMigrationTransaction(
                    EthereumDerivationPathMigration(preferences)
                )

                assertArrayEquals(
                    ETHEREUM_KEYPAIR.publicKey,
                    requireNotNull(
                        version2Store.getMetaAccountSecrets(metaId)
                    )[MetaAccountSecrets.EthereumKeypair]
                        ?.get(KeyPairSchema.PublicKey)
                )
                assertArrayEquals(
                    ETHEREUM_KEYPAIR.publicKey,
                    database.singleBlob(
                        "SELECT ethereumPublicKey FROM meta_accounts WHERE id = ?",
                        metaId
                    )
                )
            } finally {
                database.close()
            }
        }

    @Test
    fun db31ConcurrentRecoveryMarkerCreationIsNeverOverwritten() =
        runBlocking {
            val preferences = FailingDurableEncryptedPreferences()
            val database = createVersion31Database(preferences)
            val metaId = database.getMetaAccounts().single().id
            val activeSecretKey = "$metaId:ACCESS_SECRETS"
            val recoveryMarkerKey = WalletPublicIdentityRecovery.keyFor(
                metaId = metaId,
                activeSecretKey = activeSecretKey
            )
            val concurrentMarker = "concurrent-invalid-recovery-marker"
            database.execSQL(
                "UPDATE meta_accounts SET ethereumAddress = NULL WHERE id = ?",
                arrayOf(metaId)
            )
            val exactBeforeRace = preferences.rawSnapshot()
            val durableWritesBeforeRace = preferences.durableWriteCount
            preferences.mutateOnceBeforeSnapshotBoundReplacement(
                sourceField = recoveryMarkerKey,
                replacement = concurrentMarker
            )

            try {
                database.runMigrationTransactionExpectingFailure(
                    migration = EthereumDerivationPathMigration(preferences),
                    expectedFailure =
                    WalletSecretConcurrentMutationException::class.java
                )

                assertEquals(
                    exactBeforeRace + (recoveryMarkerKey to concurrentMarker),
                    preferences.rawSnapshot()
                )
                assertEquals(
                    durableWritesBeforeRace,
                    preferences.durableWriteCount
                )
                assertEquals(
                    exactBeforeRace.getValue(activeSecretKey),
                    preferences.getDecryptedString(activeSecretKey)
                )
                assertFalse(
                    preferences.hasKey(
                        WalletSecretQuarantine.keyFor(activeSecretKey)
                    )
                )

                database.runMigrationTransactionExpectingFailure(
                    migration = EthereumDerivationPathMigration(preferences),
                    expectedFailure = IllegalStateException::class.java
                )
                assertEquals(
                    concurrentMarker,
                    preferences.getDecryptedString(recoveryMarkerKey)
                )

                preferences.removeKey(recoveryMarkerKey)
                database.runMigrationTransaction(
                    EthereumDerivationPathMigration(preferences)
                )
                assertEquals(
                    WalletPublicIdentityRecovery.MARKER_VALUE,
                    preferences.getDecryptedString(recoveryMarkerKey)
                )
                assertEquals(
                    exactBeforeRace.getValue(activeSecretKey),
                    preferences.getDecryptedString(activeSecretKey)
                )
            } finally {
                database.close()
            }
        }

    @Test
    fun separatedEthereumSecretRecoversFull31To77TransactionAfterSqlRollback() = runBlocking {
        val preferences = FailingDurableEncryptedPreferences()
        val oldStore = SecretStoreV1Impl(preferences)
        val version2Store = SecretStoreV2(preferences)
        val ethereumSecretStore = EthereumSecretStore(preferences)
        oldStore.insertSecrets(Type.MNEMONIC)

        val version31Database = helper.createDatabase(TEST_DB, 28).apply {
            insertAccount(publicKey = SUBSTRATE_KEYPAIR.publicKey, CRYPTO_TYPE)
            beginTransaction()
            try {
                V2Migration(oldStore, preferences).migrate(this)
                MigrateTablesToV2_29_30.migrate(this)
                MigrateTablesToV2_30_31.migrate(this)
                version = 31
                setTransactionSuccessful()
            } finally {
                endTransaction()
            }
        }

        val metaId = version31Database.getMetaAccounts().single().id
        val oldEthereumKeypair = EthereumKeypairFactory.generate(
            seed = ETHEREUM_SEED,
            junctions = emptyList()
        )
        val oldSecrets = MetaAccountSecrets(
            substrateKeyPair = SUBSTRATE_KEYPAIR,
            entropy = MNEMONIC.entropy,
            seed = SUBSTRATE_SEED,
            substrateDerivationPath = DERIVATION_PATH,
            ethereumKeypair = oldEthereumKeypair,
            ethereumDerivationPath = "m/44'/60'/0'/0"
        )
        version2Store.putMetaAccountSecrets(metaId, oldSecrets)
        version31Database.updateEthereumPublicKey(metaId, oldEthereumKeypair.publicKey)

        val productionMigrations = AppDatabase.migrations(
            storeV1 = oldStore,
            storeV2 = version2Store,
            encryptedPreferences = preferences,
            substrateSecretStore = SubstrateSecretStore(preferences),
            ethereumSecretStore = ethereumSecretStore
        ).filter { it.startVersion >= 31 }

        version31Database.beginTransaction()
        try {
            assertThrows(InjectedPostTonSqlFailure::class.java) {
                productionMigrations.forEach { migration ->
                    migration.migrate(version31Database)
                    if (migration.endVersion == 72) {
                        throw InjectedPostTonSqlFailure()
                    }
                }
            }
        } finally {
            version31Database.endTransaction()
        }

        // The outer SQL transaction returned to v31 metadata, while TON's
        // durable external commit already moved the corrected key into v3.
        assertEquals(31, version31Database.version)
        assertArrayEquals(
            oldEthereumKeypair.publicKey,
            version31Database.singleBlob(
                "SELECT ethereumPublicKey FROM meta_accounts WHERE id = ?",
                metaId
            )
        )
        assertNull(version2Store.getMetaAccountSecrets(metaId))
        assertArrayEquals(
            ETHEREUM_KEYPAIR.publicKey,
            requireNotNull(
                ethereumSecretStore.get(metaId)
            )[EthereumSecrets.EthereumKeypair][KeyPairSchema.PublicKey]
        )
        version31Database.close()

        // Reopen through the real production Room builder. This retries the
        // complete 31 -> 77 path in one transaction and performs Room's final
        // schema validation.
        val migratedDatabase = AppDatabase.create(
            context = InstrumentationRegistry.getInstrumentation().targetContext,
            databaseName = TEST_DB,
            storeV1 = oldStore,
            storeV2 = version2Store,
            encryptedPreferences = preferences,
            substrateSecretStore = SubstrateSecretStore(preferences),
            ethereumSecretStore = ethereumSecretStore
        )
        try {
            val migrated = migratedDatabase.openHelper.writableDatabase
            assertEquals(77, migrated.version)
            assertArrayEquals(
                ETHEREUM_KEYPAIR.publicKey,
                migrated.singleBlob(
                    "SELECT ethereumPublicKey FROM meta_accounts WHERE id = ?",
                    metaId
                )
            )
            assertNull(version2Store.getMetaAccountSecrets(metaId))
            assertArrayEquals(
                ETHEREUM_KEYPAIR.publicKey,
                requireNotNull(
                    ethereumSecretStore.get(metaId)
                )[EthereumSecrets.EthereumKeypair][KeyPairSchema.PublicKey]
            )
        } finally {
            migratedDatabase.close()
        }
    }

    @Test
    fun separatedRootsRecoverFull28To77TransactionAfterPostTonSqlRollback() =
        runBlocking {
            val preferences = FailingDurableEncryptedPreferences()
            val oldStore = SecretStoreV1Impl(preferences)
            val version2Store = SecretStoreV2(preferences)
            val substrateSecretStore = SubstrateSecretStore(preferences)
            val ethereumSecretStore = EthereumSecretStore(preferences)
            oldStore.insertSecrets(Type.MNEMONIC)
            val version28Database = helper.createDatabase(TEST_DB, 28).apply {
                insertAccount(
                    publicKey = SUBSTRATE_KEYPAIR.publicKey,
                    encryptionType = CRYPTO_TYPE
                )
            }
            val productionMigrations = AppDatabase.migrations(
                storeV1 = oldStore,
                storeV2 = version2Store,
                encryptedPreferences = preferences,
                substrateSecretStore = substrateSecretStore,
                ethereumSecretStore = ethereumSecretStore
            ).filter { it.startVersion >= 28 }
            var migratedMetaId: Long? = null

            version28Database.beginTransaction()
            try {
                assertThrows(InjectedPostTonSqlFailure::class.java) {
                    productionMigrations.forEach { migration ->
                        migration.migrate(version28Database)
                        if (migration.endVersion == 29) {
                            migratedMetaId =
                                version28Database.getMetaAccounts().single().id
                        }
                        if (migration.endVersion == 73) {
                            throw InjectedPostTonSqlFailure()
                        }
                    }
                }
            } finally {
                version28Database.endTransaction()
            }

            val metaId = checkNotNull(migratedMetaId)
            val accessKey = "$metaId:ACCESS_SECRETS"
            val substrateKey = "$metaId:SUBSTRATE_SECRETS"
            val ethereumKey = "$metaId:ETHEREUM_SECRETS"
            assertEquals(28, version28Database.version)
            assertEquals(
                0,
                version28Database.singleInt(
                    "SELECT COUNT(*) FROM meta_accounts"
                )
            )
            assertFalse(preferences.hasKey(accessKey))
            assertTrue(preferences.hasKey(substrateKey))
            assertTrue(preferences.hasKey(ethereumKey))
            val exactSplitState = preferences.rawSnapshot()
            val durableWritesAfterRollback =
                preferences.durableWriteCount
            version28Database.close()

            // This is the production retry that previously recreated ACCESS in
            // V2 and then failed forever at the v31 ambiguity check.
            val migratedDatabase = AppDatabase.create(
                context =
                    InstrumentationRegistry.getInstrumentation().targetContext,
                databaseName = TEST_DB,
                storeV1 = oldStore,
                storeV2 = version2Store,
                encryptedPreferences = preferences,
                substrateSecretStore = substrateSecretStore,
                ethereumSecretStore = ethereumSecretStore
            )
            try {
                val migrated = migratedDatabase.openHelper.writableDatabase
                assertEquals(77, migrated.version)
                assertEquals(metaId, migrated.getMetaAccounts().single().id)
                assertEquals(exactSplitState, preferences.rawSnapshot())
                assertEquals(
                    durableWritesAfterRollback,
                    preferences.durableWriteCount
                )
                assertFalse(preferences.hasKey(accessKey))
                assertTrue(preferences.hasKey(substrateKey))
                assertTrue(preferences.hasKey(ethereumKey))
                assertNull(version2Store.getMetaAccountSecrets(metaId))
                assertNotNull(substrateSecretStore.get(metaId))
                assertNotNull(ethereumSecretStore.get(metaId))
            } finally {
                migratedDatabase.close()
            }
        }

    @Test
    fun exactSubstrateOnlySeparatedRetryMigratesWithoutPreferenceWrite() =
        runBlocking {
            val preferences = FailingDurableEncryptedPreferences()
            val oldStore = SecretStoreV1Impl(preferences)
            oldStore.insertSecrets(Type.SEED)
            val substrateSecretStore = SubstrateSecretStore(preferences)
            substrateSecretStore.put(
                metaId = 1L,
                secrets = SubstrateSecrets(
                    substrateKeyPair = SUBSTRATE_KEYPAIR,
                    entropy = null,
                    seed = SUBSTRATE_SEED,
                    substrateDerivationPath = DERIVATION_PATH
                )
            )
            val exactBefore = preferences.rawSnapshot()
            helper.createDatabase(TEST_DB, 28).apply {
                insertAccount(
                    publicKey = SUBSTRATE_KEYPAIR.publicKey,
                    encryptionType = CRYPTO_TYPE
                )
                close()
            }
            val migratedDatabase = AppDatabase.create(
                context =
                    InstrumentationRegistry.getInstrumentation().targetContext,
                databaseName = TEST_DB,
                storeV1 = oldStore,
                storeV2 = SecretStoreV2(preferences),
                encryptedPreferences = preferences,
                substrateSecretStore = substrateSecretStore,
                ethereumSecretStore = EthereumSecretStore(preferences)
            )

            try {
                val migrated = migratedDatabase.openHelper.writableDatabase
                assertEquals(77, migrated.version)
                val migratedAccount = migrated.getMetaAccounts().single()
                assertEquals(1L, migratedAccount.id)
                assertCorrectMetaAccount(
                    metaAccountLocal = migratedAccount,
                    selected = true,
                    withEthereum = false
                )
                assertEquals(exactBefore, preferences.rawSnapshot())
                assertEquals(0, preferences.durableWriteCount)
                assertTrue(
                    preferences.snapshotBoundReplacementCount >= 1
                )
                assertFalse(preferences.hasKey("1:ACCESS_SECRETS"))
                assertTrue(preferences.hasKey("1:SUBSTRATE_SECRETS"))
                assertFalse(preferences.hasKey("1:ETHEREUM_SECRETS"))
                assertFalse(preferences.hasKey("1:TON_SECRETS"))
                assertNull(SecretStoreV2(preferences).getMetaAccountSecrets(1L))
                assertNotNull(substrateSecretStore.get(1L))
            } finally {
                migratedDatabase.close()
            }
        }

    @Test
    fun multipleExactSeparatedRetriesMigrateWithoutPreferenceWrite() =
        runBlocking {
            val preferences = FailingDurableEncryptedPreferences()
            val oldStore = SecretStoreV1Impl(preferences)
            val substrateSecretStore = SubstrateSecretStore(preferences)
            val firstKeypair = distinctSubstrateKeypair(211)
            val secondKeypair = distinctSubstrateKeypair(223)
            listOf(firstKeypair, secondKeypair)
                .forEachIndexed { index, keypair ->
                    oldStore.saveSecuritySource(
                        keypair.publicKey.toAddress(0),
                        SecuritySource.Unspecified(keypair = keypair)
                    )
                    substrateSecretStore.put(
                        metaId = index + 1L,
                        secrets = SubstrateSecrets(
                            substrateKeyPair = keypair,
                            entropy = null,
                            seed = null,
                            substrateDerivationPath = null
                        )
                    )
                }
            val exactBefore = preferences.rawSnapshot()
            helper.createDatabase(TEST_DB, 28).apply {
                insertAccount(
                    publicKey = firstKeypair.publicKey,
                    encryptionType = CRYPTO_TYPE,
                    position = 0,
                    username = "First retry wallet"
                )
                insertAccount(
                    publicKey = secondKeypair.publicKey,
                    encryptionType = CRYPTO_TYPE,
                    position = 1,
                    username = "Second retry wallet"
                )
                close()
            }
            val migratedDatabase = AppDatabase.create(
                context =
                    InstrumentationRegistry.getInstrumentation().targetContext,
                databaseName = TEST_DB,
                storeV1 = oldStore,
                storeV2 = SecretStoreV2(preferences),
                encryptedPreferences = preferences,
                substrateSecretStore = substrateSecretStore,
                ethereumSecretStore = EthereumSecretStore(preferences)
            )

            try {
                val migrated = migratedDatabase.openHelper.writableDatabase
                assertEquals(77, migrated.version)
                val migratedAccounts =
                    migrated.getMetaAccounts().sortedBy(MetaAccountLocal::position)
                assertEquals(2, migratedAccounts.size)
                assertEquals(1L, migratedAccounts[0].id)
                assertEquals(2L, migratedAccounts[1].id)
                assertArrayEquals(
                    firstKeypair.publicKey,
                    migratedAccounts[0].substratePublicKey
                )
                assertArrayEquals(
                    secondKeypair.publicKey,
                    migratedAccounts[1].substratePublicKey
                )
                assertTrue(migratedAccounts[0].isSelected)
                assertFalse(migratedAccounts[1].isSelected)
                assertEquals(exactBefore, preferences.rawSnapshot())
                assertEquals(0, preferences.durableWriteCount)
                assertTrue(
                    preferences.snapshotBoundReplacementCount >= 1
                )
                listOf(1L, 2L).forEach { metaId ->
                    assertFalse(
                        preferences.hasKey("$metaId:ACCESS_SECRETS")
                    )
                    assertTrue(
                        preferences.hasKey("$metaId:SUBSTRATE_SECRETS")
                    )
                    assertFalse(
                        preferences.hasKey("$metaId:ETHEREUM_SECRETS")
                    )
                    assertFalse(
                        preferences.hasKey("$metaId:TON_SECRETS")
                    )
                    assertNull(
                        SecretStoreV2(preferences)
                            .getMetaAccountSecrets(metaId)
                    )
                    assertNotNull(substrateSecretStore.get(metaId))
                }
            } finally {
                migratedDatabase.close()
            }
        }

    @Test
    fun keypairOnlyAccessSecretSurvivesFull31To77RollbackAndRetry() =
        runBlocking {
            assertNullEntropyAccessSecretSurvivesFullChainRetry(
                insertionType = Type.KEYPAIR,
                expectedSeed = null,
                expectedDerivationPath = null
            )
        }

    @Test
    fun seedOnlyAccessSecretSurvivesFull31To77RollbackAndRetry() =
        runBlocking {
            assertNullEntropyAccessSecretSurvivesFullChainRetry(
                insertionType = Type.SEED,
                expectedSeed = SUBSTRATE_SEED,
                expectedDerivationPath = DERIVATION_PATH
            )
        }

    @Test
    fun db31KeypairOnlySeparatedRetryUsesHistoricalIdentityWithoutEntropy() =
        runBlocking {
            val preferences = FailingDurableEncryptedPreferences()
            val database = createVersion31Database(preferences)
            val metaId = database.getMetaAccounts().single().id
            installKeypairOnlySeparatedRetrySecrets(preferences, metaId)
            database.updateEthereumPublicKey(
                metaId = metaId,
                publicKey = ETHEREUM_KEYPAIR.publicKey
            )
            val exactBefore = preferences.rawSnapshot()
            val durableWritesBefore = preferences.durableWriteCount
            val snapshotReplacementsBefore =
                preferences.snapshotBoundReplacementCount

            try {
                repeat(2) {
                    database.runMigrationTransaction(
                        EthereumDerivationPathMigration(preferences)
                    )
                }

                assertEquals(exactBefore, preferences.rawSnapshot())
                assertEquals(
                    durableWritesBefore,
                    preferences.durableWriteCount
                )
                assertEquals(
                    snapshotReplacementsBefore + 2,
                    preferences.snapshotBoundReplacementCount
                )
                assertArrayEquals(
                    ETHEREUM_KEYPAIR.publicKey,
                    database.singleBlob(
                        "SELECT ethereumPublicKey FROM meta_accounts " +
                            "WHERE id = ?",
                        metaId
                    )
                )
                assertArrayEquals(
                    ETHEREUM_KEYPAIR.publicKey
                        .ethereumAddressFromPublicKey(),
                    database.singleBlob(
                        "SELECT ethereumAddress FROM meta_accounts " +
                            "WHERE id = ?",
                        metaId
                    )
                )
            } finally {
                database.close()
            }
        }

    @Test
    fun db31SeparatedEthereumMutationCannotPublishStaleIdentity() =
        runBlocking {
            assertSeparatedRetrySnapshotRace(
                mutateEthereumSecret = true
            )
        }

    @Test
    fun db31SeparatedSubstrateMutationCannotPublishStaleIdentity() =
        runBlocking {
            assertSeparatedRetrySnapshotRace(
                mutateEthereumSecret = false
            )
        }

    @Test
    fun db31CorruptSeparatedPairQuarantineIsAtomicUnderEthereumRace() =
        runBlocking {
            val preferences = FailingDurableEncryptedPreferences()
            val database = createVersion31Database(preferences)
            val metaId = database.getMetaAccounts().single().id
            val substrateKey = "$metaId:SUBSTRATE_SECRETS"
            val ethereumKey = "$metaId:ETHEREUM_SECRETS"
            val substrateQuarantine =
                WalletSecretQuarantine.keyFor(substrateKey)
            val ethereumQuarantine =
                WalletSecretQuarantine.keyFor(ethereumKey)
            val oldEthereumKeypair = EthereumKeypairFactory.generate(
                seed = ETHEREUM_SEED,
                junctions = emptyList()
            )
            database.updateEthereumPublicKey(
                metaId = metaId,
                publicKey = oldEthereumKeypair.publicKey
            )
            installSeparatedRetrySecrets(preferences, metaId)
            val exactEthereum =
                preferences.rawSnapshot().getValue(ethereumKey)
            preferences.putEncryptedString(
                substrateKey,
                MALFORMED_SECRET
            )
            val concurrentEthereum = "concurrent-separated-ethereum"
            val durableWritesBeforeRace = preferences.durableWriteCount
            preferences.mutateOnceBeforeSnapshotBoundReplacement(
                sourceField = ethereumKey,
                replacement = concurrentEthereum
            )

            try {
                database.runMigrationTransactionExpectingFailure(
                    migration = EthereumDerivationPathMigration(preferences),
                    expectedFailure =
                    WalletSecretConcurrentMutationException::class.java
                )

                assertEquals(
                    durableWritesBeforeRace,
                    preferences.durableWriteCount
                )
                assertEquals(
                    MALFORMED_SECRET,
                    preferences.getDecryptedString(substrateKey)
                )
                assertEquals(
                    concurrentEthereum,
                    preferences.getDecryptedString(ethereumKey)
                )
                assertFalse(preferences.hasKey(substrateQuarantine))
                assertFalse(preferences.hasKey(ethereumQuarantine))
                assertArrayEquals(
                    oldEthereumKeypair.publicKey,
                    database.singleBlob(
                        "SELECT ethereumPublicKey FROM meta_accounts " +
                            "WHERE id = ?",
                        metaId
                    )
                )

                preferences.putEncryptedString(
                    ethereumKey,
                    exactEthereum
                )
                database.runMigrationTransaction(
                    EthereumDerivationPathMigration(preferences)
                )

                assertFalse(preferences.hasKey(substrateKey))
                assertFalse(preferences.hasKey(ethereumKey))
                assertEquals(
                    MALFORMED_SECRET,
                    preferences.getDecryptedString(substrateQuarantine)
                )
                assertEquals(
                    exactEthereum,
                    preferences.getDecryptedString(ethereumQuarantine)
                )
                assertEquals(
                    durableWritesBeforeRace + 1,
                    preferences.durableWriteCount
                )
                val exactAfterQuarantine = preferences.rawSnapshot()
                database.runMigrationTransaction(
                    EthereumDerivationPathMigration(preferences)
                )
                assertEquals(
                    exactAfterQuarantine,
                    preferences.rawSnapshot()
                )
                assertEquals(
                    durableWritesBeforeRace + 1,
                    preferences.durableWriteCount
                )
            } finally {
                database.close()
            }
        }

    @Test
    fun db31EthereumOnlyQuarantineBindsPairedSubstrateSnapshot() =
        runBlocking {
            val preferences = FailingDurableEncryptedPreferences()
            val database = createVersion31Database(preferences)
            val metaId = database.getMetaAccounts().single().id
            val substrateKey = "$metaId:SUBSTRATE_SECRETS"
            val ethereumKey = "$metaId:ETHEREUM_SECRETS"
            val substrateQuarantine =
                WalletSecretQuarantine.keyFor(substrateKey)
            val ethereumQuarantine =
                WalletSecretQuarantine.keyFor(ethereumKey)
            installSeparatedRetrySecrets(preferences, metaId)
            val exactSubstrate =
                preferences.rawSnapshot().getValue(substrateKey)
            preferences.putEncryptedString(
                ethereumKey,
                MALFORMED_SECRET
            )
            val concurrentSubstrate = "concurrent-separated-substrate"
            val durableWritesBeforeRace = preferences.durableWriteCount
            preferences.mutateOnceBeforeSnapshotBoundReplacement(
                sourceField = substrateKey,
                replacement = concurrentSubstrate
            )

            try {
                database.runMigrationTransactionExpectingFailure(
                    migration = EthereumDerivationPathMigration(preferences),
                    expectedFailure =
                        WalletSecretConcurrentMutationException::class.java
                )

                assertEquals(
                    durableWritesBeforeRace,
                    preferences.durableWriteCount
                )
                assertEquals(
                    concurrentSubstrate,
                    preferences.getDecryptedString(substrateKey)
                )
                assertEquals(
                    MALFORMED_SECRET,
                    preferences.getDecryptedString(ethereumKey)
                )
                assertFalse(preferences.hasKey(substrateQuarantine))
                assertFalse(preferences.hasKey(ethereumQuarantine))

                preferences.putEncryptedString(
                    substrateKey,
                    exactSubstrate
                )
                database.runMigrationTransaction(
                    EthereumDerivationPathMigration(preferences)
                )

                assertEquals(
                    exactSubstrate,
                    preferences.getDecryptedString(substrateKey)
                )
                assertFalse(preferences.hasKey(ethereumKey))
                assertFalse(preferences.hasKey(substrateQuarantine))
                assertEquals(
                    MALFORMED_SECRET,
                    preferences.getDecryptedString(ethereumQuarantine)
                )
                assertEquals(
                    durableWritesBeforeRace + 1,
                    preferences.durableWriteCount
                )
            } finally {
                database.close()
            }
        }

    @Test
    fun db31SimultaneousV2AndV3SecretsFailClosedBeforeAnyMutation() =
        runBlocking {
            val preferences = FailingDurableEncryptedPreferences()
            val database = createVersion31Database(preferences)
            val metaId = database.getMetaAccounts().single().id
            val accessKey = "$metaId:ACCESS_SECRETS"
            val exactAccess =
                preferences.rawSnapshot().getValue(accessKey)
            val oldEthereumKeypair = EthereumKeypairFactory.generate(
                seed = ETHEREUM_SEED,
                junctions = emptyList()
            )
            assertFalse(
                oldEthereumKeypair.publicKey.contentEquals(
                    ETHEREUM_KEYPAIR.publicKey
                )
            )
            val oldEthereumAddress =
                oldEthereumKeypair.publicKey.ethereumAddressFromPublicKey()
            database.updateEthereumPublicKey(
                metaId = metaId,
                publicKey = oldEthereumKeypair.publicKey
            )
            database.insertVersion31Asset(
                metaId = metaId,
                accountId = oldEthereumAddress
            )
            installSeparatedRetrySecrets(preferences, metaId)
            preferences.putEncryptedString(accessKey, exactAccess)
            val exactBefore = preferences.rawSnapshot()
            val durableWritesBefore = preferences.durableWriteCount
            val snapshotReplacementsBefore =
                preferences.snapshotBoundReplacementCount

            try {
                database.runMigrationTransactionExpectingFailure(
                    migration = EthereumDerivationPathMigration(preferences),
                    expectedFailure =
                        WalletSecretConcurrentMutationException::class.java
                )

                assertEquals(exactBefore, preferences.rawSnapshot())
                assertEquals(
                    durableWritesBefore,
                    preferences.durableWriteCount
                )
                assertEquals(
                    snapshotReplacementsBefore,
                    preferences.snapshotBoundReplacementCount
                )
                assertArrayEquals(
                    oldEthereumKeypair.publicKey,
                    database.singleBlob(
                        "SELECT ethereumPublicKey FROM meta_accounts " +
                            "WHERE id = ?",
                        metaId
                    )
                )
                assertArrayEquals(
                    oldEthereumAddress,
                    database.singleBlob(
                        "SELECT ethereumAddress FROM meta_accounts " +
                            "WHERE id = ?",
                        metaId
                    )
                )
                assertArrayEquals(
                    oldEthereumAddress,
                    database.singleBlob(
                        "SELECT accountId FROM assets WHERE metaId = ?",
                        metaId
                    )
                )

                preferences.removeKey(accessKey)
                database.runMigrationTransaction(
                    EthereumDerivationPathMigration(preferences)
                )
                val correctedAddress =
                    ETHEREUM_KEYPAIR.publicKey.ethereumAddressFromPublicKey()
                assertArrayEquals(
                    ETHEREUM_KEYPAIR.publicKey,
                    database.singleBlob(
                        "SELECT ethereumPublicKey FROM meta_accounts " +
                            "WHERE id = ?",
                        metaId
                    )
                )
                assertArrayEquals(
                    correctedAddress,
                    database.singleBlob(
                        "SELECT ethereumAddress FROM meta_accounts " +
                            "WHERE id = ?",
                        metaId
                    )
                )
                assertArrayEquals(
                    correctedAddress,
                    database.singleBlob(
                        "SELECT accountId FROM assets WHERE metaId = ?",
                        metaId
                    )
                )
            } finally {
                database.close()
            }
        }

    @Test
    fun version28LaterWrongStorageIdentityFailsBeforeAnyPreferenceMutation() =
        runBlocking {
            assertVersion28LaterIdentityFailurePreservesExactState(
                invalidPublicKey = ByteArray(32) { (it + 91).toByte() }
            )
        }

    @Test
    fun version28LaterOversizedIdentityFailsBeforeAnyPreferenceMutation() =
        runBlocking {
            assertVersion28LaterIdentityFailurePreservesExactState(
                invalidPublicKey = "a".repeat(ATTACKER_DB_IDENTITY_BYTES)
            )
        }

    @Test
    fun productionV2LimitsAccommodatePowerUsersWhileRemainingBounded() {
        assertEquals(
            4_096,
            V2MigrationLimits.PRODUCTION.maxAccountPlans
        )
        assertEquals(
            33_554_432L,
            V2MigrationLimits.PRODUCTION.maxRetainedSecretCharacters
        )
    }

    @Test
    fun version28InitialRowLimitsAcceptExactMetaAndChainBounds() =
        runBlocking {
            val preferences = FailingDurableEncryptedPreferences()
            val database = helper.createDatabase(TEST_DB, 28)
            val limits = V2MigrationLimits(
                maxAccountPlans = 1,
                maxRetainedSecretCharacters =
                    V2MigrationLimits.PRODUCTION.maxRetainedSecretCharacters
            )
            val rowLimits = WalletMigrationRowLimits(
                maxWalletRows = 1,
                maxChainAccountRows = 1
            )
            try {
                database.insertBoundedMetaAccount(metaId = 28_001L)
                database.insertBoundedChainAccount(
                    metaId = 28_001L,
                    chainId = "bounded-chain-exact"
                )
                val exactBefore = preferences.rawSnapshot()

                database.runMigrationTransaction(
                    V2Migration(
                        storeV1 = SecretStoreV1Impl(preferences),
                        encryptedPreferences = preferences,
                        limits = limits,
                        walletRowLimits = rowLimits
                    )
                )

                assertEquals(
                    1,
                    database.singleInt("SELECT COUNT(*) FROM meta_accounts")
                )
                assertEquals(
                    1,
                    database.singleInt("SELECT COUNT(*) FROM chain_accounts")
                )
                assertEquals(exactBefore, preferences.rawSnapshot())
            } finally {
                database.close()
            }
        }

    @Test
    fun version28InitialUserLimitPlusOneFailsBeforePreferenceAccess() =
        runBlocking {
            val preferences = FailingDurableEncryptedPreferences()
            val database = helper.createDatabase(TEST_DB, 28)
            val limits = V2MigrationLimits(
                maxAccountPlans = 1,
                maxRetainedSecretCharacters =
                    V2MigrationLimits.PRODUCTION.maxRetainedSecretCharacters
            )
            try {
                repeat(2) { index ->
                    database.insertRawLegacyAccount(
                        address = "initial-user-limit-$index",
                        publicKey = SUBSTRATE_KEYPAIR.publicKey.toPlainHexString(),
                        position = index
                    )
                }

                database.runMigrationTransactionExpectingFailure(
                    migration = V2Migration(
                        storeV1 = SecretStoreV1Impl(preferences),
                        encryptedPreferences = preferences,
                        limits = limits
                    ),
                    expectedFailure =
                        WalletPublicIdentityIntegrityException::class.java
                )

                assertEquals(0, preferences.preferenceInspectionCount)
                assertEquals(0, preferences.durableWriteCount)
                assertEquals(
                    0,
                    database.singleInt("SELECT COUNT(*) FROM meta_accounts")
                )
            } finally {
                database.close()
            }
        }

    @Test
    fun version28AttackerSizedPositionFailsBeforePreferenceAccess() {
        assertUnsafeLegacyUserIntegerFailsBeforePreferenceAccess { database, address ->
            database.execSQL(
                "UPDATE users SET position = zeroblob(?) WHERE address = ?",
                arrayOf(ATTACKER_DB_IDENTITY_BYTES, address)
            )
        }
    }

    @Test
    fun version28NegativePositionFailsBeforePreferenceAccess() {
        assertUnsafeLegacyUserIntegerFailsBeforePreferenceAccess { database, address ->
            database.execSQL(
                "UPDATE users SET position = -1 WHERE address = ?",
                arrayOf(address)
            )
        }
    }

    @Test
    fun version28OutOfIntRangePositionFailsBeforePreferenceAccess() {
        assertUnsafeLegacyUserIntegerFailsBeforePreferenceAccess { database, address ->
            database.execSQL(
                "UPDATE users SET position = ? WHERE address = ?",
                arrayOf(Int.MAX_VALUE.toLong() + 1L, address)
            )
        }
    }

    @Test
    fun version28AttackerSizedCryptoTypeFailsBeforePreferenceAccess() {
        assertUnsafeLegacyUserIntegerFailsBeforePreferenceAccess { database, address ->
            database.execSQL(
                "UPDATE users SET cryptoType = zeroblob(?) WHERE address = ?",
                arrayOf(ATTACKER_DB_IDENTITY_BYTES, address)
            )
        }
    }

    @Test
    fun version28InitialMetaLimitPlusOneFailsBeforePreferenceAccess() =
        runBlocking {
            val preferences = FailingDurableEncryptedPreferences()
            val database = helper.createDatabase(TEST_DB, 28)
            val rowLimits = WalletMigrationRowLimits(
                maxWalletRows = 1,
                maxChainAccountRows = 1
            )
            try {
                database.insertBoundedMetaAccount(metaId = 28_101L)
                database.insertBoundedMetaAccount(metaId = 28_102L)

                database.runMigrationTransactionExpectingFailure(
                    migration = V2Migration(
                        storeV1 = SecretStoreV1Impl(preferences),
                        encryptedPreferences = preferences,
                        limits = V2MigrationLimits(
                            maxAccountPlans = 1,
                            maxRetainedSecretCharacters =
                                V2MigrationLimits.PRODUCTION
                                    .maxRetainedSecretCharacters
                        ),
                        walletRowLimits = rowLimits
                    ),
                    expectedFailure =
                        WalletPublicIdentityIntegrityException::class.java
                )

                assertEquals(0, preferences.preferenceInspectionCount)
                assertEquals(0, preferences.durableWriteCount)
            } finally {
                database.close()
            }
        }

    @Test
    fun version28InitialChainLimitPlusOneFailsBeforePreferenceAccess() =
        runBlocking {
            val preferences = FailingDurableEncryptedPreferences()
            val database = helper.createDatabase(TEST_DB, 28)
            val rowLimits = WalletMigrationRowLimits(
                maxWalletRows = 1,
                maxChainAccountRows = 1
            )
            try {
                database.insertBoundedMetaAccount(metaId = 28_201L)
                database.insertBoundedChainAccount(
                    metaId = 28_201L,
                    chainId = "bounded-chain-first"
                )
                database.insertBoundedChainAccount(
                    metaId = 28_201L,
                    chainId = "bounded-chain-second"
                )

                database.runMigrationTransactionExpectingFailure(
                    migration = V2Migration(
                        storeV1 = SecretStoreV1Impl(preferences),
                        encryptedPreferences = preferences,
                        limits = V2MigrationLimits(
                            maxAccountPlans = 1,
                            maxRetainedSecretCharacters =
                                V2MigrationLimits.PRODUCTION
                                    .maxRetainedSecretCharacters
                        ),
                        walletRowLimits = rowLimits
                    ),
                    expectedFailure =
                        WalletPublicIdentityIntegrityException::class.java
                )

                assertEquals(0, preferences.preferenceInspectionCount)
                assertEquals(0, preferences.durableWriteCount)
            } finally {
                database.close()
            }
        }

    @Test
    fun version28WalletPlanLimitFailsBeforeAnyPreferenceMutation() =
        runBlocking {
            val preferences = FailingDurableEncryptedPreferences()
            val database = helper.createDatabase(TEST_DB, 28)
            val limits = V2MigrationLimits(
                maxAccountPlans = 256,
                maxRetainedSecretCharacters =
                    V2MigrationLimits.PRODUCTION
                        .maxRetainedSecretCharacters
            )
            try {
                repeat(257) { index ->
                    database.insertRawLegacyAccount(
                        address = "bounded-watch-only-$index",
                        publicKey = SUBSTRATE_KEYPAIR.publicKey.toPlainHexString(),
                        position = index
                    )
                }
                val exactBefore = preferences.rawSnapshot()

                repeat(2) {
                    database.runMigrationTransactionExpectingFailure(
                        migration = V2Migration(
                            SecretStoreV1Impl(preferences),
                            preferences,
                            limits
                        ),
                        expectedFailure =
                        WalletPublicIdentityIntegrityException::class.java
                    )
                    assertEquals(28, database.version)
                    assertEquals(
                        0,
                        database.singleInt(
                            "SELECT COUNT(*) FROM meta_accounts"
                        )
                    )
                    assertEquals(exactBefore, preferences.rawSnapshot())
                    assertEquals(0, preferences.preferenceInspectionCount)
                }

                database.execSQL(
                    "DELETE FROM users WHERE address = ?",
                    arrayOf("bounded-watch-only-256")
                )
                database.runMigrationTransaction(
                    V2Migration(
                        SecretStoreV1Impl(preferences),
                        preferences,
                        limits
                    )
                )
                assertEquals(exactBefore, preferences.rawSnapshot())
            } finally {
                database.close()
            }
        }

    @Test
    fun version28PreparedSecretBudgetFailsBeforeAnyPreferenceMutation() =
        runBlocking {
            val preferences = FailingDurableEncryptedPreferences()
            val database = helper.createDatabase(TEST_DB, 28)
            val retainedSecretLimit = 1_048_576L
            val limits = V2MigrationLimits(
                maxAccountPlans =
                    V2MigrationLimits.PRODUCTION.maxAccountPlans,
                maxRetainedSecretCharacters = retainedSecretLimit
            )
            val oversizedRetainedSecret = "x".repeat(
                (retainedSecretLimit / 2L + 1L).toInt()
            )
            try {
                repeat(2) { index ->
                    val address = "bounded-secret-$index"
                    database.insertRawLegacyAccount(
                        address = address,
                        publicKey = SUBSTRATE_KEYPAIR.publicKey.toPlainHexString(),
                        position = index
                    )
                    preferences.putEncryptedString(
                        legacyV1SecretKey(address),
                        oversizedRetainedSecret
                    )
                }
                val exactBefore = preferences.rawSnapshot()

                database.runMigrationTransactionExpectingFailure(
                    migration = V2Migration(
                        SecretStoreV1Impl(preferences),
                        preferences,
                        limits
                    ),
                    expectedFailure =
                    WalletPublicIdentityIntegrityException::class.java
                )
                assertEquals(28, database.version)
                assertEquals(
                    0,
                    database.singleInt("SELECT COUNT(*) FROM meta_accounts")
                )
                assertEquals(exactBefore, preferences.rawSnapshot())

                database.execSQL(
                    "DELETE FROM users WHERE address = ?",
                    arrayOf("bounded-secret-1")
                )
                preferences.removeKey(
                    legacyV1SecretKey("bounded-secret-1")
                )
                database.runMigrationTransaction(
                    V2Migration(
                        SecretStoreV1Impl(preferences),
                        preferences,
                        limits
                    )
                )
                assertTrue(
                    preferences.hasKey(
                        legacyV1QuarantineKey(
                            publicKey = SUBSTRATE_KEYPAIR.publicKey
                        )
                    )
                )
            } finally {
                database.close()
            }
        }

    @Test
    fun version28ConcurrentSourceMutationCannotPublishStalePreparedSecret() =
        runBlocking {
            val preferences = FailingDurableEncryptedPreferences()
            val oldStore = SecretStoreV1Impl(preferences)
            oldStore.insertSecrets(Type.MNEMONIC)
            val address = SUBSTRATE_KEYPAIR.publicKey.toAddress(0)
            val sourceKey = legacyV1SecretKey(address)
            val originalSource = preferences.rawSnapshot().getValue(sourceKey)
            val database = helper.createDatabase(TEST_DB, 28).apply {
                insertAccount(
                    publicKey = SUBSTRATE_KEYPAIR.publicKey,
                    encryptionType = CRYPTO_TYPE
                )
            }
            val triggerMarker = WalletPublicIdentityRecovery.keyFor(
                metaId = 1L,
                activeSecretKey = "1:ACCESS_SECRETS"
            )
            preferences.mutateOnceWhenHasKey(
                triggerField = triggerMarker,
                sourceField = sourceKey,
                replacement = MALFORMED_SECRET
            )

            try {
                database.runMigrationTransactionExpectingFailure(
                    migration = V2Migration(oldStore, preferences),
                    expectedFailure =
                    WalletSecretConcurrentMutationException::class.java
                )
                assertEquals(28, database.version)
                assertEquals(
                    0,
                    database.singleInt("SELECT COUNT(*) FROM meta_accounts")
                )
                assertEquals(
                    MALFORMED_SECRET,
                    preferences.getDecryptedString(sourceKey)
                )
                assertFalse(preferences.hasKey("1:ACCESS_SECRETS"))

                preferences.putEncryptedString(sourceKey, originalSource)
                database.runMigrationTransaction(
                    V2Migration(oldStore, preferences)
                )
                assertNotNull(
                    preferences.getDecryptedString("1:ACCESS_SECRETS")
                )
            } finally {
                database.close()
            }
        }

    @Test
    fun version28SecondWalletRaceCannotPartiallyPublishFirstWallet() =
        runBlocking {
            val preferences = FailingDurableEncryptedPreferences()
            val oldStore = SecretStoreV1Impl(preferences)
            oldStore.insertSecrets(Type.MNEMONIC)
            val firstAddress = SUBSTRATE_KEYPAIR.publicKey.toAddress(0)
            val firstSourceKey = legacyV1SecretKey(firstAddress)
            val firstSource =
                preferences.rawSnapshot().getValue(firstSourceKey)
            val secondSeed = ByteArray(32) { (it + 71).toByte() }
            val secondKeypair = SubstrateKeypairFactory.generate(
                encryptionType = CRYPTO_TYPE,
                seed = secondSeed,
                junctions = DECODED_SUBSTRATE_DERIVATION_PATH.junctions
            )
            val secondAddress = secondKeypair.publicKey.toAddress(0)
            val secondSourceKey = legacyV1SecretKey(secondAddress)
            oldStore.saveSecuritySource(
                secondAddress,
                SecuritySource.Specified.Seed(
                    seed = secondSeed,
                    keypair = secondKeypair,
                    derivationPath = DERIVATION_PATH
                )
            )
            val secondSource =
                preferences.rawSnapshot().getValue(secondSourceKey)
            val database = helper.createDatabase(TEST_DB, 28).apply {
                insertAccount(
                    publicKey = SUBSTRATE_KEYPAIR.publicKey,
                    encryptionType = CRYPTO_TYPE,
                    position = 0,
                    username = "first"
                )
                insertAccount(
                    publicKey = secondKeypair.publicKey,
                    encryptionType = CRYPTO_TYPE,
                    position = 1,
                    username = "second"
                )
            }
            preferences.mutateOnceBeforeSnapshotBoundReplacement(
                sourceField = secondSourceKey,
                replacement = MALFORMED_SECRET
            )

            try {
                database.runMigrationTransactionExpectingFailure(
                    migration = V2Migration(oldStore, preferences),
                    expectedFailure =
                    WalletSecretConcurrentMutationException::class.java
                )

                assertEquals(28, database.version)
                assertEquals(
                    0,
                    database.singleInt("SELECT COUNT(*) FROM meta_accounts")
                )
                assertFalse(preferences.hasKey("1:ACCESS_SECRETS"))
                assertFalse(preferences.hasKey("2:ACCESS_SECRETS"))
                assertEquals(
                    firstSource,
                    preferences.getDecryptedString(firstSourceKey)
                )
                assertEquals(
                    MALFORMED_SECRET,
                    preferences.getDecryptedString(secondSourceKey)
                )
                assertEquals(0, preferences.durableWriteCount)

                preferences.putEncryptedString(
                    secondSourceKey,
                    secondSource
                )
                database.runMigrationTransaction(
                    V2Migration(oldStore, preferences)
                )

                assertNotNull(
                    preferences.getDecryptedString("1:ACCESS_SECRETS")
                )
                assertNotNull(
                    preferences.getDecryptedString("2:ACCESS_SECRETS")
                )
                assertEquals(1, preferences.durableWriteCount)
            } finally {
                database.close()
            }
        }

    @Test
    fun version28ConcurrentTargetCreationCannotBeOverwritten() =
        runBlocking {
            val preferences = FailingDurableEncryptedPreferences()
            val oldStore = SecretStoreV1Impl(preferences)
            oldStore.insertSecrets(Type.MNEMONIC)
            val address = SUBSTRATE_KEYPAIR.publicKey.toAddress(0)
            val sourceKey = legacyV1SecretKey(address)
            val originalSource =
                preferences.rawSnapshot().getValue(sourceKey)
            val targetKey = "1:ACCESS_SECRETS"
            val concurrentTarget =
                "concurrently-created-wallet-target"
            val database = helper.createDatabase(TEST_DB, 28).apply {
                insertAccount(
                    publicKey = SUBSTRATE_KEYPAIR.publicKey,
                    encryptionType = CRYPTO_TYPE
                )
            }
            preferences.mutateOnceWhenHasKey(
                triggerField = WalletPublicIdentityRecovery.keyFor(
                    metaId = 1L,
                    activeSecretKey = targetKey
                ),
                sourceField = targetKey,
                replacement = concurrentTarget
            )

            try {
                database.runMigrationTransactionExpectingFailure(
                    migration = V2Migration(oldStore, preferences),
                    expectedFailure =
                    WalletSecretConcurrentMutationException::class.java
                )
                assertEquals(28, database.version)
                assertEquals(
                    0,
                    database.singleInt("SELECT COUNT(*) FROM meta_accounts")
                )
                assertEquals(
                    concurrentTarget,
                    preferences.getDecryptedString(targetKey)
                )
                assertEquals(
                    originalSource,
                    preferences.getDecryptedString(sourceKey)
                )

                preferences.removeKey(targetKey)
                database.runMigrationTransaction(
                    V2Migration(oldStore, preferences)
                )
                assertNotNull(
                    preferences.getDecryptedString(targetKey)
                )
            } finally {
                database.close()
            }
        }

    @Test
    fun version28ConcurrentV04AncillaryAdditionCannotPublishStalePreparedSecret() =
        runBlocking {
            val preferences = FailingDurableEncryptedPreferences()
            val keypairWithoutDerivation = SubstrateKeypairFactory.generate(
                encryptionType = CRYPTO_TYPE,
                seed = SUBSTRATE_SEED,
                junctions = emptyList()
            )
            val address = keypairWithoutDerivation.publicKey.toAddress(0)
            insertV04Secrets(
                preferences = preferences,
                address = address,
                keypair = keypairWithoutDerivation,
                seed = SUBSTRATE_SEED,
                entropy = null,
                derivationPath = null
            )
            val derivationKey = "derivation_$address"
            val database = helper.createDatabase(TEST_DB, 28).apply {
                insertAccount(
                    publicKey = keypairWithoutDerivation.publicKey,
                    encryptionType = CRYPTO_TYPE
                )
            }
            preferences.mutateOnceBeforeSnapshotBoundReplacement(
                sourceField = derivationKey,
                replacement = DERIVATION_PATH
            )

            try {
                database.runMigrationTransactionExpectingFailure(
                    migration = V2Migration(
                        SecretStoreV1Impl(preferences),
                        preferences
                    ),
                    expectedFailure =
                    WalletSecretConcurrentMutationException::class.java
                )
                assertEquals(28, database.version)
                assertEquals(
                    0,
                    database.singleInt("SELECT COUNT(*) FROM meta_accounts")
                )
                assertEquals(
                    DERIVATION_PATH,
                    preferences.getDecryptedString(derivationKey)
                )
                assertFalse(preferences.hasKey("1:ACCESS_SECRETS"))

                preferences.removeKey(derivationKey)
                database.runMigrationTransaction(
                    V2Migration(SecretStoreV1Impl(preferences), preferences)
                )
                assertNotNull(
                    preferences.getDecryptedString("1:ACCESS_SECRETS")
                )
            } finally {
                database.close()
            }
        }

    @Test
    fun version28CorruptV1MutationCannotSplitFallbackPublishAndQuarantine() =
        runBlocking {
            val preferences = FailingDurableEncryptedPreferences()
            val address = SUBSTRATE_KEYPAIR.publicKey.toAddress(0)
            val legacyV1Key = legacyV1SecretKey(address)
            val concurrentReplacement = "$MALFORMED_SECRET-concurrent"
            preferences.putEncryptedString(
                legacyV1Key,
                MALFORMED_SECRET
            )
            val exactV04Values = insertV04Secrets(
                preferences = preferences,
                address = address,
                keypair = SUBSTRATE_KEYPAIR,
                seed = SUBSTRATE_SEED,
                entropy = MNEMONIC.entropy,
                derivationPath = DERIVATION_PATH
            )
            val database = helper.createDatabase(TEST_DB, 28).apply {
                insertAccount(
                    publicKey = SUBSTRATE_KEYPAIR.publicKey,
                    encryptionType = CRYPTO_TYPE
                )
            }
            preferences.mutateOnceBeforeSnapshotBoundReplacement(
                sourceField = legacyV1Key,
                replacement = concurrentReplacement
            )

            try {
                database.runMigrationTransactionExpectingFailure(
                    migration = V2Migration(
                        SecretStoreV1Impl(preferences),
                        preferences
                    ),
                    expectedFailure =
                    WalletSecretConcurrentMutationException::class.java
                )

                assertEquals(28, database.version)
                assertEquals(
                    0,
                    database.singleInt("SELECT COUNT(*) FROM meta_accounts")
                )
                assertEquals(
                    concurrentReplacement,
                    preferences.getDecryptedString(legacyV1Key)
                )
                assertFalse(preferences.hasKey("1:ACCESS_SECRETS"))
                assertFalse(
                    preferences.hasKey(
                        WalletSecretQuarantine.keyFor(legacyV1Key)
                    )
                )
                exactV04Values.forEach { (key, value) ->
                    assertEquals(
                        value,
                        preferences.getDecryptedString(key)
                    )
                }
                assertEquals(0, preferences.durableWriteCount)

                preferences.putEncryptedString(
                    legacyV1Key,
                    MALFORMED_SECRET
                )
                database.runMigrationTransaction(
                    V2Migration(
                        SecretStoreV1Impl(preferences),
                        preferences
                    )
                )

                assertNotNull(
                    preferences.getDecryptedString("1:ACCESS_SECRETS")
                )
                assertFalse(preferences.hasKey(legacyV1Key))
                assertEquals(
                    MALFORMED_SECRET,
                    preferences.getDecryptedString(
                        WalletSecretQuarantine.keyFor(legacyV1Key)
                    )
                )
                assertEquals(1, preferences.durableWriteCount)
            } finally {
                database.close()
            }
        }

    @Test
    fun version28ConflictingExistingTargetFailsWithoutOverwrite() =
        runBlocking {
            val preferences = FailingDurableEncryptedPreferences()
            val oldStore = SecretStoreV1Impl(preferences)
            oldStore.insertSecrets(Type.MNEMONIC)
            preferences.putEncryptedString(
                "1:ACCESS_SECRETS",
                "conflicting-concurrently-created-target"
            )
            val exactBefore = preferences.rawSnapshot()
            val database = helper.createDatabase(TEST_DB, 28).apply {
                insertAccount(
                    publicKey = SUBSTRATE_KEYPAIR.publicKey,
                    encryptionType = CRYPTO_TYPE
                )
            }

            try {
                database.runMigrationTransactionExpectingFailure(
                    migration = V2Migration(oldStore, preferences),
                    expectedFailure =
                    WalletSecretConcurrentMutationException::class.java
                )
                assertEquals(28, database.version)
                assertEquals(exactBefore, preferences.rawSnapshot())
                assertEquals(
                    0,
                    database.singleInt("SELECT COUNT(*) FROM meta_accounts")
                )

                preferences.removeKey("1:ACCESS_SECRETS")
                database.runMigrationTransaction(
                    V2Migration(oldStore, preferences)
                )
                assertNotNull(
                    preferences.getDecryptedString("1:ACCESS_SECRETS")
                )
            } finally {
                database.close()
            }
        }

    @Test
    fun version28PartialSeparatedRetryFailsBeforeAnyPreferenceWrite() =
        runBlocking {
            assertVersion28SeparatedStateRejectedWithoutMutation {
                SubstrateSecretStore(it).put(
                    metaId = 1L,
                    secrets = expectedSubstrateSecrets()
                )
            }
        }

    @Test
    fun version28ConflictingSeparatedRetryFailsBeforeAnyPreferenceWrite() =
        runBlocking {
            assertVersion28SeparatedStateRejectedWithoutMutation {
                installSeparatedRetrySecrets(it, metaId = 1L)
                it.putEncryptedString(
                    "1:ETHEREUM_SECRETS",
                    "conflicting-separated-wallet-material"
                )
            }
        }

    @Test
    fun version28MixedAccessAndSeparatedRetryFailsBeforeAnyPreferenceWrite() =
        runBlocking {
            assertVersion28SeparatedStateRejectedWithoutMutation {
                installSeparatedRetrySecrets(it, metaId = 1L)
                SecretStoreV2(it).putMetaAccountSecrets(
                    metaId = 1L,
                    secrets = expectedV2Secrets()
                )
            }
        }

    @Test
    fun version28ConcurrentSeparatedCreationRejectsTheWholeRootSecretCas() =
        runBlocking {
            val preferences = FailingDurableEncryptedPreferences()
            val oldStore = SecretStoreV1Impl(preferences)
            oldStore.insertSecrets(Type.MNEMONIC)
            val sourceKey =
                legacyV1SecretKey(SUBSTRATE_KEYPAIR.publicKey.toAddress(0))
            val exactSource =
                preferences.rawSnapshot().getValue(sourceKey)
            val substrateKey = "1:SUBSTRATE_SECRETS"
            val concurrentValue = "concurrently-created-separated-secret"
            val database = helper.createDatabase(TEST_DB, 28).apply {
                insertAccount(
                    publicKey = SUBSTRATE_KEYPAIR.publicKey,
                    encryptionType = CRYPTO_TYPE
                )
            }
            preferences.mutateOnceBeforeSnapshotBoundReplacement(
                sourceField = substrateKey,
                replacement = concurrentValue
            )

            try {
                database.runMigrationTransactionExpectingFailure(
                    migration = V2Migration(oldStore, preferences),
                    expectedFailure =
                    WalletSecretConcurrentMutationException::class.java
                )
                assertEquals(28, database.version)
                assertEquals(
                    0,
                    database.singleInt("SELECT COUNT(*) FROM meta_accounts")
                )
                assertEquals(
                    exactSource,
                    preferences.getDecryptedString(sourceKey)
                )
                assertEquals(
                    concurrentValue,
                    preferences.getDecryptedString(substrateKey)
                )
                assertFalse(preferences.hasKey("1:ACCESS_SECRETS"))
                assertEquals(0, preferences.durableWriteCount)
                assertEquals(
                    1,
                    preferences.snapshotBoundReplacementCount
                )
            } finally {
                database.close()
            }
        }

    @Test
    fun version31InvalidDownstreamMarkerFailsBeforeEthereumPreferenceMutation() =
        runBlocking {
            val preferences = FailingDurableEncryptedPreferences()
            val database = createVersion31Database(preferences)
            val metaId = database.getMetaAccounts().single().id
            val invalidMarkerKey = WalletPublicIdentityRecovery.keyFor(
                metaId = metaId,
                activeSecretKey = "$metaId:TON_SECRETS"
            )
            preferences.putEncryptedString(
                invalidMarkerKey,
                "invalid-recovery-protocol-value"
            )
            val exactBefore = preferences.rawSnapshot()

            repeat(2) {
                database.runMigrationTransactionExpectingFailure(
                    migration = EthereumDerivationPathMigration(preferences),
                    expectedFailure = IllegalStateException::class.java
                )
                assertEquals(31, database.version)
                assertEquals(exactBefore, preferences.rawSnapshot())
            }

            preferences.removeKey(invalidMarkerKey)
            database.runMigrationTransaction(
                EthereumDerivationPathMigration(preferences)
            )
            val exactAfterRecovery = preferences.rawSnapshot()
            database.runMigrationTransaction(
                EthereumDerivationPathMigration(preferences)
            )
            assertEquals(exactAfterRecovery, preferences.rawSnapshot())
            database.close()
        }

    @Test
    fun db31PartialEthereumIdentityMarksLegacyV2WithoutMutatingCiphertext() =
        runBlocking {
            val preferences = FailingDurableEncryptedPreferences()
            val database = createVersion31Database(preferences)
            val metaId = database.getMetaAccounts().single().id
            val activeKey = "$metaId:ACCESS_SECRETS"
            val markerKey = WalletPublicIdentityRecovery.keyFor(
                metaId = metaId,
                activeSecretKey = activeKey
            )
            val exactPublicKey = database.singleBlob(
                "SELECT ethereumPublicKey FROM meta_accounts WHERE id = ?",
                metaId
            )
            database.execSQL(
                "UPDATE meta_accounts SET ethereumAddress = NULL WHERE id = ?",
                arrayOf(metaId)
            )
            val exactBefore = preferences.rawSnapshot()

            preferences.failDurableWrites = true
            database.runMigrationTransactionExpectingFailure(
                migration = EthereumDerivationPathMigration(preferences)
            )

            assertEquals(exactBefore, preferences.rawSnapshot())
            assertArrayEquals(
                exactPublicKey,
                database.singleBlob(
                    "SELECT ethereumPublicKey FROM meta_accounts WHERE id = ?",
                    metaId
                )
            )
            assertTrue(
                database.singleColumnIsNull(
                    "SELECT ethereumAddress FROM meta_accounts WHERE id = ?",
                    metaId
                )
            )

            preferences.failDurableWrites = false
            database.runMigrationTransaction(
                EthereumDerivationPathMigration(preferences)
            )

            assertEquals(
                exactBefore + (
                    markerKey to
                        WalletPublicIdentityRecovery.MARKER_VALUE
                    ),
                preferences.rawSnapshot()
            )
            assertEquals(
                exactBefore.getValue(activeKey),
                preferences.getDecryptedString(activeKey)
            )
            assertFalse(
                preferences.hasKey(WalletSecretQuarantine.keyFor(activeKey))
            )
            assertArrayEquals(
                exactPublicKey,
                database.singleBlob(
                    "SELECT ethereumPublicKey FROM meta_accounts WHERE id = ?",
                    metaId
                )
            )
            assertTrue(
                database.singleColumnIsNull(
                    "SELECT ethereumAddress FROM meta_accounts WHERE id = ?",
                    metaId
                )
            )

            val durableWritesAfterRecovery =
                preferences.durableWriteCount
            database.runMigrationTransaction(
                EthereumDerivationPathMigration(preferences)
            )
            assertEquals(
                durableWritesAfterRecovery,
                preferences.durableWriteCount
            )
            assertEquals(
                exactBefore + (
                    markerKey to
                        WalletPublicIdentityRecovery.MARKER_VALUE
                    ),
                preferences.rawSnapshot()
            )
            database.close()
        }

    @Test
    fun db31OffCurveEthereumIdentityMarksRecoveryAndRetriesIdempotently() =
        runBlocking {
            val preferences = FailingDurableEncryptedPreferences()
            val database = createVersion31Database(preferences)
            val metaId = database.getMetaAccounts().single().id
            val activeKey = "$metaId:ACCESS_SECRETS"
            val markerKey = WalletPublicIdentityRecovery.keyFor(
                metaId = metaId,
                activeSecretKey = activeKey
            )
            val offCurvePublicKey =
                byteArrayOf(0x02) + ByteArray(32) { 0xFF.toByte() }
            val exactAddress =
                ETHEREUM_KEYPAIR.publicKey.ethereumAddressFromPublicKey()
            database.execSQL(
                "UPDATE meta_accounts SET ethereumPublicKey = ?, " +
                    "ethereumAddress = ? WHERE id = ?",
                arrayOf(offCurvePublicKey, exactAddress, metaId)
            )
            val exactBefore = preferences.rawSnapshot()

            try {
                database.runMigrationTransaction(
                    EthereumDerivationPathMigration(preferences)
                )

                assertEquals(
                    exactBefore + (
                        markerKey to
                            WalletPublicIdentityRecovery.MARKER_VALUE
                        ),
                    preferences.rawSnapshot()
                )
                assertEquals(
                    exactBefore.getValue(activeKey),
                    preferences.getDecryptedString(activeKey)
                )
                assertFalse(
                    preferences.hasKey(
                        WalletSecretQuarantine.keyFor(activeKey)
                    )
                )
                assertArrayEquals(
                    offCurvePublicKey,
                    database.singleBlob(
                        "SELECT ethereumPublicKey FROM meta_accounts WHERE id = ?",
                        metaId
                    )
                )

                val durableWritesAfterRecovery =
                    preferences.durableWriteCount
                database.runMigrationTransaction(
                    EthereumDerivationPathMigration(preferences)
                )
                assertEquals(
                    durableWritesAfterRecovery,
                    preferences.durableWriteCount
                )
                assertEquals(
                    exactBefore + (
                        markerKey to
                            WalletPublicIdentityRecovery.MARKER_VALUE
                        ),
                    preferences.rawSnapshot()
                )
            } finally {
                database.close()
            }
        }

    @Test
    fun db31MultibyteTextIdentityAndMalformedPayloadPreserveCiphertextAcrossMarkerRetry() =
        runBlocking {
            val preferences = FailingDurableEncryptedPreferences()
            val database = createVersion31Database(preferences)
            val metaId = database.getMetaAccounts().single().id
            val activeKey = "$metaId:ACCESS_SECRETS"
            val markerKey = WalletPublicIdentityRecovery.keyFor(
                metaId = metaId,
                activeSecretKey = activeKey
            )
            preferences.putEncryptedString(activeKey, MALFORMED_SECRET)
            database.execSQL(
                "UPDATE meta_accounts SET substratePublicKey = ? WHERE id = ?",
                arrayOf("\u00E9".repeat(32), metaId)
            )
            val exactBefore = preferences.rawSnapshot()

            preferences.failDurableWrites = true
            database.runMigrationTransactionExpectingFailure(
                migration = EthereumDerivationPathMigration(preferences)
            )

            assertEquals(exactBefore, preferences.rawSnapshot())
            assertEquals(
                "text",
                database.singleString(
                    "SELECT typeof(substratePublicKey) " +
                        "FROM meta_accounts WHERE id = ?",
                    metaId
                )
            )

            preferences.failDurableWrites = false
            database.runMigrationTransaction(
                EthereumDerivationPathMigration(preferences)
            )

            assertEquals(MALFORMED_SECRET, preferences.getDecryptedString(activeKey))
            assertFalse(
                preferences.hasKey(WalletSecretQuarantine.keyFor(activeKey))
            )
            assertEquals(
                WalletPublicIdentityRecovery.MARKER_VALUE,
                preferences.getDecryptedString(markerKey)
            )
            val writesAfterRecovery = preferences.durableWriteCount

            database.runMigrationTransaction(
                EthereumDerivationPathMigration(preferences)
            )
            assertEquals(writesAfterRecovery, preferences.durableWriteCount)
            assertEquals(MALFORMED_SECRET, preferences.getDecryptedString(activeKey))
            database.close()
        }

    @Test
    fun db31OversizedSubstrateIdentityMarksLegacyV2WithoutBlobRead() =
        runBlocking {
            val preferences = FailingDurableEncryptedPreferences()
            val database = createVersion31Database(preferences)
            val metaId = database.getMetaAccounts().single().id
            val activeKey = "$metaId:ACCESS_SECRETS"
            database.execSQL(
                "UPDATE meta_accounts SET substratePublicKey = zeroblob(?) " +
                    "WHERE id = ?",
                arrayOf(ATTACKER_DB_IDENTITY_BYTES, metaId)
            )
            val exactBefore = preferences.rawSnapshot()

            database.runMigrationTransaction(
                EthereumDerivationPathMigration(preferences)
            )

            assertEquals(
                ATTACKER_DB_IDENTITY_BYTES.toLong(),
                database.singleLong(
                    "SELECT length(substratePublicKey) " +
                        "FROM meta_accounts WHERE id = ?",
                    metaId
                )
            )
            assertRecoveryMarkersOnly(
                preferences = preferences,
                exactBefore = exactBefore,
                metaId = metaId,
                activeSecretKeys = listOf(activeKey)
            )
            database.close()
        }

    @Test
    fun db31InvalidSeparatedCryptoTypeMarksBothSecretsWithoutQuarantine() =
        runBlocking {
            val preferences = FailingDurableEncryptedPreferences()
            val database = createVersion31Database(preferences)
            val metaId = database.getMetaAccounts().single().id
            installSeparatedRetrySecrets(preferences, metaId)
            database.execSQL(
                "UPDATE meta_accounts SET substrateCryptoType = ? " +
                    "WHERE id = ?",
                arrayOf("NOT_A_CRYPTO_TYPE", metaId)
            )
            val activeKeys = listOf(
                "$metaId:SUBSTRATE_SECRETS",
                "$metaId:ETHEREUM_SECRETS"
            )
            val exactBefore = preferences.rawSnapshot()

            database.runMigrationTransaction(
                EthereumDerivationPathMigration(preferences)
            )

            assertEquals(
                "NOT_A_CRYPTO_TYPE",
                database.singleString(
                    "SELECT substrateCryptoType FROM meta_accounts " +
                        "WHERE id = ?",
                    metaId
                )
            )
            assertRecoveryMarkersOnly(
                preferences = preferences,
                exactBefore = exactBefore,
                metaId = metaId,
                activeSecretKeys = activeKeys
            )
            database.close()
        }

    @Test
    fun db31OversizedSeparatedIdentityMarksBothSecretsWithoutBlobRead() =
        runBlocking {
            val preferences = FailingDurableEncryptedPreferences()
            val database = createVersion31Database(preferences)
            val metaId = database.getMetaAccounts().single().id
            installSeparatedRetrySecrets(preferences, metaId)
            database.execSQL(
                "UPDATE meta_accounts SET substratePublicKey = zeroblob(?) " +
                    "WHERE id = ?",
                arrayOf(ATTACKER_DB_IDENTITY_BYTES, metaId)
            )
            val activeKeys = listOf(
                "$metaId:SUBSTRATE_SECRETS",
                "$metaId:ETHEREUM_SECRETS"
            )
            val exactBefore = preferences.rawSnapshot()

            database.runMigrationTransaction(
                EthereumDerivationPathMigration(preferences)
            )

            assertEquals(
                ATTACKER_DB_IDENTITY_BYTES.toLong(),
                database.singleLong(
                    "SELECT length(substratePublicKey) " +
                        "FROM meta_accounts WHERE id = ?",
                    metaId
                )
            )
            assertRecoveryMarkersOnly(
                preferences = preferences,
                exactBefore = exactBefore,
                metaId = metaId,
                activeSecretKeys = activeKeys
            )
            database.close()
        }

    private suspend fun assertVersion28LaterIdentityFailurePreservesExactState(
        invalidPublicKey: Any
    ) {
        val preferences = FailingDurableEncryptedPreferences()
        val oldStore = SecretStoreV1Impl(preferences)
        oldStore.insertSecrets(Type.MNEMONIC)
        val invalidKeypair = distinctSubstrateKeypair(91)
        val database = helper.createDatabase(TEST_DB, 28).apply {
            insertAccount(
                publicKey = SUBSTRATE_KEYPAIR.publicKey,
                encryptionType = CRYPTO_TYPE
            )
            execSQL(
                """
                INSERT INTO users(
                    address,
                    publicKey,
                    cryptoType,
                    position,
                    networkType,
                    username
                ) VALUES(?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any>(
                    invalidKeypair.publicKey.toAddress(0),
                    invalidPublicKey,
                    mapEncryptionToCryptoType(CRYPTO_TYPE).ordinal,
                    1,
                    0,
                    "Invalid later identity"
                )
            )
        }
        val exactBefore = preferences.rawSnapshot()

        try {
            repeat(2) {
                database.runMigrationTransactionExpectingFailure(
                    migration = V2Migration(oldStore, preferences),
                    expectedFailure =
                    WalletPublicIdentityIntegrityException::class.java
                )
                assertEquals(28, database.version)
                assertEquals(
                    0,
                    database.singleInt("SELECT COUNT(*) FROM meta_accounts")
                )
                assertEquals(exactBefore, preferences.rawSnapshot())
            }

            database.execSQL("DELETE FROM users WHERE position = 1")
        } finally {
            database.close()
        }

        fun openCurrentDatabase(): AppDatabase = AppDatabase.create(
            context =
            InstrumentationRegistry.getInstrumentation().targetContext,
            databaseName = TEST_DB,
            storeV1 = oldStore,
            storeV2 = SecretStoreV2(preferences),
            encryptedPreferences = preferences,
            substrateSecretStore = SubstrateSecretStore(preferences),
            ethereumSecretStore = EthereumSecretStore(preferences)
        ).also {
            assertEquals(77, it.openHelper.writableDatabase.version)
        }

        openCurrentDatabase().close()
        val exactAfterRecovery = preferences.rawSnapshot()
        openCurrentDatabase().close()
        assertEquals(exactAfterRecovery, preferences.rawSnapshot())
    }

    private suspend fun performSingleAccountTest(
        insertionType: Type,
        withEntropy: Boolean,
        withEthereum: Boolean,
        withSeed: Boolean,
        withDerivationPath: Boolean
    ) {
        storeV1.insertSecrets(insertionType)

        val db = performMigration {
            insertAccount(publicKey = SUBSTRATE_KEYPAIR.publicKey, CRYPTO_TYPE)
        }

        try {
            val metaAccount = db.getMetaAccounts().firstOrNull()
                ?: error("There should be at least one account after migration")

            assertCorrectMetaAccount(metaAccount, withEthereum = withEthereum, selected = true)
            assertCorrectSecrets(
                metaAccountSecrets = storeV2.getMetaAccountSecrets(metaAccount.id),
                withEthereum = withEthereum,
                withEntropy = withEntropy,
                withSeed = withSeed,
                withDerivationPath = withDerivationPath
            )
        } finally {
            db.close()
        }
    }

    private fun performMigration(oldDbBuilder: SupportSQLiteDatabase.() -> Unit) : SupportSQLiteDatabase {
        return helper.createDatabase(TEST_DB, 28).apply {
            oldDbBuilder()
            runMigrationTransaction(migration)
        }
    }

    private suspend fun createVersion31Database(
        preferences: FailingDurableEncryptedPreferences,
        insertionType: Type = Type.MNEMONIC
    ): SupportSQLiteDatabase {
        val oldStore = SecretStoreV1Impl(preferences)
        oldStore.insertSecrets(insertionType)

        return helper.createDatabase(TEST_DB, 28).apply {
            insertAccount(
                publicKey = SUBSTRATE_KEYPAIR.publicKey,
                encryptionType = CRYPTO_TYPE
            )
            beginTransaction()
            try {
                V2Migration(oldStore, preferences).migrate(this)
                MigrateTablesToV2_29_30.migrate(this)
                MigrateTablesToV2_30_31.migrate(this)
                version = 31
                setTransactionSuccessful()
            } finally {
                endTransaction()
            }
        }
    }

    private suspend fun assertNullEntropyAccessSecretSurvivesFullChainRetry(
        insertionType: Type,
        expectedSeed: ByteArray?,
        expectedDerivationPath: String?
    ) {
        val preferences = FailingDurableEncryptedPreferences()
        val oldStore = SecretStoreV1Impl(preferences)
        val version2Store = SecretStoreV2(preferences)
        val substrateSecretStore = SubstrateSecretStore(preferences)
        val ethereumSecretStore = EthereumSecretStore(preferences)
        val version31Database = createVersion31Database(
            preferences = preferences,
            insertionType = insertionType
        )
        val version31MetaAccount =
            version31Database.getMetaAccounts().single()
        val metaId = version31MetaAccount.id
        val accessKey = "$metaId:ACCESS_SECRETS"
        val substrateKey = "$metaId:SUBSTRATE_SECRETS"
        val ethereumKey = "$metaId:ETHEREUM_SECRETS"

        val exactSplitState = try {
            assertEquals(31, version31Database.version)
            assertCorrectMetaAccount(
                metaAccountLocal = version31MetaAccount,
                selected = true,
                withEthereum = false
            )
            assertCorrectSecrets(
                metaAccountSecrets =
                    version2Store.getMetaAccountSecrets(metaId),
                withEntropy = false,
                withSeed = expectedSeed != null,
                withEthereum = false,
                withDerivationPath = expectedDerivationPath != null
            )
            assertTrue(preferences.hasKey(accessKey))
            assertFalse(preferences.hasKey(substrateKey))
            assertFalse(preferences.hasKey(ethereumKey))
            assertNoWalletSecretMigrationArtifacts(
                preferences = preferences,
                metaId = metaId
            )

            val productionMigrations = AppDatabase.migrations(
                storeV1 = oldStore,
                storeV2 = version2Store,
                encryptedPreferences = preferences,
                substrateSecretStore = substrateSecretStore,
                ethereumSecretStore = ethereumSecretStore
            ).filter { it.startVersion >= 31 }

            version31Database.beginTransaction()
            try {
                assertThrows(InjectedPostTonSqlFailure::class.java) {
                    productionMigrations.forEach { migration ->
                        migration.migrate(version31Database)
                        if (migration.endVersion == 73) {
                            // TON has already durably split ACCESS_SECRETS.
                            // Failing the following SQL edge proves the split
                            // survives rollback of both schema migrations.
                            throw InjectedPostTonSqlFailure()
                        }
                    }
                }
            } finally {
                version31Database.endTransaction()
            }

            assertEquals(31, version31Database.version)
            assertEquals(
                1,
                version31Database.singleInt("SELECT COUNT(*) FROM users")
            )
            assertCorrectMetaAccount(
                metaAccountLocal =
                    version31Database.getMetaAccounts().single(),
                selected = true,
                withEthereum = false
            )
            assertFalse(preferences.hasKey(accessKey))
            assertTrue(preferences.hasKey(substrateKey))
            assertFalse(preferences.hasKey(ethereumKey))
            assertNull(version2Store.getMetaAccountSecrets(metaId))
            assertNull(ethereumSecretStore.get(metaId))
            assertExpectedNullEntropySubstrateSecret(
                substrateSecretStore = substrateSecretStore,
                metaId = metaId,
                expectedSeed = expectedSeed,
                expectedDerivationPath = expectedDerivationPath
            )
            assertNoWalletSecretMigrationArtifacts(
                preferences = preferences,
                metaId = metaId
            )
            preferences.rawSnapshot()
        } finally {
            version31Database.close()
        }

        // Reopen through the production Room builder. The database still says
        // v31, while durable storage already contains the canonical v3 split.
        val migratedDatabase = AppDatabase.create(
            context =
                InstrumentationRegistry.getInstrumentation().targetContext,
            databaseName = TEST_DB,
            storeV1 = oldStore,
            storeV2 = version2Store,
            encryptedPreferences = preferences,
            substrateSecretStore = substrateSecretStore,
            ethereumSecretStore = ethereumSecretStore
        )
        try {
            val migrated = migratedDatabase.openHelper.writableDatabase
            assertEquals(77, migrated.version)
            val migratedMetaAccount = migrated.getMetaAccounts().single()
            assertEquals(metaId, migratedMetaAccount.id)
            assertCorrectMetaAccount(
                metaAccountLocal = migratedMetaAccount,
                selected = true,
                withEthereum = false
            )
            assertEquals(exactSplitState, preferences.rawSnapshot())
            assertFalse(preferences.hasKey(accessKey))
            assertTrue(preferences.hasKey(substrateKey))
            assertFalse(preferences.hasKey(ethereumKey))
            assertNull(version2Store.getMetaAccountSecrets(metaId))
            assertNull(ethereumSecretStore.get(metaId))
            assertExpectedNullEntropySubstrateSecret(
                substrateSecretStore = substrateSecretStore,
                metaId = metaId,
                expectedSeed = expectedSeed,
                expectedDerivationPath = expectedDerivationPath
            )
            assertNoWalletSecretMigrationArtifacts(
                preferences = preferences,
                metaId = metaId
            )
        } finally {
            migratedDatabase.close()
        }
    }

    private fun assertExpectedNullEntropySubstrateSecret(
        substrateSecretStore: SubstrateSecretStore,
        metaId: Long,
        expectedSeed: ByteArray?,
        expectedDerivationPath: String?
    ) {
        val substrate =
            requireNotNull(substrateSecretStore.get(metaId))
        assertNull(substrate[SubstrateSecrets.Entropy])
        assertArrayEquals(expectedSeed, substrate[SubstrateSecrets.Seed])
        assertEquals(
            expectedDerivationPath,
            substrate[SubstrateSecrets.SubstrateDerivationPath]
        )
        assertCorrectKeypair(
            expected = SUBSTRATE_KEYPAIR,
            actual = substrate[SubstrateSecrets.SubstrateKeypair]
        )
    }

    private suspend fun assertVersion28SeparatedStateRejectedWithoutMutation(
        configurePreferences:
            suspend (FailingDurableEncryptedPreferences) -> Unit
    ) {
        val preferences = FailingDurableEncryptedPreferences()
        val oldStore = SecretStoreV1Impl(preferences)
        oldStore.insertSecrets(Type.MNEMONIC)
        configurePreferences(preferences)
        val exactBefore = preferences.rawSnapshot()
        val database = helper.createDatabase(TEST_DB, 28).apply {
            insertAccount(
                publicKey = SUBSTRATE_KEYPAIR.publicKey,
                encryptionType = CRYPTO_TYPE
            )
        }

        try {
            database.runMigrationTransactionExpectingFailure(
                migration = V2Migration(oldStore, preferences),
                expectedFailure =
                WalletSecretConcurrentMutationException::class.java
            )
            assertEquals(28, database.version)
            assertEquals(
                0,
                database.singleInt("SELECT COUNT(*) FROM meta_accounts")
            )
            assertEquals(exactBefore, preferences.rawSnapshot())
            assertEquals(0, preferences.durableWriteCount)
            assertEquals(
                0,
                preferences.snapshotBoundReplacementCount
            )
        } finally {
            database.close()
        }
    }

    private fun expectedSubstrateSecrets() = SubstrateSecrets(
        substrateKeyPair = SUBSTRATE_KEYPAIR,
        entropy = MNEMONIC.entropy,
        seed = SUBSTRATE_SEED,
        substrateDerivationPath = DERIVATION_PATH
    )

    private fun expectedV2Secrets() = MetaAccountSecrets(
        substrateKeyPair = SUBSTRATE_KEYPAIR,
        entropy = MNEMONIC.entropy,
        seed = SUBSTRATE_SEED,
        substrateDerivationPath = DERIVATION_PATH,
        ethereumKeypair = ETHEREUM_KEYPAIR,
        ethereumDerivationPath =
            BIP32JunctionDecoder.DEFAULT_DERIVATION_PATH
    )

    private fun assertNoWalletSecretMigrationArtifacts(
        preferences: FailingDurableEncryptedPreferences,
        metaId: Long
    ) {
        val activeRootKeys = listOf(
            "$metaId:ACCESS_SECRETS",
            "$metaId:SUBSTRATE_SECRETS",
            "$metaId:ETHEREUM_SECRETS",
            "$metaId:TON_SECRETS"
        )
        activeRootKeys.forEach { activeKey ->
            assertFalse(
                preferences.hasKey(
                    WalletSecretQuarantine.keyFor(activeKey)
                )
            )
            assertFalse(
                preferences.hasKey(
                    WalletPublicIdentityRecovery.keyFor(
                        metaId = metaId,
                        activeSecretKey = activeKey
                    )
                )
            )
        }
        assertFalse(
            preferences.hasKey(
                legacyV1QuarantineKey(
                    publicKey = SUBSTRATE_KEYPAIR.publicKey,
                    metaId = metaId
                )
            )
        )
        assertFalse(
            preferences.hasKey(
                legacyV04QuarantineKey(
                    publicKey = SUBSTRATE_KEYPAIR.publicKey,
                    metaId = metaId
                )
            )
        )
    }

    private fun installSeparatedRetrySecrets(
        preferences: FailingDurableEncryptedPreferences,
        metaId: Long
    ) {
        preferences.removeKey("$metaId:ACCESS_SECRETS")
        SubstrateSecretStore(preferences).put(
            metaId = metaId,
            secrets = SubstrateSecrets(
                substrateKeyPair = SUBSTRATE_KEYPAIR,
                entropy = MNEMONIC.entropy,
                seed = SUBSTRATE_SEED,
                substrateDerivationPath = DERIVATION_PATH
            )
        )
        EthereumSecretStore(preferences).put(
            metaId = metaId,
            secrets = EthereumSecrets(
                entropy = MNEMONIC.entropy,
                seed = ETHEREUM_KEYPAIR.privateKey,
                ethereumKeypair = ETHEREUM_KEYPAIR,
                ethereumDerivationPath =
                    BIP32JunctionDecoder.DEFAULT_DERIVATION_PATH
            )
        )
    }

    private fun installKeypairOnlySeparatedRetrySecrets(
        preferences: FailingDurableEncryptedPreferences,
        metaId: Long
    ) {
        preferences.removeKey("$metaId:ACCESS_SECRETS")
        SubstrateSecretStore(preferences).put(
            metaId = metaId,
            secrets = SubstrateSecrets(
                substrateKeyPair = SUBSTRATE_KEYPAIR,
                entropy = null,
                seed = null,
                substrateDerivationPath = null
            )
        )
        EthereumSecretStore(preferences).put(
            metaId = metaId,
            secrets = EthereumSecrets(
                entropy = null,
                seed = ETHEREUM_KEYPAIR.privateKey,
                ethereumKeypair = ETHEREUM_KEYPAIR,
                ethereumDerivationPath = null
            )
        )
    }

    private suspend fun assertSeparatedRetrySnapshotRace(
        mutateEthereumSecret: Boolean
    ) {
        val preferences = FailingDurableEncryptedPreferences()
        val database = createVersion31Database(preferences)
        val metaId = database.getMetaAccounts().single().id
        val substrateKey = "$metaId:SUBSTRATE_SECRETS"
        val ethereumKey = "$metaId:ETHEREUM_SECRETS"
        val oldEthereumKeypair = EthereumKeypairFactory.generate(
            seed = ETHEREUM_SEED,
            junctions = emptyList()
        )
        assertFalse(
            oldEthereumKeypair.publicKey.contentEquals(
                ETHEREUM_KEYPAIR.publicKey
            )
        )
        val oldEthereumAddress =
            oldEthereumKeypair.publicKey.ethereumAddressFromPublicKey()
        val correctedEthereumAddress =
            ETHEREUM_KEYPAIR.publicKey.ethereumAddressFromPublicKey()
        database.updateEthereumPublicKey(
            metaId = metaId,
            publicKey = oldEthereumKeypair.publicKey
        )
        database.insertVersion31Asset(
            metaId = metaId,
            accountId = oldEthereumAddress
        )
        installSeparatedRetrySecrets(preferences, metaId)
        val exactBefore = preferences.rawSnapshot()
        val racedKey = if (mutateEthereumSecret) {
            ethereumKey
        } else {
            substrateKey
        }
        val durableWritesBeforeRace = preferences.durableWriteCount
        preferences.mutateOnceBeforeSnapshotBoundReplacement(
            sourceField = racedKey,
            replacement = MALFORMED_SECRET
        )

        try {
            database.runMigrationTransactionExpectingFailure(
                migration = EthereumDerivationPathMigration(preferences),
                expectedFailure =
                    WalletSecretConcurrentMutationException::class.java
            )

            assertEquals(
                exactBefore + (racedKey to MALFORMED_SECRET),
                preferences.rawSnapshot()
            )
            assertEquals(
                durableWritesBeforeRace,
                preferences.durableWriteCount
            )
            assertArrayEquals(
                oldEthereumKeypair.publicKey,
                database.singleBlob(
                    "SELECT ethereumPublicKey FROM meta_accounts " +
                        "WHERE id = ?",
                    metaId
                )
            )
            assertArrayEquals(
                oldEthereumAddress,
                database.singleBlob(
                    "SELECT ethereumAddress FROM meta_accounts " +
                        "WHERE id = ?",
                    metaId
                )
            )
            assertArrayEquals(
                oldEthereumAddress,
                database.singleBlob(
                    "SELECT accountId FROM assets WHERE metaId = ?",
                    metaId
                )
            )
            assertFalse(
                preferences.hasKey(
                    WalletSecretQuarantine.keyFor(substrateKey)
                )
            )
            assertFalse(
                preferences.hasKey(
                    WalletSecretQuarantine.keyFor(ethereumKey)
                )
            )

            preferences.putEncryptedString(
                racedKey,
                exactBefore.getValue(racedKey)
            )
            database.runMigrationTransaction(
                EthereumDerivationPathMigration(preferences)
            )

            assertEquals(exactBefore, preferences.rawSnapshot())
            assertEquals(
                durableWritesBeforeRace,
                preferences.durableWriteCount
            )
            assertArrayEquals(
                ETHEREUM_KEYPAIR.publicKey,
                database.singleBlob(
                    "SELECT ethereumPublicKey FROM meta_accounts " +
                        "WHERE id = ?",
                    metaId
                )
            )
            assertArrayEquals(
                correctedEthereumAddress,
                database.singleBlob(
                    "SELECT ethereumAddress FROM meta_accounts " +
                        "WHERE id = ?",
                    metaId
                )
            )
            assertArrayEquals(
                correctedEthereumAddress,
                database.singleBlob(
                    "SELECT accountId FROM assets WHERE metaId = ?",
                    metaId
                )
            )
        } finally {
            database.close()
        }
    }

    private fun SupportSQLiteDatabase.insertVersion31Asset(
        metaId: Long,
        accountId: ByteArray,
        tokenSymbol: String = "ETH"
    ) {
        execSQL(
            """
            INSERT INTO assets(
                tokenSymbol,
                chainId,
                accountId,
                metaId,
                freeInPlanks,
                reservedInPlanks,
                miscFrozenInPlanks,
                feeFrozenInPlanks,
                bondedInPlanks,
                redeemableInPlanks,
                unbondingInPlanks
            ) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any>(
                tokenSymbol,
                "ethereum",
                accountId,
                metaId,
                "0",
                "0",
                "0",
                "0",
                "0",
                "0",
                "0"
            )
        )
    }

    private fun SupportSQLiteDatabase.replaceChainNodesWithWrongDefault() {
        execSQL("DROP INDEX index_chain_nodes_chainId")
        execSQL("ALTER TABLE chain_nodes RENAME TO saved_chain_nodes")
        execSQL(
            """
            CREATE TABLE chain_nodes(
                chainId TEXT NOT NULL,
                url TEXT NOT NULL,
                name TEXT NOT NULL,
                isActive INTEGER NOT NULL DEFAULT 0,
                isDefault INTEGER NOT NULL DEFAULT 7,
                PRIMARY KEY(chainId, url),
                FOREIGN KEY(chainId) REFERENCES chains(id)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        execSQL(
            """
            INSERT INTO chain_nodes(
                chainId,
                url,
                name,
                isActive,
                isDefault
            )
            SELECT
                chainId,
                url,
                name,
                isActive,
                isDefault
            FROM saved_chain_nodes
            """.trimIndent()
        )
        execSQL("DROP TABLE saved_chain_nodes")
        execSQL(
            "CREATE INDEX index_chain_nodes_chainId " +
                "ON chain_nodes(chainId)"
        )
    }

    private fun SupportSQLiteDatabase.replaceAssetsWithBlockingCheck(
        allowedAccountId: ByteArray
    ) {
        val allowedAccountIdHex = allowedAccountId.toPlainHexString()
        execSQL("DROP INDEX index_assets_metaId")
        execSQL("ALTER TABLE assets RENAME TO saved_assets")
        execSQL(
            """
            CREATE TABLE assets(
                tokenSymbol TEXT NOT NULL,
                chainId TEXT NOT NULL,
                accountId BLOB NOT NULL,
                metaId INTEGER NOT NULL,
                freeInPlanks TEXT NOT NULL,
                reservedInPlanks TEXT NOT NULL,
                miscFrozenInPlanks TEXT NOT NULL,
                feeFrozenInPlanks TEXT NOT NULL,
                bondedInPlanks TEXT NOT NULL,
                redeemableInPlanks TEXT NOT NULL,
                unbondingInPlanks TEXT NOT NULL,
                PRIMARY KEY(tokenSymbol, chainId, accountId),
                CHECK(accountId = X'$allowedAccountIdHex')
            )
            """.trimIndent()
        )
        execSQL("INSERT INTO assets SELECT * FROM saved_assets")
        execSQL("DROP TABLE saved_assets")
        execSQL("CREATE INDEX index_assets_metaId ON assets(metaId)")
    }

    private suspend fun createDb31EthereumCorrectionFixture(
        preferences: FailingDurableEncryptedPreferences
    ): Db31EthereumCorrectionFixture {
        val database = createVersion31Database(preferences)
        val metaId = database.getMetaAccounts().single().id
        val oldEthereumKeypair = EthereumKeypairFactory.generate(
            seed = ETHEREUM_SEED,
            junctions = emptyList()
        )
        val oldEthereumAddress =
            oldEthereumKeypair.publicKey.ethereumAddressFromPublicKey()
        SecretStoreV2(preferences).putMetaAccountSecrets(
            metaId = metaId,
            secrets = MetaAccountSecrets(
                substrateKeyPair = SUBSTRATE_KEYPAIR,
                entropy = MNEMONIC.entropy,
                seed = SUBSTRATE_SEED,
                substrateDerivationPath = DERIVATION_PATH,
                ethereumKeypair = oldEthereumKeypair,
                ethereumDerivationPath = "m/44'/60'/0'/0"
            )
        )
        database.updateEthereumPublicKey(
            metaId = metaId,
            publicKey = oldEthereumKeypair.publicKey
        )
        database.insertVersion31Asset(
            metaId = metaId,
            accountId = oldEthereumAddress
        )
        return Db31EthereumCorrectionFixture(
            database = database,
            metaId = metaId,
            activeSecretKey = "$metaId:ACCESS_SECRETS",
            oldEthereumPublicKey = oldEthereumKeypair.publicKey,
            oldEthereumAddress = oldEthereumAddress
        )
    }

    private fun Db31EthereumCorrectionFixture.assertDatabaseStillUsesOldEthereumIdentity() {
        assertArrayEquals(
            oldEthereumPublicKey,
            database.singleBlob(
                "SELECT ethereumPublicKey FROM meta_accounts WHERE id = ?",
                metaId
            )
        )
        assertArrayEquals(
            oldEthereumAddress,
            database.singleBlob(
                "SELECT ethereumAddress FROM meta_accounts WHERE id = ?",
                metaId
            )
        )
        assertArrayEquals(
            oldEthereumAddress,
            database.singleBlob(
                "SELECT accountId FROM assets WHERE metaId = ?",
                metaId
            )
        )
    }

    private fun Db31EthereumCorrectionFixture.assertDatabaseUsesCorrectedEthereumIdentity() {
        val correctedAddress =
            ETHEREUM_KEYPAIR.publicKey.ethereumAddressFromPublicKey()
        assertArrayEquals(
            ETHEREUM_KEYPAIR.publicKey,
            database.singleBlob(
                "SELECT ethereumPublicKey FROM meta_accounts WHERE id = ?",
                metaId
            )
        )
        assertArrayEquals(
            correctedAddress,
            database.singleBlob(
                "SELECT ethereumAddress FROM meta_accounts WHERE id = ?",
                metaId
            )
        )
        assertArrayEquals(
            correctedAddress,
            database.singleBlob(
                "SELECT accountId FROM assets WHERE metaId = ?",
                metaId
            )
        )
    }

    private data class Db31EthereumCorrectionFixture(
        val database: SupportSQLiteDatabase,
        val metaId: Long,
        val activeSecretKey: String,
        val oldEthereumPublicKey: ByteArray,
        val oldEthereumAddress: ByteArray
    )

    private fun assertRecoveryMarkersOnly(
        preferences: FailingDurableEncryptedPreferences,
        exactBefore: Map<String, String>,
        metaId: Long,
        activeSecretKeys: List<String>
    ) {
        val expectedMarkers = activeSecretKeys.associate { activeSecretKey ->
            WalletPublicIdentityRecovery.keyFor(
                metaId = metaId,
                activeSecretKey = activeSecretKey
            ) to WalletPublicIdentityRecovery.MARKER_VALUE
        }
        assertEquals(
            exactBefore + expectedMarkers,
            preferences.rawSnapshot()
        )
        activeSecretKeys.forEach { activeSecretKey ->
            assertEquals(
                exactBefore.getValue(activeSecretKey),
                preferences.getDecryptedString(activeSecretKey)
            )
            assertFalse(
                preferences.hasKey(
                    WalletSecretQuarantine.keyFor(activeSecretKey)
                )
            )
        }
        expectedMarkers.forEach { (markerKey, markerValue) ->
            assertEquals(
                markerValue,
                preferences.getDecryptedString(markerKey)
            )
        }
    }

    private fun SupportSQLiteDatabase.runMigrationTransaction(migration: Migration) {
        beginTransaction()
        try {
            migration.migrate(this)
            setTransactionSuccessful()
        } finally {
            endTransaction()
        }
    }

    private fun SupportSQLiteDatabase.runMigrationTransactionExpectingFailure(
        migration: Migration,
        expectedFailure: Class<out Throwable> = InjectedDurableWriteFailure::class.java
    ) {
        beginTransaction()
        try {
            assertThrows(expectedFailure) {
                migration.migrate(this)
            }
        } finally {
            endTransaction()
        }
    }

    private fun SupportSQLiteDatabase.singleInt(sql: String): Int =
        query(sql).use { cursor ->
            check(cursor.moveToFirst())
            cursor.getInt(0)
        }

    private fun SupportSQLiteDatabase.singleBlob(
        sql: String,
        bindArg: Long
    ): ByteArray = query(sql, arrayOf(bindArg)).use { cursor ->
        check(cursor.moveToFirst())
            cursor.getBlob(0)
        }

    private fun SupportSQLiteDatabase.singleLong(
        sql: String,
        bindArg: Long
    ): Long = query(sql, arrayOf(bindArg)).use { cursor ->
        check(cursor.moveToFirst())
        cursor.getLong(0)
    }

    private fun SupportSQLiteDatabase.singleString(
        sql: String,
        bindArg: Long
    ): String = query(sql, arrayOf(bindArg)).use { cursor ->
        check(cursor.moveToFirst())
        cursor.getString(0)
    }

    private fun SupportSQLiteDatabase.singleColumnIsNull(
        sql: String,
        bindArg: Long
    ): Boolean = query(sql, arrayOf(bindArg)).use { cursor ->
        check(cursor.moveToFirst())
        cursor.isNull(0)
    }

    private fun SupportSQLiteDatabase.insertBoundedMetaAccount(metaId: Long) {
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
                position
            ) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any?>(
                metaId,
                SUBSTRATE_KEYPAIR.publicKey,
                CryptoType.SR25519.name,
                SUBSTRATE_KEYPAIR.publicKey.substrateAccountId(),
                null,
                null,
                "Bounded pre-existing wallet $metaId",
                1,
                0
            )
        )
    }

    private fun assertUnsafeLegacyUserIntegerFailsBeforePreferenceAccess(
        mutateDatabase: (SupportSQLiteDatabase, String) -> Unit
    ) {
        val preferences = FailingDurableEncryptedPreferences()
        val database = helper.createDatabase(TEST_DB, 28)
        val address = "unsafe-legacy-integer"
        try {
            database.insertRawLegacyAccount(
                address = address,
                publicKey = SUBSTRATE_KEYPAIR.publicKey.toPlainHexString(),
                position = 0
            )
            mutateDatabase(database, address)

            database.runMigrationTransactionExpectingFailure(
                migration = V2Migration(
                    storeV1 = SecretStoreV1Impl(preferences),
                    encryptedPreferences = preferences,
                    limits = V2MigrationLimits(
                        maxAccountPlans = 1,
                        maxRetainedSecretCharacters =
                            V2MigrationLimits.PRODUCTION
                                .maxRetainedSecretCharacters
                    )
                ),
                expectedFailure =
                    WalletPublicIdentityIntegrityException::class.java
            )

            assertEquals(0, preferences.preferenceInspectionCount)
            assertEquals(0, preferences.durableWriteCount)
            assertTrue(preferences.rawSnapshot().isEmpty())
            assertEquals(
                0,
                database.singleInt("SELECT COUNT(*) FROM meta_accounts")
            )
        } finally {
            database.close()
        }
    }

    private fun SupportSQLiteDatabase.insertBoundedChainAccount(
        metaId: Long,
        chainId: String
    ) {
        execSQL(
            """
            INSERT INTO chains(
                id,
                parentId,
                name,
                icon,
                prefix,
                isEthereumBased,
                isTestNet,
                hasCrowdloans,
                url,
                overridesCommon,
                staking_url,
                staking_type,
                history_url,
                history_type,
                crowdloans_url,
                crowdloans_type
            ) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any?>(
                chainId,
                null,
                "Bounded chain $chainId",
                "bounded-chain-icon",
                0,
                0,
                1,
                0,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
            )
        )
        execSQL(
            """
            INSERT INTO chain_accounts(
                metaId,
                chainId,
                publicKey,
                accountId,
                cryptoType
            ) VALUES(?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any>(
                metaId,
                chainId,
                SUBSTRATE_KEYPAIR.publicKey,
                SUBSTRATE_KEYPAIR.publicKey.substrateAccountId(),
                CryptoType.SR25519.name
            )
        )
    }

    private fun SupportSQLiteDatabase.updateEthereumPublicKey(
        metaId: Long,
        publicKey: ByteArray
    ) {
        val values = ContentValues().apply {
            put("ethereumPublicKey", publicKey)
            put("ethereumAddress", publicKey.ethereumAddressFromPublicKey())
        }
        update("meta_accounts", SQLiteDatabase.CONFLICT_REPLACE, values, "id=?", arrayOf(metaId))
    }

    private fun SupportSQLiteDatabase.insertRawLegacyAccount(
        address: String,
        publicKey: String,
        position: Int
    ) {
        execSQL(
            """
            INSERT INTO users(
                address,
                publicKey,
                cryptoType,
                position,
                networkType,
                username
            ) VALUES(?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any>(
                address,
                publicKey,
                mapEncryptionToCryptoType(CRYPTO_TYPE).ordinal,
                position,
                0,
                "Bounded migration wallet $position"
            )
        )
    }

    private fun assertCorrectMetaAccount(
        metaAccountLocal: MetaAccountLocal,
        selected: Boolean,
        withEthereum: Boolean,
    ) = with(metaAccountLocal) {
        assertEquals(NAME, name)

        assertArrayEquals(SUBSTRATE_KEYPAIR.publicKey, substratePublicKey)
        assertArrayEquals(SUBSTRATE_KEYPAIR.publicKey.substrateAccountId(), substrateAccountId)
        assertEquals(CRYPTO_TYPE, substrateCryptoType?.let { mapCryptoTypeToEncryption(it) })

        assertEquals(selected, metaAccountLocal.isSelected)

        if (withEthereum) {
            assertArrayEquals(ETHEREUM_KEYPAIR.publicKey, ethereumPublicKey)
            assertArrayEquals(ETHEREUM_KEYPAIR.publicKey.ethereumAddressFromPublicKey(), ethereumAddress)
        } else {
            assertNull(ethereumAddress)
            assertNull(ethereumPublicKey)
        }
    }

    private fun assertCorrectSecrets(
        metaAccountSecrets: EncodableStruct<MetaAccountSecrets>?,
        withEntropy: Boolean,
        withSeed: Boolean,
        withEthereum: Boolean,
        withDerivationPath: Boolean
    ) {
        requireNotNull(metaAccountSecrets)

        val expectedEntropy = MNEMONIC.entropy.takeIf { withEntropy }
        val expectedSeed = SUBSTRATE_SEED.takeIf { withSeed }
        val expectedEthereumKeypair = ETHEREUM_KEYPAIR.takeIf { withEthereum }
        val expectedEthereumDerivationPath = ETHEREUM_DERIVATION_PATH.takeIf { withEthereum }
        val expectedDerivationPath = DERIVATION_PATH.takeIf { withDerivationPath }

        assertArrayEquals(expectedEntropy, metaAccountSecrets[MetaAccountSecrets.Entropy])
        assertArrayEquals(expectedSeed, metaAccountSecrets[MetaAccountSecrets.Seed])

        assertCorrectKeypair(SUBSTRATE_KEYPAIR, metaAccountSecrets[MetaAccountSecrets.SubstrateKeypair])
        assertEquals(expectedDerivationPath, metaAccountSecrets[MetaAccountSecrets.SubstrateDerivationPath])

        assertCorrectKeypair(expectedEthereumKeypair, metaAccountSecrets[MetaAccountSecrets.EthereumKeypair])
        assertEquals(expectedEthereumDerivationPath, metaAccountSecrets[MetaAccountSecrets.EthereumDerivationPath])
    }

    private fun assertCorrectKeypair(
        expected: Keypair?,
        actual: EncodableStruct<KeyPairSchema>?
    ) {
        assertArrayEquals(expected?.publicKey, actual?.get(KeyPairSchema.PublicKey))
        assertArrayEquals(expected?.privateKey, actual?.get(KeyPairSchema.PrivateKey))
        assertArrayEquals((expected as? Sr25519Keypair)?.nonce, actual?.get(KeyPairSchema.Nonce))
    }

    private fun SupportSQLiteDatabase.getMetaAccounts(): List<MetaAccountLocal> {
        val cursor = query("SELECT * FROM ${MetaAccountLocal.Table.TABLE_NAME}")

        return cursor.map {

            val metaAccount = MetaAccountLocal(
                substratePublicKey= getBlob(getColumnIndex(Column.SUBSTRATE_PUBKEY)),
                substrateCryptoType = CryptoType.valueOf(getString(getColumnIndex(Column.SUBSTRATE_CRYPTO_TYPE))),
                substrateAccountId = getBlob(getColumnIndex(Column.SUBSTRATE_ACCOUNT_ID)),
                ethereumPublicKey = getBlob(getColumnIndex(Column.ETHEREUM_PUBKEY)),
                ethereumAddress = getBlob(getColumnIndex(Column.ETHEREUM_ADDRESS)),
                tonPublicKey = null,
                name = getString(getColumnIndex(Column.NAME)),
                isSelected = getInt(getColumnIndex(Column.IS_SELECTED)) == 1,
                position = getInt(getColumnIndex(Column.POSITION)),
                isBackedUp = false,
                googleBackupAddress = null,
                initialized = true
            )

            metaAccount.id = getLong(getColumnIndex(Column.ID))

            metaAccount
        }
    }

    private suspend fun SecretStoreV1.insertSecrets(type: Type) {
        val securitySource = when(type) {
            Type.MNEMONIC -> SecuritySource.Specified.Mnemonic(
                seed = SUBSTRATE_SEED,
                keypair = SUBSTRATE_KEYPAIR,
                mnemonic = MNEMONIC_WORDS,
                derivationPath = DERIVATION_PATH
            )
            Type.SEED -> SecuritySource.Specified.Seed(
                seed = SUBSTRATE_SEED,
                keypair = SUBSTRATE_KEYPAIR,
                derivationPath = DERIVATION_PATH
            )
            Type.KEYPAIR -> SecuritySource.Unspecified(
                keypair = SUBSTRATE_KEYPAIR
            )
            Type.JSON -> SecuritySource.Specified.Json(
                seed = null, // no seed for SR25519
                keypair = SUBSTRATE_KEYPAIR
            )
            Type.CREATE -> SecuritySource.Specified.Create(
                seed = SUBSTRATE_SEED,
                keypair = SUBSTRATE_KEYPAIR,
                mnemonic = MNEMONIC_WORDS,
                derivationPath = DERIVATION_PATH
            )
        }

        saveSecuritySource(SUBSTRATE_KEYPAIR.publicKey.toAddress(0), securitySource)
    }

    private fun distinctSubstrateKeypair(marker: Int): Keypair {
        val seed = ByteArray(32) { index -> (marker + index).toByte() }
        return SubstrateKeypairFactory.generate(
            encryptionType = CRYPTO_TYPE,
            seed = seed,
            junctions = emptyList()
        )
    }

    private fun insertV04Secrets(
        preferences: EncryptedPreferences,
        address: String,
        keypair: Keypair,
        seed: ByteArray?,
        entropy: ByteArray?,
        derivationPath: String?
    ): Map<String, String> {
        val signingData = KeyPairSchema {
            it[PrivateKey] = keypair.privateKey
            it[PublicKey] = keypair.publicKey
            it[Nonce] = (keypair as? Sr25519Keypair)?.nonce
        }
        val values = buildMap {
            put("private_$address", KeyPairSchema.toHexString(signingData))
            seed?.let { put("seed_$address", it.toPlainHexString()) }
            entropy?.let { put("entropy_$address", it.toPlainHexString()) }
            derivationPath?.let { put("derivation_$address", it) }
        }
        values.forEach(preferences::putEncryptedString)
        return values
    }

    private fun SupportSQLiteDatabase.insertAccount(
        publicKey: ByteArray,
        encryptionType: EncryptionType,
        position: Int = 0,
        username: String = NAME
    ) {
        val params = ContentValues().apply {
            put("address", publicKey.toAddress(0))
            put("publicKey", publicKey.toPlainHexString())
            put("cryptoType", mapEncryptionToCryptoType(encryptionType).ordinal)
            put("position", position)
            put("networkType", 0)
            put("username", username)
        }

        insert("users", SQLiteDatabase.CONFLICT_REPLACE, params)
    }

    private enum class Type {
        MNEMONIC, SEED, JSON, KEYPAIR, CREATE
    }

    private class FailingDurableEncryptedPreferences : EncryptedPreferences {
        private val values = mutableMapOf<String, String>()
        private var mutationOnHasKey: MutationOnHasKey? = null
        private var mutationBeforeSnapshotBoundReplacement:
            SnapshotBoundMutation? = null
        var failDurableWrites = false
        var failAfterDurableWrite = false
        var secureStorageUnavailableKey: String? = null
        var durableWriteCount = 0
            private set
        var snapshotBoundReplacementCount = 0
            private set
        var preferenceInspectionCount = 0
            private set

        override fun putEncryptedString(field: String, value: String) {
            values[field] = value
        }

        override fun getDecryptedString(field: String): String? {
            preferenceInspectionCount += 1
            if (field == secureStorageUnavailableKey) {
                throw WalletSecureStorageUnavailableException(
                    "Injected global secure-storage failure"
                )
            }
            return values[field]
        }

        override fun hasKey(field: String): Boolean {
            preferenceInspectionCount += 1
            mutationOnHasKey?.takeIf { it.triggerField == field }?.let {
                values[it.sourceField] = it.replacement
                mutationOnHasKey = null
            }
            return field in values
        }

        override fun removeKey(field: String) {
            values.remove(field)
        }

        override fun replaceEncryptedStringsDurably(
            valuesToPut: Map<String, String>,
            keysToRemove: Set<String>
        ) {
            if (failDurableWrites) throw InjectedDurableWriteFailure()

            require(valuesToPut.keys.intersect(keysToRemove).isEmpty())
            values.putAll(valuesToPut)
            keysToRemove.forEach(values::remove)
            durableWriteCount += 1

            if (failAfterDurableWrite) throw InjectedDurableWriteFailure()
        }

        @Synchronized
        override fun replaceEncryptedStringsDurablyIfStatesMatch(
            expectedStates:
            Map<String, EncryptedPreferenceSnapshot?>,
            valuesToPut: Map<String, String>,
            keysToRemove: Set<String>,
            snapshotMoves: List<EncryptedPreferenceSnapshotMove>
        ): Boolean {
            snapshotBoundReplacementCount += 1
            mutationBeforeSnapshotBoundReplacement?.let {
                values[it.sourceField] = it.replacement
                mutationBeforeSnapshotBoundReplacement = null
            }
            return super<EncryptedPreferences>
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
        ): Boolean {
            val sourceExists = sourceKey in values
            if (!sourceExists) {
                check(quarantineKey in values)
                return expectedSnapshot.matchesUnencryptedStorageValue(
                    values.getValue(quarantineKey)
                )
            }

            val sourceValue = values.getValue(sourceKey)
            if (
                !expectedSnapshot.matchesUnencryptedStorageValue(sourceValue)
            ) {
                return false
            }
            val existingQuarantine = values[quarantineKey]
            check(existingQuarantine == null || existingQuarantine == sourceValue)
            replaceEncryptedStringsDurably(
                valuesToPut = mapOf(quarantineKey to sourceValue),
                keysToRemove = setOf(sourceKey)
            )
            return true
        }

        override fun requireDurableStorageHealthy() = Unit

        fun rawSnapshot(): Map<String, String> = values.toMap()

        fun mutateOnceWhenHasKey(
            triggerField: String,
            sourceField: String,
            replacement: String
        ) {
            mutationOnHasKey = MutationOnHasKey(
                triggerField = triggerField,
                sourceField = sourceField,
                replacement = replacement
            )
        }

        fun mutateOnceBeforeSnapshotBoundReplacement(
            sourceField: String,
            replacement: String
        ) {
            mutationBeforeSnapshotBoundReplacement =
                SnapshotBoundMutation(
                    sourceField = sourceField,
                    replacement = replacement
                )
        }

        private data class MutationOnHasKey(
            val triggerField: String,
            val sourceField: String,
            val replacement: String
        )

        private data class SnapshotBoundMutation(
            val sourceField: String,
            val replacement: String
        )
    }

    private class InjectedDurableWriteFailure : RuntimeException()
    private class InjectedPostTonSqlFailure : RuntimeException()

    private companion object {
        const val WATCH_ONLY_NAME = "READ ONLY"
        const val EMPTY_SECRET_NAME = "EMPTY SECRET"
        const val MALFORMED_SECRET_NAME = "MALFORMED SECRET"
        const val MALFORMED_SECRET = "not-scale-\u0000-\uD83D\uDD10"
        const val ATTACKER_DB_IDENTITY_BYTES = 8_388_608
        val WATCH_ONLY_PUBLIC_KEY = ByteArray(32) { (it + 17).toByte() }
        val EMPTY_SECRET_PUBLIC_KEY = ByteArray(32) { (it + 33).toByte() }
        val MALFORMED_SECRET_PUBLIC_KEY = ByteArray(32) { (it + 49).toByte() }

        fun legacyV1SecretKey(address: String) = "security_source_$address"
        fun legacyV1QuarantineKey(
            publicKey: ByteArray,
            metaId: Long = 1L
        ) = WalletSecretQuarantine.legacyV1KeyForMetaId(metaId, publicKey)

        fun legacyV04QuarantineKey(
            publicKey: ByteArray,
            metaId: Long = 1L
        ) = WalletSecretQuarantine.legacyV04KeyForMetaId(metaId, publicKey)
    }
}
