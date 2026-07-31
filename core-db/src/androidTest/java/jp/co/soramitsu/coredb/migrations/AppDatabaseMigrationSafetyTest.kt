package jp.co.soramitsu.coredb.migrations

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import jp.co.soramitsu.common.data.secrets.legacy.LegacyWalletV04Secrets
import jp.co.soramitsu.common.data.secrets.v1.SecretStoreV1Impl
import jp.co.soramitsu.common.data.secrets.v2.KeyPairSchema
import jp.co.soramitsu.common.data.secrets.v2.SecretStoreV2
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecretStore
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecrets
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecrets
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecretStore
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretQuarantine
import jp.co.soramitsu.common.utils.DEFAULT_DERIVATION_PATH
import jp.co.soramitsu.common.utils.deriveSeed32
import jp.co.soramitsu.common.utils.ethereumAddressFromPublicKey
import jp.co.soramitsu.common.utils.invoke
import jp.co.soramitsu.common.utils.substrateAccountId
import jp.co.soramitsu.core.crypto.mapEncryptionToCryptoType
import jp.co.soramitsu.core.model.SecuritySource
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.coredb.AppDatabase
import jp.co.soramitsu.coredb.migrations.fixtures.ReleasedV04Account
import jp.co.soramitsu.coredb.migrations.fixtures.ReleasedV04DatabaseFixture
import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.junction.BIP32JunctionDecoder
import jp.co.soramitsu.fearless_utils.encrypt.junction.SubstrateJunctionDecoder
import jp.co.soramitsu.fearless_utils.encrypt.keypair.ethereum.EthereumKeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.Sr25519Keypair
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.mnemonic.MnemonicCreator
import jp.co.soramitsu.fearless_utils.encrypt.seed.ethereum.EthereumSeedFactory
import jp.co.soramitsu.fearless_utils.encrypt.seed.substrate.SubstrateSeedFactory
import jp.co.soramitsu.fearless_utils.extensions.toHexString as toPlainHexString
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAddress
import jp.co.soramitsu.testshared.HashMapEncryptedPreferences
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AppDatabaseMigrationSafetyTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @After
    fun deleteTestDatabases() {
        DATABASE_NAMES.forEach(context::deleteDatabase)
    }

    @Test
    fun migratesVersion73To76AndPreservesRepresentativeUserData() {
        helper.createDatabase(VERSION_73_DATABASE, 73).apply {
            insertRepresentativeUserData()
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            VERSION_73_DATABASE,
            76,
            true,
            Migration_73_74,
            Migration_74_75,
            Migration_75_76
        )

        migrated.assertRepresentativeUserData()
        assertNull(migrated.singleNullableString("SELECT xcm FROM chains WHERE id = '$CHAIN_ID'"))
        assertNull(migrated.singleNullableString("SELECT coinbaseUrl FROM chain_assets WHERE chainId = '$CHAIN_ID'"))
        migrated.close()
    }

    @Test
    fun migratesProductionVersion74To76WithoutRemovingWalletRows() {
        helper.createDatabase(VERSION_74_DATABASE, 74).apply {
            insertRepresentativeUserData()
            execSQL(
                """
                INSERT INTO sora_card(
                    id,
                    accessToken,
                    refreshToken,
                    accessTokenExpirationTime,
                    kycStatus
                ) VALUES(?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any>("legacy-card", "access", "refresh", 123456789L, "PENDING")
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            VERSION_74_DATABASE,
            76,
            true,
            Migration_74_75,
            Migration_75_76
        )

        migrated.assertRepresentativeUserData()
        assertFalse(migrated.tableExists("sora_card"))
        assertNull(migrated.singleNullableString("SELECT xcm FROM chains WHERE id = '$CHAIN_ID'"))
        migrated.close()
    }

    @Test
    fun version75MigrationPreservesAdversarialTextAndBlobValues() {
        helper.createDatabase(VERSION_75_DATABASE, 75).apply {
            insertRepresentativeUserData()
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            VERSION_75_DATABASE,
            76,
            true,
            Migration_75_76
        )

        migrated.assertRepresentativeUserData()
        assertEquals(
            ADVERSARIAL_WALLET_NAME,
            migrated.singleString("SELECT name FROM meta_accounts WHERE id = $META_ID")
        )
        assertEquals(
            ADVERSARIAL_CHAIN_NAME,
            migrated.singleString("SELECT name FROM chains WHERE id = '$CHAIN_ID'")
        )
        assertTrue(
            migrated.singleBlob("SELECT accountId FROM assets WHERE metaId = $META_ID")
                .contentEquals(ADVERSARIAL_ACCOUNT_ID)
        )
        migrated.close()
    }

    @Test
    fun malformedSchemaRollsBackEarlierMigrationWorkAndPreservesRows() {
        helper.createDatabase(MALFORMED_DATABASE, 74).apply {
            insertRepresentativeUserData()
            execSQL(
                """
                INSERT INTO sora_card(
                    id,
                    accessToken,
                    refreshToken,
                    accessTokenExpirationTime,
                    kycStatus
                ) VALUES('rollback-card', 'access', 'refresh', 1, 'PENDING')
                """.trimIndent()
            )
            execSQL("DROP TABLE chains")
            close()
        }

        assertThrows(Exception::class.java) {
            helper.runMigrationsAndValidate(
                MALFORMED_DATABASE,
                76,
                true,
                Migration_74_75,
                Migration_75_76
            )
        }

        openRawReadOnly(MALFORMED_DATABASE).use { db ->
            assertEquals(74, db.version)
            assertEquals(1, db.singleInt("SELECT COUNT(*) FROM meta_accounts WHERE id = $META_ID"))
            assertEquals(1, db.singleInt("SELECT COUNT(*) FROM sora_card WHERE id = 'rollback-card'"))
        }
    }

    @Test
    fun futureVersionFailsClosedAndDoesNotEraseUserData() {
        createVersion76DatabaseWithUserData(FUTURE_DATABASE)
        setDatabaseVersion(FUTURE_DATABASE, 78)

        val failure = openWithProductionPolicyExpectingFailure(FUTURE_DATABASE)

        assertTrue(failure.message.orEmpty().contains("78"))
        assertTrue(failure.message.orEmpty().contains("77"))
        assertRawUserDataPreserved(FUTURE_DATABASE, expectedVersion = 78)
    }

    @Test
    fun unsupportedAncientVersionFailsClosedAndDoesNotEraseUserData() {
        createVersion76DatabaseWithUserData(ANCIENT_DATABASE)
        setDatabaseVersion(ANCIENT_DATABASE, 8)

        val failure = openWithProductionPolicyExpectingFailure(ANCIENT_DATABASE)

        assertTrue(failure.message.orEmpty().contains("8"))
        assertTrue(failure.message.orEmpty().contains("77"))
        assertRawUserDataPreserved(ANCIENT_DATABASE, expectedVersion = 8)
    }

    @Test
    fun productionOpenMigratesShippedVersion26SchemaTo77AndPreservesWallets() {
        assertShippedLegacySchemaMigratesTo77(VERSION_26_DATABASE, sourceVersion = 26)
    }

    @Test
    fun productionOpenMigratesShippedVersion27SchemaTo77AndPreservesWallets() {
        assertShippedLegacySchemaMigratesTo77(VERSION_27_DATABASE, sourceVersion = 27)
    }

    @Test
    fun productionOpenMigratesReleasedV04Version9To77AndPreservesSplitSecrets() =
        runBlocking {
            val preferences = HashMapEncryptedPreferences()
            val originalSplitSecrets = putReleasedV04Secrets(
                preferences = preferences,
                address = RELEASED_V04_SIGNING_ADDRESS
            )
            createReleasedV04Database(
                databaseName = VERSION_9_DATABASE,
                accounts = listOf(
                    releasedV04SigningAccount(position = 0),
                    ReleasedV04Account(
                        address = RELEASED_V04_WATCH_ONLY_ADDRESS,
                        username = RELEASED_V04_WATCH_ONLY_NAME,
                        publicKeyHex =
                        RELEASED_V04_WATCH_ONLY_PUBLIC_KEY.toPlainHexString(),
                        cryptoTypeOrdinal =
                        mapEncryptionToCryptoType(RELEASED_V04_CRYPTO_TYPE).ordinal,
                        position = 1
                    )
                )
            )

            val stores = openProductionDatabase(
                databaseName = VERSION_9_DATABASE,
                preferences = preferences
            )
            try {
                val migrated = stores.database.openHelper.writableDatabase
                assertEquals(77, migrated.version)
                assertEquals(2, migrated.singleInt("SELECT COUNT(*) FROM meta_accounts"))
                assertEquals(
                    1,
                    migrated.singleInt("SELECT COUNT(*) FROM meta_accounts WHERE isSelected = 1")
                )

                val signingMetaId = migrated.singleLong(
                    "SELECT id FROM meta_accounts WHERE name = ?",
                    RELEASED_V04_SIGNING_NAME
                )
                val watchOnlyMetaId = migrated.singleLong(
                    "SELECT id FROM meta_accounts WHERE name = ?",
                    RELEASED_V04_WATCH_ONLY_NAME
                )

                assertArrayEquals(
                    RELEASED_V04_SUBSTRATE_KEYPAIR.publicKey,
                    migrated.singleBlob(
                        "SELECT substratePublicKey FROM meta_accounts WHERE id = ?",
                        signingMetaId
                    )
                )
                assertArrayEquals(
                    RELEASED_V04_EXPECTED_ETHEREUM_KEYPAIR.publicKey,
                    migrated.singleBlob(
                        "SELECT ethereumPublicKey FROM meta_accounts WHERE id = ?",
                        signingMetaId
                    )
                )
                assertArrayEquals(
                    RELEASED_V04_WATCH_ONLY_PUBLIC_KEY,
                    migrated.singleBlob(
                        "SELECT substratePublicKey FROM meta_accounts WHERE id = ?",
                        watchOnlyMetaId
                    )
                )

                val substrateSecrets = requireNotNull(
                    stores.substrateSecretStore.get(
                        metaId = signingMetaId,
                        expectedPublicKey =
                        RELEASED_V04_SUBSTRATE_KEYPAIR.publicKey,
                        expectedCryptoType = CryptoType.SR25519,
                        expectedAccountId =
                        RELEASED_V04_SUBSTRATE_KEYPAIR.publicKey
                            .substrateAccountId()
                    )
                )
                assertArrayEquals(
                    RELEASED_V04_ENTROPY,
                    substrateSecrets[SubstrateSecrets.Entropy]
                )
                assertArrayEquals(
                    RELEASED_V04_SUBSTRATE_SEED,
                    substrateSecrets[SubstrateSecrets.Seed]
                )
                assertEquals(
                    RELEASED_V04_DERIVATION_PATH,
                    substrateSecrets[SubstrateSecrets.SubstrateDerivationPath]
                )
                val substrateKeypair =
                    substrateSecrets[SubstrateSecrets.SubstrateKeypair]
                assertArrayEquals(
                    RELEASED_V04_SUBSTRATE_KEYPAIR.publicKey,
                    substrateKeypair[KeyPairSchema.PublicKey]
                )
                assertArrayEquals(
                    RELEASED_V04_SUBSTRATE_KEYPAIR.privateKey,
                    substrateKeypair[KeyPairSchema.PrivateKey]
                )
                assertArrayEquals(
                    (RELEASED_V04_SUBSTRATE_KEYPAIR as Sr25519Keypair).nonce,
                    substrateKeypair[KeyPairSchema.Nonce]
                )

                val ethereumSecrets = requireNotNull(
                    stores.ethereumSecretStore.get(
                        metaId = signingMetaId,
                        expectedPublicKey =
                        RELEASED_V04_EXPECTED_ETHEREUM_KEYPAIR.publicKey,
                        expectedAddress =
                        RELEASED_V04_EXPECTED_ETHEREUM_KEYPAIR.publicKey
                            .ethereumAddressFromPublicKey()
                    )
                )
                assertArrayEquals(
                    RELEASED_V04_ENTROPY,
                    ethereumSecrets[EthereumSecrets.Entropy]
                )
                assertArrayEquals(
                    RELEASED_V04_EXPECTED_ETHEREUM_KEYPAIR.privateKey,
                    ethereumSecrets[EthereumSecrets.Seed]
                )
                assertEquals(
                    BIP32JunctionDecoder.DEFAULT_DERIVATION_PATH,
                    ethereumSecrets[EthereumSecrets.EthereumDerivationPath]
                )
                val ethereumKeypair =
                    ethereumSecrets[EthereumSecrets.EthereumKeypair]
                assertArrayEquals(
                    RELEASED_V04_EXPECTED_ETHEREUM_KEYPAIR.publicKey,
                    ethereumKeypair[KeyPairSchema.PublicKey]
                )
                assertArrayEquals(
                    RELEASED_V04_EXPECTED_ETHEREUM_KEYPAIR.privateKey,
                    ethereumKeypair[KeyPairSchema.PrivateKey]
                )

                assertNull(stores.substrateSecretStore.get(watchOnlyMetaId))
                assertNull(stores.ethereumSecretStore.get(watchOnlyMetaId))
                assertNull(stores.storeV2.getMetaAccountSecrets(signingMetaId))
                originalSplitSecrets.forEach { (key, value) ->
                    assertTrue(preferences.hasKey(key))
                    assertEquals(value, preferences.getDecryptedString(key))
                }
            } finally {
                stores.database.close()
            }
        }

    @Test
    fun corruptReleasedV04SecretIsQuarantinedWithoutBlockingNextVersion9Wallet() =
        runBlocking {
            val preferences = HashMapEncryptedPreferences()
            val corruptAddress = RELEASED_V04_CORRUPT_PUBLIC_KEY.toAddress(0)
            val corruptPrivateKey = LegacyWalletV04Secrets.privateKey(corruptAddress)
            val corruptSeedKey = LegacyWalletV04Secrets.seedKey(corruptAddress)
            val corruptPrivateValue = "not-canonical-scale-'\";\u0000-\uD83D\uDD10"
            val corruptSeedValue = RELEASED_V04_SUBSTRATE_SEED.toPlainHexString()
            preferences.putEncryptedString(corruptPrivateKey, corruptPrivateValue)
            preferences.putEncryptedString(corruptSeedKey, corruptSeedValue)
            val validSplitSecrets = putReleasedV04Secrets(
                preferences = preferences,
                address = RELEASED_V04_SIGNING_ADDRESS
            )

            createReleasedV04Database(
                databaseName = VERSION_9_CORRUPT_DATABASE,
                accounts = listOf(
                    ReleasedV04Account(
                        address = corruptAddress,
                        username = RELEASED_V04_CORRUPT_NAME,
                        publicKeyHex = RELEASED_V04_CORRUPT_PUBLIC_KEY.toPlainHexString(),
                        cryptoTypeOrdinal =
                        mapEncryptionToCryptoType(RELEASED_V04_CRYPTO_TYPE).ordinal,
                        position = 0
                    ),
                    releasedV04SigningAccount(position = 1)
                )
            )

            val stores = openProductionDatabase(
                databaseName = VERSION_9_CORRUPT_DATABASE,
                preferences = preferences
            )
            try {
                val migrated = stores.database.openHelper.writableDatabase
                assertEquals(77, migrated.version)
                assertEquals(2, migrated.singleInt("SELECT COUNT(*) FROM meta_accounts"))

                val corruptMetaId = migrated.singleLong(
                    "SELECT id FROM meta_accounts WHERE name = ?",
                    RELEASED_V04_CORRUPT_NAME
                )
                val validMetaId = migrated.singleLong(
                    "SELECT id FROM meta_accounts WHERE name = ?",
                    RELEASED_V04_SIGNING_NAME
                )
                assertNull(stores.substrateSecretStore.get(corruptMetaId))
                assertNull(stores.ethereumSecretStore.get(corruptMetaId))
                assertNotNull(
                    stores.substrateSecretStore.get(
                        metaId = validMetaId,
                        expectedPublicKey =
                        RELEASED_V04_SUBSTRATE_KEYPAIR.publicKey,
                        expectedCryptoType = CryptoType.SR25519,
                        expectedAccountId =
                        RELEASED_V04_SUBSTRATE_KEYPAIR.publicKey
                            .substrateAccountId()
                    )
                )

                val quarantineKey =
                    WalletSecretQuarantine.legacyV04KeyForMetaId(
                        metaId = corruptMetaId,
                        publicKey = RELEASED_V04_CORRUPT_PUBLIC_KEY
                    )
                assertFalse(preferences.hasKey(corruptPrivateKey))
                assertEquals(
                    corruptPrivateValue,
                    preferences.getDecryptedString(quarantineKey)
                )
                assertEquals(
                    corruptSeedValue,
                    preferences.getDecryptedString(
                        WalletSecretQuarantine.keyFor(corruptSeedKey)
                    )
                )
                assertFalse(preferences.hasKey(corruptSeedKey))
                validSplitSecrets.forEach { (key, value) ->
                    assertEquals(value, preferences.getDecryptedString(key))
                }
            } finally {
                stores.database.close()
            }
        }

    private fun putReleasedV04Secrets(
        preferences: EncryptedPreferences,
        address: String
    ): Map<String, String> {
        val keypair = KeyPairSchema { signingData ->
            signingData[KeyPairSchema.PrivateKey] =
                RELEASED_V04_SUBSTRATE_KEYPAIR.privateKey
            signingData[KeyPairSchema.PublicKey] =
                RELEASED_V04_SUBSTRATE_KEYPAIR.publicKey
            signingData[KeyPairSchema.Nonce] =
                (RELEASED_V04_SUBSTRATE_KEYPAIR as Sr25519Keypair).nonce
        }
        val values = mapOf(
            LegacyWalletV04Secrets.privateKey(address) to
                KeyPairSchema.toHexString(keypair),
            LegacyWalletV04Secrets.seedKey(address) to
                RELEASED_V04_SUBSTRATE_SEED.toPlainHexString(),
            LegacyWalletV04Secrets.entropyKey(address) to
                RELEASED_V04_ENTROPY.toPlainHexString(),
            LegacyWalletV04Secrets.derivationKey(address) to
                RELEASED_V04_DERIVATION_PATH
        )
        values.forEach(preferences::putEncryptedString)
        return values
    }

    private fun releasedV04SigningAccount(position: Int) =
        ReleasedV04Account(
            address = RELEASED_V04_SIGNING_ADDRESS,
            username = RELEASED_V04_SIGNING_NAME,
            publicKeyHex =
            RELEASED_V04_SUBSTRATE_KEYPAIR.publicKey.toPlainHexString(),
            cryptoTypeOrdinal =
            mapEncryptionToCryptoType(RELEASED_V04_CRYPTO_TYPE).ordinal,
            position = position
        )

    private fun createReleasedV04Database(
        databaseName: String,
        accounts: List<ReleasedV04Account>
    ) {
        ReleasedV04DatabaseFixture.create(
            context = context,
            databaseName = databaseName,
            accounts = accounts
        )
    }

    private fun openProductionDatabase(
        databaseName: String,
        preferences: EncryptedPreferences
    ): ProductionDatabaseStores {
        val storeV1 = SecretStoreV1Impl(preferences)
        val storeV2 = SecretStoreV2(preferences)
        val substrateSecretStore = SubstrateSecretStore(preferences)
        val ethereumSecretStore = EthereumSecretStore(preferences)
        val database = AppDatabase.create(
            context = context,
            databaseName = databaseName,
            storeV1 = storeV1,
            storeV2 = storeV2,
            encryptedPreferences = preferences,
            substrateSecretStore = substrateSecretStore,
            ethereumSecretStore = ethereumSecretStore
        )
        return ProductionDatabaseStores(
            database = database,
            storeV2 = storeV2,
            substrateSecretStore = substrateSecretStore,
            ethereumSecretStore = ethereumSecretStore
        )
    }

    private data class ProductionDatabaseStores(
        val database: AppDatabase,
        val storeV2: SecretStoreV2,
        val substrateSecretStore: SubstrateSecretStore,
        val ethereumSecretStore: EthereumSecretStore
    )

    private fun assertShippedLegacySchemaMigratesTo77(
        databaseName: String,
        sourceVersion: Int
    ) = runBlocking {
        val preferences = HashMapEncryptedPreferences()
        val storeV1 = SecretStoreV1Impl(preferences)
        val storeV2 = SecretStoreV2(preferences)
        val substrateSecretStore = SubstrateSecretStore(preferences)
        val ethereumSecretStore = EthereumSecretStore(preferences)
        val signingAddress = LEGACY_SUBSTRATE_KEYPAIR.publicKey.toAddress(0)

        storeV1.saveSecuritySource(
            signingAddress,
            SecuritySource.Specified.Mnemonic(
                seed = LEGACY_SUBSTRATE_SEED,
                keypair = LEGACY_SUBSTRATE_KEYPAIR,
                mnemonic = LEGACY_MNEMONIC_WORDS,
                derivationPath = LEGACY_DERIVATION_PATH
            )
        )
        createShippedLegacyDatabase(
            databaseName = databaseName,
            sourceVersion = sourceVersion,
            signingAddress = signingAddress
        )

        val database = AppDatabase.create(
            context = context,
            databaseName = databaseName,
            storeV1 = storeV1,
            storeV2 = storeV2,
            encryptedPreferences = preferences,
            substrateSecretStore = substrateSecretStore,
            ethereumSecretStore = ethereumSecretStore
        )
        try {
            val migrated = database.openHelper.writableDatabase
            assertEquals(77, migrated.version)
            assertEquals(2, migrated.singleInt("SELECT COUNT(*) FROM meta_accounts"))
            assertEquals(1, migrated.singleInt("SELECT COUNT(*) FROM meta_accounts WHERE isSelected = 1"))
            assertEquals(
                LEGACY_REWARD_VALUE,
                migrated.singleString(
                    "SELECT totalReward FROM total_reward WHERE accountAddress = ?",
                    LEGACY_REWARD_ADDRESS
                )
            )

            val signingMetaId = migrated.singleLong(
                "SELECT id FROM meta_accounts WHERE name = ?",
                LEGACY_SIGNING_NAME
            )
            val watchOnlyMetaId = migrated.singleLong(
                "SELECT id FROM meta_accounts WHERE name = ?",
                LEGACY_WATCH_ONLY_NAME
            )
            assertArrayEquals(
                LEGACY_SUBSTRATE_KEYPAIR.publicKey,
                migrated.singleBlob(
                    "SELECT substratePublicKey FROM meta_accounts WHERE id = ?",
                    signingMetaId
                )
            )
            assertArrayEquals(
                LEGACY_WATCH_ONLY_PUBLIC_KEY,
                migrated.singleBlob(
                    "SELECT substratePublicKey FROM meta_accounts WHERE id = ?",
                    watchOnlyMetaId
                )
            )

            val migratedSigningSecrets = requireNotNull(
                substrateSecretStore.get(signingMetaId)
            )
            assertArrayEquals(
                LEGACY_SUBSTRATE_KEYPAIR.publicKey,
                migratedSigningSecrets[SubstrateSecrets.SubstrateKeypair][KeyPairSchema.PublicKey]
            )
            assertNotNull(ethereumSecretStore.get(signingMetaId))
            assertNull(substrateSecretStore.get(watchOnlyMetaId))
            assertNull(ethereumSecretStore.get(watchOnlyMetaId))
            assertNull(storeV2.getMetaAccountSecrets(signingMetaId))
            assertNotNull(storeV1.getSecuritySource(signingAddress))
        } finally {
            database.close()
        }
    }

    /**
     * Exact Room table definitions from the shipped AppDatabase entity lists:
     * v26 commit 44251d519 and v27 commit 0d66165f7. The schema is identical
     * between those releases; v27 only refreshed bundled node rows.
     */
    private fun createShippedLegacyDatabase(
        databaseName: String,
        sourceVersion: Int,
        signingAddress: String
    ) {
        check(sourceVersion == 26 || sourceVersion == 27)
        context.deleteDatabase(databaseName)
        val databaseFile = context.getDatabasePath(databaseName)
        val databaseDirectory = checkNotNull(databaseFile.parentFile) {
            "Shipped legacy fixture database has no parent directory"
        }
        check(
            databaseDirectory.isDirectory ||
                databaseDirectory.mkdirs()
        ) {
            "Could not create the shipped legacy fixture database directory"
        }

        SQLiteDatabase.openOrCreateDatabase(
            databaseFile,
            null
        ).use { db ->
            db.execSQL("PRAGMA foreign_keys = ON")
            SHIPPED_LEGACY_SCHEMA_SQL.forEach(db::execSQL)
            db.version = sourceVersion

            db.execSQL(
                """
                INSERT INTO users(address, username, publicKey, cryptoType, position, networkType)
                VALUES(?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any>(
                    signingAddress,
                    LEGACY_SIGNING_NAME,
                    LEGACY_SUBSTRATE_KEYPAIR.publicKey.toPlainHexString(),
                    mapEncryptionToCryptoType(LEGACY_CRYPTO_TYPE).ordinal,
                    0,
                    0
                )
            )
            db.execSQL(
                """
                INSERT INTO users(address, username, publicKey, cryptoType, position, networkType)
                VALUES(?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any>(
                    LEGACY_WATCH_ONLY_PUBLIC_KEY.toAddress(0),
                    LEGACY_WATCH_ONLY_NAME,
                    LEGACY_WATCH_ONLY_PUBLIC_KEY.toPlainHexString(),
                    mapEncryptionToCryptoType(LEGACY_CRYPTO_TYPE).ordinal,
                    1,
                    0
                )
            )
            db.execSQL(
                """
                INSERT INTO nodes(name, link, networkType, isDefault, isActive)
                VALUES(?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any>("Legacy node", "wss://legacy.invalid", 0, 1, 1)
            )
            db.execSQL(
                "INSERT INTO tokens(type, dollarRate, recentRateChange) VALUES(?, ?, ?)",
                arrayOf<Any>(0, "123.45", "-0.75")
            )
            db.execSQL(
                """
                INSERT INTO assets(
                    token,
                    accountAddress,
                    freeInPlanks,
                    reservedInPlanks,
                    miscFrozenInPlanks,
                    feeFrozenInPlanks,
                    bondedInPlanks,
                    redeemableInPlanks,
                    unbondingInPlanks
                ) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any>(0, signingAddress, "1", "2", "3", "4", "5", "6", "7")
            )
            db.execSQL(
                """
                INSERT INTO runtimeCache(
                    networkName,
                    latestKnownVersion,
                    latestAppliedVersion,
                    typesVersion
                ) VALUES(?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any>("legacy-runtime", 99, 98, 7)
            )
            db.execSQL(
                "INSERT INTO phishing_addresses(publicKey) VALUES(?)",
                arrayOf<Any>("legacy-phishing-key")
            )
            db.execSQL(
                """
                INSERT INTO storage(storageKey, networkType, content, runtimeVersion)
                VALUES(?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any>("legacy-storage-key", 0, "legacy-content", 98)
            )
            db.execSQL(
                """
                INSERT INTO account_staking_accesses(address, stashId, controllerId)
                VALUES(?, ?, ?)
                """.trimIndent(),
                arrayOf<Any>(signingAddress, byteArrayOf(1, 2), byteArrayOf(3, 4))
            )
            db.execSQL(
                "INSERT INTO total_reward(accountAddress, totalReward) VALUES(?, ?)",
                arrayOf<Any>(LEGACY_REWARD_ADDRESS, LEGACY_REWARD_VALUE)
            )
            db.execSQL(
                """
                INSERT INTO operations(
                    id,
                    address,
                    time,
                    tokenType,
                    status,
                    source,
                    operationType
                ) VALUES(?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any>("legacy-operation", signingAddress, Long.MAX_VALUE, 0, 1, 2, 0)
            )
        }
    }

    private fun createVersion76DatabaseWithUserData(databaseName: String) {
        helper.createDatabase(databaseName, 76).apply {
            insertRepresentativeUserData()
            close()
        }
    }

    private fun openWithProductionPolicyExpectingFailure(databaseName: String): Throwable {
        val preferences = HashMapEncryptedPreferences()
        var database: AppDatabase? = null

        return try {
            val failure = assertThrows(IllegalStateException::class.java) {
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
            }
            failure
        } finally {
            database?.close()
        }
    }

    private fun setDatabaseVersion(databaseName: String, version: Int) {
        SQLiteDatabase.openDatabase(
            context.getDatabasePath(databaseName).path,
            null,
            SQLiteDatabase.OPEN_READWRITE
        ).use { db ->
            db.version = version
        }
    }

    private fun assertRawUserDataPreserved(databaseName: String, expectedVersion: Int) {
        openRawReadOnly(databaseName).use { db ->
            assertEquals(expectedVersion, db.version)
            assertEquals(1, db.singleInt("SELECT COUNT(*) FROM meta_accounts WHERE id = $META_ID"))
            assertEquals(
                ADVERSARIAL_WALLET_NAME,
                db.singleString("SELECT name FROM meta_accounts WHERE id = $META_ID")
            )
        }
    }

    private fun openRawReadOnly(databaseName: String): SQLiteDatabase =
        SQLiteDatabase.openDatabase(
            context.getDatabasePath(databaseName).path,
            null,
            SQLiteDatabase.OPEN_READONLY
        )

    private fun SupportSQLiteDatabase.insertRepresentativeUserData() {
        execSQL(
            """
            INSERT INTO meta_accounts(
                id,
                name,
                isSelected,
                position,
                isBackedUp,
                initialized
            ) VALUES(?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any>(META_ID, ADVERSARIAL_WALLET_NAME, 1, 4, 1, 1)
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
                ADVERSARIAL_CHAIN_NAME,
                "fixture-icon",
                42,
                0,
                0,
                1,
                1,
                0,
                0,
                1,
                0,
                "SUBSTRATE"
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
                staking,
                precision
            ) VALUES(?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any>("asset-fixture", "Fixture Asset", "FIX", CHAIN_ID, "asset-icon", "ALLOWED", 12)
        )
        execSQL(
            """
            INSERT INTO assets(
                id,
                chainId,
                accountId,
                metaId,
                sortIndex,
                markedNotNeed
            ) VALUES(?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any>("asset-fixture", CHAIN_ID, ADVERSARIAL_ACCOUNT_ID, META_ID, Int.MAX_VALUE, 0)
        )
        execSQL(
            """
            INSERT INTO address_book(address, name, chainId, created)
            VALUES(?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any>("fixture-address", "Recipient ' \" ; -- 雪", CHAIN_ID, Long.MAX_VALUE)
        )
    }

    private fun SupportSQLiteDatabase.assertRepresentativeUserData() {
        assertEquals(1, singleInt("SELECT COUNT(*) FROM meta_accounts WHERE id = $META_ID"))
        assertEquals(1, singleInt("SELECT COUNT(*) FROM chains WHERE id = '$CHAIN_ID'"))
        assertEquals(1, singleInt("SELECT COUNT(*) FROM chain_assets WHERE chainId = '$CHAIN_ID'"))
        assertEquals(1, singleInt("SELECT COUNT(*) FROM assets WHERE metaId = $META_ID"))
        assertEquals(1, singleInt("SELECT COUNT(*) FROM address_book WHERE chainId = '$CHAIN_ID'"))
    }

    private fun SupportSQLiteDatabase.tableExists(tableName: String): Boolean =
        singleInt(
            "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = '$tableName'"
        ) == 1

    private fun SupportSQLiteDatabase.singleInt(sql: String): Int =
        query(sql).use { cursor ->
            check(cursor.moveToFirst())
            cursor.getInt(0)
        }

    private fun SupportSQLiteDatabase.singleString(
        sql: String,
        vararg bindArgs: Any?
    ): String =
        query(sql, bindArgs).use { cursor ->
            check(cursor.moveToFirst())
            cursor.getString(0)
        }

    private fun SupportSQLiteDatabase.singleNullableString(sql: String): String? =
        query(sql).use { cursor ->
            check(cursor.moveToFirst())
            if (cursor.isNull(0)) null else cursor.getString(0)
        }

    private fun SupportSQLiteDatabase.singleLong(
        sql: String,
        vararg bindArgs: Any?
    ): Long =
        query(sql, bindArgs).use { cursor ->
            check(cursor.moveToFirst())
            cursor.getLong(0)
        }

    private fun SupportSQLiteDatabase.singleBlob(
        sql: String,
        vararg bindArgs: Any?
    ): ByteArray =
        query(sql, bindArgs).use { cursor ->
            check(cursor.moveToFirst())
            cursor.getBlob(0)
        }

    private fun SQLiteDatabase.singleInt(sql: String): Int =
        rawQuery(sql, null).use { cursor ->
            check(cursor.moveToFirst())
            cursor.getInt(0)
        }

    private fun SQLiteDatabase.singleString(sql: String): String =
        rawQuery(sql, null).use { cursor ->
            check(cursor.moveToFirst())
            cursor.getString(0)
        }

    private companion object {
        const val VERSION_73_DATABASE = "migration-safety-73"
        const val VERSION_74_DATABASE = "migration-safety-74"
        const val VERSION_75_DATABASE = "migration-safety-75"
        const val MALFORMED_DATABASE = "migration-safety-malformed"
        const val FUTURE_DATABASE = "migration-safety-future"
        const val ANCIENT_DATABASE = "migration-safety-ancient"
        const val VERSION_9_DATABASE = "migration-safety-released-v04-version-9"
        const val VERSION_9_CORRUPT_DATABASE =
            "migration-safety-released-v04-version-9-corrupt"
        const val VERSION_26_DATABASE = "migration-safety-26"
        const val VERSION_27_DATABASE = "migration-safety-27"
        const val META_ID = 7L
        const val CHAIN_ID = "fixture-chain"
        const val ADVERSARIAL_WALLET_NAME = "Wallet ' \" ; -- \u0000 雪 \uD83D\uDD10"
        const val ADVERSARIAL_CHAIN_NAME = "Chain ' \" ; DROP TABLE chains; -- \uD83D\uDEE1"
        const val LEGACY_REWARD_ADDRESS = "legacy-address-'\";--"
        const val LEGACY_REWARD_VALUE = "999999999999999999999999999999"
        const val LEGACY_MNEMONIC_WORDS = "bottom drive obey lake curtain smoke basket hold race lonely fit walk"
        const val LEGACY_DERIVATION_PATH = "//legacy"
        const val LEGACY_SIGNING_NAME = "Legacy signing wallet ' \" ; -- 雪"
        const val LEGACY_WATCH_ONLY_NAME = "Legacy watch-only wallet \uD83D\uDD10"
        const val RELEASED_V04_MNEMONIC_WORDS =
            "bottom drive obey lake curtain smoke basket hold race lonely fit walk"
        const val RELEASED_V04_DERIVATION_PATH = "//released-v04"
        const val RELEASED_V04_SIGNING_NAME =
            "Released 0.4 signing wallet ' \" ; -- 雪 \uD83D\uDD10"
        const val RELEASED_V04_WATCH_ONLY_NAME =
            "Released 0.4 watch-only wallet ' \" ; -- \uD83D\uDEE1"
        const val RELEASED_V04_CORRUPT_NAME =
            "Released 0.4 corrupt wallet retained for recovery"

        val LEGACY_CRYPTO_TYPE = EncryptionType.SR25519
        val LEGACY_DECODED_DERIVATION_PATH =
            SubstrateJunctionDecoder.decode(LEGACY_DERIVATION_PATH)
        val LEGACY_SUBSTRATE_SEED = SubstrateSeedFactory.deriveSeed32(
            LEGACY_MNEMONIC_WORDS,
            password = LEGACY_DECODED_DERIVATION_PATH.password
        ).seed
        val LEGACY_SUBSTRATE_KEYPAIR = SubstrateKeypairFactory.generate(
            LEGACY_CRYPTO_TYPE,
            seed = LEGACY_SUBSTRATE_SEED,
            junctions = LEGACY_DECODED_DERIVATION_PATH.junctions
        )
        val LEGACY_WATCH_ONLY_PUBLIC_KEY = ByteArray(32) { (it + 91).toByte() }

        val RELEASED_V04_CRYPTO_TYPE = EncryptionType.SR25519
        val RELEASED_V04_MNEMONIC =
            MnemonicCreator.fromWords(RELEASED_V04_MNEMONIC_WORDS)
        val RELEASED_V04_ENTROPY = RELEASED_V04_MNEMONIC.entropy
        val RELEASED_V04_DECODED_DERIVATION_PATH =
            SubstrateJunctionDecoder.decode(RELEASED_V04_DERIVATION_PATH)
        val RELEASED_V04_SUBSTRATE_SEED =
            SubstrateSeedFactory.deriveSeed32(
                RELEASED_V04_MNEMONIC.words,
                password = RELEASED_V04_DECODED_DERIVATION_PATH.password
            ).seed
        val RELEASED_V04_SUBSTRATE_KEYPAIR =
            SubstrateKeypairFactory.generate(
                RELEASED_V04_CRYPTO_TYPE,
                seed = RELEASED_V04_SUBSTRATE_SEED,
                junctions = RELEASED_V04_DECODED_DERIVATION_PATH.junctions
            )
        val RELEASED_V04_SIGNING_ADDRESS =
            RELEASED_V04_SUBSTRATE_KEYPAIR.publicKey.toAddress(0)
        val RELEASED_V04_WATCH_ONLY_PUBLIC_KEY =
            ByteArray(32) { (it + 119).toByte() }
        val RELEASED_V04_WATCH_ONLY_ADDRESS =
            RELEASED_V04_WATCH_ONLY_PUBLIC_KEY.toAddress(0)
        val RELEASED_V04_CORRUPT_PUBLIC_KEY =
            ByteArray(32) { (it * 7 + 31).toByte() }
        val RELEASED_V04_ETHEREUM_DERIVATION_PATH =
            BIP32JunctionDecoder.decode(BIP32JunctionDecoder.DEFAULT_DERIVATION_PATH)
        val RELEASED_V04_ETHEREUM_SEED =
            EthereumSeedFactory.deriveSeed32(
                RELEASED_V04_MNEMONIC.words,
                password = RELEASED_V04_ETHEREUM_DERIVATION_PATH.password
            ).seed
        val RELEASED_V04_EXPECTED_ETHEREUM_KEYPAIR =
            EthereumKeypairFactory.generate(
                RELEASED_V04_ETHEREUM_SEED,
                junctions = RELEASED_V04_ETHEREUM_DERIVATION_PATH.junctions
            )

        val SHIPPED_LEGACY_SCHEMA_SQL = listOf(
            """
            CREATE TABLE IF NOT EXISTS `users` (
                `address` TEXT NOT NULL,
                `username` TEXT NOT NULL,
                `publicKey` TEXT NOT NULL,
                `cryptoType` INTEGER NOT NULL,
                `position` INTEGER NOT NULL,
                `networkType` INTEGER NOT NULL,
                PRIMARY KEY(`address`)
            )
            """.trimIndent(),
            """
            CREATE TABLE IF NOT EXISTS `nodes` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `link` TEXT NOT NULL,
                `networkType` INTEGER NOT NULL,
                `isDefault` INTEGER NOT NULL,
                `isActive` INTEGER NOT NULL
            )
            """.trimIndent(),
            """
            CREATE TABLE IF NOT EXISTS `tokens` (
                `type` INTEGER NOT NULL,
                `dollarRate` TEXT,
                `recentRateChange` TEXT,
                PRIMARY KEY(`type`)
            )
            """.trimIndent(),
            """
            CREATE TABLE IF NOT EXISTS `assets` (
                `token` INTEGER NOT NULL,
                `accountAddress` TEXT NOT NULL,
                `freeInPlanks` TEXT NOT NULL,
                `reservedInPlanks` TEXT NOT NULL,
                `miscFrozenInPlanks` TEXT NOT NULL,
                `feeFrozenInPlanks` TEXT NOT NULL,
                `bondedInPlanks` TEXT NOT NULL,
                `redeemableInPlanks` TEXT NOT NULL,
                `unbondingInPlanks` TEXT NOT NULL,
                PRIMARY KEY(`token`, `accountAddress`),
                FOREIGN KEY(`token`) REFERENCES `tokens`(`type`)
                    ON UPDATE NO ACTION ON DELETE NO ACTION
            )
            """.trimIndent(),
            "CREATE INDEX IF NOT EXISTS `index_assets_accountAddress` ON `assets` (`accountAddress`)",
            """
            CREATE TABLE IF NOT EXISTS `runtimeCache` (
                `networkName` TEXT NOT NULL,
                `latestKnownVersion` INTEGER NOT NULL,
                `latestAppliedVersion` INTEGER NOT NULL,
                `typesVersion` INTEGER NOT NULL,
                PRIMARY KEY(`networkName`)
            )
            """.trimIndent(),
            """
            CREATE TABLE IF NOT EXISTS `phishing_addresses` (
                `publicKey` TEXT NOT NULL,
                PRIMARY KEY(`publicKey`)
            )
            """.trimIndent(),
            """
            CREATE TABLE IF NOT EXISTS `storage` (
                `storageKey` TEXT NOT NULL,
                `networkType` INTEGER NOT NULL,
                `content` TEXT,
                `runtimeVersion` INTEGER NOT NULL,
                PRIMARY KEY(`storageKey`, `networkType`)
            )
            """.trimIndent(),
            """
            CREATE TABLE IF NOT EXISTS `account_staking_accesses` (
                `address` TEXT NOT NULL,
                `stashId` BLOB,
                `controllerId` BLOB,
                PRIMARY KEY(`address`),
                FOREIGN KEY(`address`) REFERENCES `users`(`address`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
            """
            CREATE TABLE IF NOT EXISTS `total_reward` (
                `accountAddress` TEXT NOT NULL,
                `totalReward` TEXT NOT NULL,
                PRIMARY KEY(`accountAddress`)
            )
            """.trimIndent(),
            """
            CREATE TABLE IF NOT EXISTS `operations` (
                `id` TEXT NOT NULL,
                `address` TEXT NOT NULL,
                `time` INTEGER NOT NULL,
                `tokenType` INTEGER NOT NULL,
                `status` INTEGER NOT NULL,
                `source` INTEGER NOT NULL,
                `operationType` INTEGER NOT NULL,
                `module` TEXT,
                `call` TEXT,
                `amount` TEXT,
                `sender` TEXT,
                `receiver` TEXT,
                `hash` TEXT,
                `fee` TEXT,
                `isReward` INTEGER,
                `era` INTEGER,
                `validator` TEXT,
                PRIMARY KEY(`id`, `address`)
            )
            """.trimIndent(),
            // Frozen from AppDatabase_Impl in the official DB26 and DB27
            // release APKs; those shipped commits did not export Room JSON.
            """
            CREATE TABLE IF NOT EXISTS room_master_table (
                id INTEGER PRIMARY KEY,
                identity_hash TEXT
            )
            """.trimIndent(),
            """
            INSERT OR REPLACE INTO room_master_table(id, identity_hash)
            VALUES(42, '4398623385060349aacdf135450ede28')
            """.trimIndent()
        )

        val ADVERSARIAL_ACCOUNT_ID = byteArrayOf(
            0,
            1,
            2,
            0x7F,
            0x80.toByte(),
            0xFF.toByte()
        )

        val DATABASE_NAMES = listOf(
            VERSION_73_DATABASE,
            VERSION_74_DATABASE,
            VERSION_75_DATABASE,
            MALFORMED_DATABASE,
            FUTURE_DATABASE,
            ANCIENT_DATABASE,
            VERSION_9_DATABASE,
            VERSION_9_CORRUPT_DATABASE,
            VERSION_26_DATABASE,
            VERSION_27_DATABASE
        )
    }
}
