package jp.co.soramitsu.coredb.migrations

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.migration.Migration
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import jp.co.soramitsu.common.data.secrets.legacy.LegacyWalletV04Secrets
import jp.co.soramitsu.common.data.secrets.v1.SecretStoreV1
import jp.co.soramitsu.common.data.secrets.v1.SecretStoreV1Impl
import jp.co.soramitsu.common.data.secrets.v2.KeyPairSchema
import jp.co.soramitsu.common.data.secrets.v2.MetaAccountSecrets
import jp.co.soramitsu.common.data.secrets.v2.SecretStoreV2
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecretStore
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecretStore
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretConcurrentMutationException
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretQuarantine
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
import jp.co.soramitsu.coredb.APP_DATABASE_VERSION
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
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

private const val DERIVATION_PATH = "//test"
private val SUBSTRATE_DERIVATION =
    SubstrateJunctionDecoder.decode(DERIVATION_PATH)
private val SUBSTRATE_KEYPAIR = SubstrateKeypairFactory.generate(
    encryptionType = CRYPTO_TYPE,
    seed = SUBSTRATE_SEED,
    junctions = SUBSTRATE_DERIVATION.junctions
)
private val RELEASED_EXISTING_KEYPAIR = SubstrateKeypairFactory.generate(
    encryptionType = CRYPTO_TYPE,
    seed = ByteArray(32) { index -> (index + 17).toByte() },
    junctions = emptyList()
)
private val SECOND_SUBSTRATE_KEYPAIR = SubstrateKeypairFactory.generate(
    encryptionType = CRYPTO_TYPE,
    seed = ByteArray(32) { index -> (index + 91).toByte() },
    junctions = emptyList()
)

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

    private val instrumentation =
        InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private var migratedDatabaseHelper: SupportSQLiteOpenHelper? = null

    @get:Rule
    val releasedSchemaHelper = MigrationTestHelper(
        instrumentation,
        RELEASED_DATABASE_CANONICAL_NAME,
        FrameworkSQLiteOpenHelperFactory()
    )

    private val encryptedPreferences = HashMapEncryptedPreferences()
    private val storeV1 = SecretStoreV1Impl(encryptedPreferences)
    private val storeV2 = SecretStoreV2(encryptedPreferences)
    private val migration = V2Migration(storeV1, encryptedPreferences)

    @After
    fun closeDatabase() {
        migratedDatabaseHelper?.close()
        context.deleteDatabase(TEST_DB)
        context.deleteDatabase(FULL_UPGRADE_DB)
        context.deleteDatabase(STALE_V31_DB)
        context.deleteDatabase(STALE_V34_DB)
        context.deleteDatabase(QUARANTINE_V31_DB)
        context.deleteDatabase(QUARANTINE_V71_DB)
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

    @Test
    fun releasedVersion28FixtureMatchesPublishedRoomArtifactAndTopology() {
        assertReleasedVersion28Asset()
        context.deleteDatabase(TEST_DB)

        releasedSchemaHelper.createDatabase(
            TEST_DB,
            V2_START_VERSION
        ).use { db ->
            assertEquals(V2_START_VERSION, db.version)
            db.assertReleasedVersion28Topology()
            db.query("PRAGMA integrity_check").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("ok", cursor.getString(0))
                assertFalse(cursor.moveToNext())
            }
        }
    }

    @Test
    fun version28To29PreservesEveryReleasedLegacyTableRow() = runBlocking {
        storeV1.insertSecrets(Type.MNEMONIC)

        val db = performMigration {
            insertReleasedLegacyRows()
            insertAccount(
                publicKey = SUBSTRATE_KEYPAIR.publicKey,
                encryptionType = CRYPTO_TYPE
            )
        }

        RELEASED_TABLES.forEach { table ->
            assertEquals(
                "Released row was not preserved in $table",
                if (table == "meta_accounts") 2L else 1L,
                db.singleLong("SELECT COUNT(*) FROM `$table`")
            )
        }
        assertEquals(
            "released-operation",
            db.singleString("SELECT id FROM operations")
        )
        assertEquals(
            "wss://released.invalid",
            db.singleString("SELECT url FROM chain_nodes")
        )
        assertEquals(
            "released-content",
            db.singleString("SELECT content FROM storage")
        )
        db.query("PRAGMA foreign_key_check").use { cursor ->
            assertFalse(cursor.moveToFirst())
        }
    }

    @Test
    fun releasedVersion28UpgradesThroughProductionGraphToCurrentRoomSchema() =
        runBlocking {
            storeV1.insertSecrets(Type.MNEMONIC)
            context.deleteDatabase(FULL_UPGRADE_DB)
            releasedSchemaHelper.createDatabase(
                FULL_UPGRADE_DB,
                V2_START_VERSION
            ).use { db ->
                db.assertReleasedVersion28Topology()
                // Populate every released-v28 table, including a deliberately
                // stale asset/chain-asset symbol join. Cache rows may be
                // defaulted or pruned by their documented refresh migration;
                // wallet identities and the recovery ledger must survive.
                db.insertReleasedLegacyRows()
                db.insertAccount(
                    publicKey = SUBSTRATE_KEYPAIR.publicKey,
                    encryptionType = CRYPTO_TYPE
                )
            }

            val roomDatabase = AppDatabase.create(
                context = context,
                databaseName = FULL_UPGRADE_DB,
                storeV1 = storeV1,
                storeV2 = storeV2,
                encryptedPreferences = encryptedPreferences,
                substrateSecretStore =
                SubstrateSecretStore(encryptedPreferences),
                ethereumSecretStore =
                EthereumSecretStore(encryptedPreferences)
            )
            try {
                val db = roomDatabase.openHelper.writableDatabase
                assertEquals(APP_DATABASE_VERSION, db.version)
                assertEquals(
                    2L,
                    db.singleLong("SELECT COUNT(*) FROM meta_accounts")
                )
                assertEquals(
                    1L,
                    db.singleLong(
                        "SELECT COUNT(*) FROM legacy_users_recovery"
                    )
                )
                assertEquals(
                    SUBSTRATE_KEYPAIR.publicKey.toPlainHexString(
                        withPrefix = false
                    ),
                    db.singleString(
                        "SELECT publicKey FROM legacy_users_recovery"
                    )
                )
                assertEquals(
                    "PRESERVED_PENDING_REVIEW",
                    db.singleString(
                        "SELECT recoveryState FROM legacy_users_recovery"
                    )
                )
                assertArrayEquals(
                    SUBSTRATE_KEYPAIR.publicKey,
                    db.singleBlob(
                        "SELECT substratePublicKey FROM meta_accounts " +
                            "WHERE id = $MIGRATED_USER_META_ID"
                    )
                )
                assertArrayEquals(
                    RELEASED_EXISTING_KEYPAIR.publicKey,
                    db.singleBlob(
                        "SELECT substratePublicKey FROM meta_accounts " +
                            "WHERE id = $RELEASED_EXISTING_META_ID"
                    )
                )
                // Registry/cache refresh at 45 -> 46 intentionally prunes
                // version-28 balances and registry rows rather than allowing
                // stale unmatched rows to block the wallet upgrade.
                assertEquals(
                    0L,
                    db.singleLong("SELECT COUNT(*) FROM assets")
                )
                assertEquals(
                    0L,
                    db.singleLong("SELECT COUNT(*) FROM chain_assets")
                )
                assertFalse(
                    encryptedPreferences.hasKey(
                        "$MIGRATED_USER_META_ID:ACCESS_SECRETS"
                    )
                )
                assertTrue(
                    encryptedPreferences.hasKey(
                        "$MIGRATED_USER_META_ID:SUBSTRATE_SECRETS"
                    )
                )
                assertTrue(
                    encryptedPreferences.hasKey(
                        "$MIGRATED_USER_META_ID:ETHEREUM_SECRETS"
                    )
                )
                db.query("PRAGMA foreign_key_check").use { cursor ->
                    assertFalse(cursor.moveToFirst())
                }
                db.query("PRAGMA integrity_check").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("ok", cursor.getString(0))
                    assertFalse(cursor.moveToNext())
                }
            } finally {
                roomDatabase.close()
            }
        }

    @Test
    fun corruptDerivedTargetRollsBackSqlWithoutAnyPreferenceMutation() =
        runBlocking {
            storeV1.insertSecrets(Type.MNEMONIC)
            val address = SUBSTRATE_KEYPAIR.publicKey.toAddress(0)
            val legacySourceKey = "security_source_$address"
            val originalLegacySource = checkNotNull(
                encryptedPreferences.getDecryptedString(legacySourceKey)
            )
            val derivedTargetKey = "1:ACCESS_SECRETS"
            val corruptDerivedTarget = "malformed-derived-wallet-material"
            encryptedPreferences.putEncryptedString(
                derivedTargetKey,
                corruptDerivedTarget
            )
            encryptedPreferences.putEncryptedString(
                PREFERENCE_SENTINEL_KEY,
                PREFERENCE_SENTINEL_VALUE
            )

            context.deleteDatabase(TEST_DB)
            releasedSchemaHelper.createDatabase(
                TEST_DB,
                V2_START_VERSION
            ).use { db ->
                db.assertReleasedVersion28Topology()
                db.insertAccount(
                    publicKey = SUBSTRATE_KEYPAIR.publicKey,
                    encryptionType = CRYPTO_TYPE
                )
            }

            val failingHelper = createOpenHelper(
                databaseName = TEST_DB,
                version = V2_END_VERSION,
                createSchema = {
                    error("Expected the released version-28 fixture")
                },
                upgrade = { db, oldVersion, newVersion ->
                    assertEquals(V2_START_VERSION, oldVersion)
                    assertEquals(V2_END_VERSION, newVersion)
                    assertTrue(db.inTransaction())
                    migration.migrate(db)
                }
            )
            try {
                assertThrows(
                    WalletSecretConcurrentMutationException::class.java
                ) {
                    failingHelper.writableDatabase
                }
            } finally {
                failingHelper.close()
            }

            assertEquals(
                originalLegacySource,
                encryptedPreferences.getDecryptedString(legacySourceKey)
            )
            assertEquals(
                corruptDerivedTarget,
                encryptedPreferences.getDecryptedString(derivedTargetKey)
            )
            assertEquals(
                PREFERENCE_SENTINEL_VALUE,
                encryptedPreferences.getDecryptedString(
                    PREFERENCE_SENTINEL_KEY
                )
            )
            assertFalse(
                encryptedPreferences.hasKey(
                    WalletSecretQuarantine.keyFor(legacySourceKey)
                )
            )
            assertFalse(
                encryptedPreferences.hasKey("1:SUBSTRATE_SECRETS")
            )
            assertFalse(
                encryptedPreferences.hasKey("1:ETHEREUM_SECRETS")
            )
            assertFalse(encryptedPreferences.hasKey("1:TON_SECRETS"))

            createOpenHelper(
                databaseName = TEST_DB,
                version = V2_START_VERSION,
                createSchema = {
                    error("Expected the rolled-back version-28 fixture")
                }
            ).use { rollbackHelper ->
                rollbackHelper.writableDatabase.use { db ->
                    assertEquals(V2_START_VERSION, db.version)
                    assertEquals(
                        1L,
                        db.singleLong("SELECT COUNT(*) FROM users")
                    )
                    assertEquals(
                        0L,
                        db.singleLong(
                            "SELECT COUNT(*) FROM meta_accounts"
                        )
                    )
                    db.assertReleasedVersion28Topology()
                    db.query("PRAGMA integrity_check").use { cursor ->
                        assertTrue(cursor.moveToFirst())
                        assertEquals("ok", cursor.getString(0))
                    }
                }
            }
        }

    @Test
    fun version31IgnoresSchemaValidOversizedUnmatchedAssetAccountId() =
        runBlocking {
            storeV1.insertSecrets(Type.MNEMONIC)
            createReleasedV28WalletDatabase(STALE_V31_DB)
            migrateExistingDatabase(
                databaseName = STALE_V31_DB,
                targetVersion = 31,
                migrations = migrationsFrom28Through31()
            ).use { helper ->
                helper.writableDatabase.execSQL(
                    """
                    INSERT INTO assets(
                        tokenSymbol, chainId, accountId, metaId,
                        freeInPlanks, reservedInPlanks, miscFrozenInPlanks,
                        feeFrozenInPlanks, bondedInPlanks,
                        redeemableInPlanks, unbondingInPlanks
                    ) VALUES(
                        'STALE', 'missing-chain', ?, 1,
                        '0', '0', '0', '0', '0', '0', '0'
                    )
                    """.trimIndent(),
                    arrayOf<Any>(ByteArray(4_096) { 0x5a })
                )
            }

            migrateExistingDatabase(
                databaseName = STALE_V31_DB,
                targetVersion = 32,
                migrations = listOf(
                    EthereumDerivationPathMigration(encryptedPreferences)
                )
            ).use { helper ->
                val db = helper.writableDatabase
                assertEquals(32, db.version)
                assertEquals(
                    4_096,
                    db.singleBlob(
                        "SELECT accountId FROM assets " +
                            "WHERE tokenSymbol = 'STALE'"
                    ).size
                )
            }
        }

    @Test
    fun version31LateQuarantineConflictLeavesEarlierWalletUntouched() =
        runBlocking {
            storeV1.insertSecrets(Type.MNEMONIC)
            storeV1.saveSecuritySource(
                SECOND_SUBSTRATE_KEYPAIR.publicKey.toAddress(0),
                SecuritySource.Unspecified(
                    keypair = SECOND_SUBSTRATE_KEYPAIR
                )
            )
            context.deleteDatabase(QUARANTINE_V31_DB)
            releasedSchemaHelper.createDatabase(
                QUARANTINE_V31_DB,
                V2_START_VERSION
            ).use { db ->
                db.insertAccount(SUBSTRATE_KEYPAIR.publicKey, CRYPTO_TYPE)
                db.insertAccount(
                    SECOND_SUBSTRATE_KEYPAIR.publicKey,
                    CRYPTO_TYPE
                )
            }

            migrateExistingDatabase(
                databaseName = QUARANTINE_V31_DB,
                targetVersion = 31,
                migrations = migrationsFrom28Through31()
            ).use { helper ->
                helper.writableDatabase
            }

            val firstActiveKey = "1:ACCESS_SECRETS"
            val firstTargetKey = "1:SUBSTRATE_SECRETS"
            val secondActiveKey = "2:ACCESS_SECRETS"
            val secondQuarantineKey =
                WalletSecretQuarantine.keyFor(secondActiveKey)
            val secondCorruptSecret = "late-db31-corrupt-secret"
            val conflictingQuarantine = "conflicting-db31-quarantine"
            val firstOriginal = checkNotNull(
                encryptedPreferences.getDecryptedString(firstActiveKey)
            )
            encryptedPreferences.putEncryptedString(
                secondActiveKey,
                secondCorruptSecret
            )
            encryptedPreferences.putEncryptedString(
                secondQuarantineKey,
                conflictingQuarantine
            )

            val helper = migrateExistingDatabase(
                databaseName = QUARANTINE_V31_DB,
                targetVersion = 32,
                migrations = listOf(
                    EthereumDerivationPathMigration(encryptedPreferences)
                )
            )
            try {
                assertThrows(
                    WalletSecretConcurrentMutationException::class.java
                ) {
                    helper.writableDatabase
                }
            } finally {
                helper.close()
            }

            assertEquals(
                firstOriginal,
                encryptedPreferences.getDecryptedString(firstActiveKey)
            )
            assertFalse(encryptedPreferences.hasKey(firstTargetKey))
            assertEquals(
                secondCorruptSecret,
                encryptedPreferences.getDecryptedString(secondActiveKey)
            )
            assertEquals(
                conflictingQuarantine,
                encryptedPreferences.getDecryptedString(
                    secondQuarantineKey
                )
            )
        }

    @Test
    fun version34IgnoresUnconsumedOutOfRangeCachePayloads() =
        runBlocking {
            storeV1.insertSecrets(Type.MNEMONIC)
            createReleasedV28WalletDatabase(STALE_V34_DB)
            migrateExistingDatabase(
                databaseName = STALE_V34_DB,
                targetVersion = 34,
                migrations = migrationsFrom28Through34()
            ).use { helper ->
                helper.writableDatabase.insertVersion34AssetOrderFixtures()
            }

            migrateExistingDatabase(
                databaseName = STALE_V34_DB,
                targetVersion = 35,
                migrations = listOf(AssetsOrderMigration())
            ).use { helper ->
                val db = helper.writableDatabase
                assertEquals(35, db.version)
                assertEquals(
                    9_999L,
                    db.singleLong(
                        "SELECT precision FROM chain_assets " +
                            "WHERE id = 'STALE'"
                    )
                )
                assertEquals(
                    4_096,
                    db.singleBlob(
                        "SELECT accountId FROM assets " +
                            "WHERE tokenSymbol = 'UNMATCHED'"
                    ).size
                )
                assertEquals(
                    0L,
                    db.singleLong(
                        "SELECT sortIndex FROM assets " +
                            "WHERE tokenSymbol = 'UNMATCHED'"
                    )
                )
                assertEquals(
                    1L,
                    db.singleLong(
                        "SELECT enabled FROM assets " +
                            "WHERE tokenSymbol = 'UNMATCHED'"
                    )
                )
            }
        }

    @Test
    fun version71LateQuarantineConflictLeavesEarlierWalletUntouched() =
        runBlocking {
            storeV1.insertSecrets(Type.MNEMONIC)
            storeV1.saveSecuritySource(
                SECOND_SUBSTRATE_KEYPAIR.publicKey.toAddress(0),
                SecuritySource.Unspecified(
                    keypair = SECOND_SUBSTRATE_KEYPAIR
                )
            )
            context.deleteDatabase(QUARANTINE_V71_DB)
            releasedSchemaHelper.createDatabase(
                QUARANTINE_V71_DB,
                V2_START_VERSION
            ).use { db ->
                db.insertAccount(SUBSTRATE_KEYPAIR.publicKey, CRYPTO_TYPE)
                db.insertAccount(
                    SECOND_SUBSTRATE_KEYPAIR.publicKey,
                    CRYPTO_TYPE
                )
            }
            migrateExistingDatabase(
                databaseName = QUARANTINE_V71_DB,
                targetVersion = 71,
                migrations = migrationsFrom28Through71()
            ).use { helper ->
                helper.writableDatabase
            }

            val firstActiveKey = "1:ACCESS_SECRETS"
            val firstTargetKey = "1:SUBSTRATE_SECRETS"
            val secondActiveKey = "2:ACCESS_SECRETS"
            val secondQuarantineKey =
                WalletSecretQuarantine.keyFor(secondActiveKey)
            val secondCorruptSecret = "late-ton-corrupt-secret"
            val conflictingQuarantine = "conflicting-ton-quarantine"
            val firstOriginal = checkNotNull(
                encryptedPreferences.getDecryptedString(firstActiveKey)
            )
            encryptedPreferences.putEncryptedString(
                secondActiveKey,
                secondCorruptSecret
            )
            encryptedPreferences.putEncryptedString(
                secondQuarantineKey,
                conflictingQuarantine
            )

            val helper = migrateExistingDatabase(
                databaseName = QUARANTINE_V71_DB,
                targetVersion = 72,
                migrations = listOf(TonMigration(encryptedPreferences))
            )
            try {
                assertThrows(
                    WalletSecretConcurrentMutationException::class.java
                ) {
                    helper.writableDatabase
                }
            } finally {
                helper.close()
            }

            assertEquals(
                firstOriginal,
                encryptedPreferences.getDecryptedString(firstActiveKey)
            )
            assertFalse(encryptedPreferences.hasKey(firstTargetKey))
            assertEquals(
                secondCorruptSecret,
                encryptedPreferences.getDecryptedString(secondActiveKey)
            )
            assertEquals(
                conflictingQuarantine,
                encryptedPreferences.getDecryptedString(
                    secondQuarantineKey
                )
            )
        }

    private suspend fun performSingleAccountTest(
        insertionType: Type,
        withEntropy: Boolean,
        withEthereum: Boolean,
        withSeed: Boolean,
        withDerivationPath: Boolean
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

    private fun performMigration(
        oldDbBuilder: SupportSQLiteDatabase.() -> Unit
    ): SupportSQLiteDatabase {
        assertEquals(V2_START_VERSION, migration.startVersion)
        assertEquals(V2_END_VERSION, migration.endVersion)

        context.deleteDatabase(TEST_DB)
        releasedSchemaHelper.createDatabase(
            TEST_DB,
            V2_START_VERSION
        ).use { db ->
            assertEquals(V2_START_VERSION, db.version)
            db.assertReleasedVersion28Topology()
            db.oldDbBuilder()
            db.assertHistoricalPublicKeyStorage()
            db.query("PRAGMA foreign_key_check").use { cursor ->
                assertFalse(cursor.moveToFirst())
            }
        }

        return createOpenHelper(
            databaseName = TEST_DB,
            version = V2_END_VERSION,
            createSchema = {
                error("Expected the existing version-28 migration fixture")
            },
            upgrade = { db, oldVersion, newVersion ->
                assertEquals(V2_START_VERSION, oldVersion)
                assertEquals(V2_END_VERSION, newVersion)
                assertTrue(db.inTransaction())
                migration.migrate(db)
            }
        ).also {
            migratedDatabaseHelper = it
        }.writableDatabase.also {
            assertEquals(V2_END_VERSION, it.version)
        }
    }

    private fun createOpenHelper(
        databaseName: String,
        version: Int,
        createSchema: (SupportSQLiteDatabase) -> Unit,
        upgrade: (
            SupportSQLiteDatabase,
            oldVersion: Int,
            newVersion: Int
        ) -> Unit = { _, _, _ -> error("Unexpected database upgrade") }
    ): SupportSQLiteOpenHelper {
        val callback = object : SupportSQLiteOpenHelper.Callback(version) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                createSchema(db)
            }

            override fun onUpgrade(
                db: SupportSQLiteDatabase,
                oldVersion: Int,
                newVersion: Int
            ) {
                upgrade(db, oldVersion, newVersion)
            }
        }
        val configuration = SupportSQLiteOpenHelper.Configuration
            .builder(context)
            .name(databaseName)
            .callback(callback)
            .build()

        return FrameworkSQLiteOpenHelperFactory().create(configuration)
    }

    private fun createReleasedV28WalletDatabase(databaseName: String) {
        context.deleteDatabase(databaseName)
        releasedSchemaHelper.createDatabase(
            databaseName,
            V2_START_VERSION
        ).use { db ->
            db.insertAccount(
                publicKey = SUBSTRATE_KEYPAIR.publicKey,
                encryptionType = CRYPTO_TYPE
            )
        }
    }

    private fun migrateExistingDatabase(
        databaseName: String,
        targetVersion: Int,
        migrations: List<Migration>
    ): SupportSQLiteOpenHelper {
        return createOpenHelper(
            databaseName = databaseName,
            version = targetVersion,
            createSchema = {
                error("Expected an existing released database")
            },
            upgrade = { db, oldVersion, newVersion ->
                var version = oldVersion
                while (version < newVersion) {
                    val migration = migrations.singleOrNull {
                        it.startVersion == version &&
                            it.endVersion == version + 1
                    } ?: error(
                        "Missing migration from $version to ${version + 1}"
                    )
                    migration.migrate(db)
                    version += 1
                }
            }
        )
    }

    private fun migrationsFrom28Through31(): List<Migration> {
        return listOf(
            migration,
            MigrateTablesToV2_29_30,
            MigrateTablesToV2_30_31
        )
    }

    private fun migrationsFrom28Through34(): List<Migration> {
        return migrationsFrom28Through31() + listOf(
            EthereumDerivationPathMigration(encryptedPreferences),
            MigrateTablesToV2_32_33,
            AddChainExplorersTable_33_34
        )
    }

    private fun migrationsFrom28Through71(): List<Migration> {
        return migrationsFrom28Through34() + listOf(
            AssetsOrderMigration(),
            RemoveLegacyData_35_36,
            FixAssetsMigration_36_37,
            DifferentCurrenciesMigrations_37_38,
            AssetsMigration_38_39,
            ChainAssetsMigration_39_40,
            AssetsMigration_40_41,
            Migration_41_42,
            Migration_42_43,
            Migration_43_44,
            Migration_44_45,
            Migration_45_46,
            Migration_46_47,
            Migration_47_48,
            Migration_48_49,
            Migration_49_50,
            Migration_50_51,
            Migration_51_52,
            Migration_52_53,
            Migration_53_54,
            Migration_54_55,
            Migration_55_56,
            Migration_56_57,
            Migration_57_58,
            Migration_58_59,
            Migration_59_60,
            Migration_60_61,
            Migration_61_62,
            Migration_62_63,
            Migration_63_64,
            Migration_64_65,
            Migration_65_66,
            Migration_66_67,
            Migration_67_68,
            Migration_68_69,
            Migration_69_70,
            Migration_70_71
        )
    }

    private fun SupportSQLiteDatabase.insertVersion34AssetOrderFixtures() {
        execSQL("PRAGMA foreign_keys = OFF")
        execSQL(
            """
            INSERT INTO chains(
                id, name, icon, prefix, isEthereumBased, isTestNet,
                hasCrowdloans
            ) VALUES(
                'asset-order-chain', 'Asset order chain', 'icon',
                42, 0, 0, 0
            )
            """.trimIndent()
        )
        execSQL(
            """
            INSERT INTO tokens(symbol, dollarRate, recentRateChange)
            VALUES('KNOWN', '2.5', '0')
            """.trimIndent()
        )
        execSQL(
            """
            INSERT INTO chain_assets(
                id, chainId, name, icon, precision, staking
            ) VALUES
                ('KNOWN', 'asset-order-chain', 'Known', 'icon', 12, '[]'),
                ('STALE', 'asset-order-chain', 'Stale', 'icon', 9999, '[]')
            """.trimIndent()
        )
        execSQL(
            """
            INSERT INTO assets(
                tokenSymbol, chainId, accountId, metaId,
                freeInPlanks, reservedInPlanks, miscFrozenInPlanks,
                feeFrozenInPlanks, bondedInPlanks,
                redeemableInPlanks, unbondingInPlanks
            ) VALUES(
                'KNOWN', 'asset-order-chain', X'01', 1,
                '10', '0', '0', '0', '0', '0', '0'
            )
            """.trimIndent()
        )
        execSQL(
            """
            INSERT INTO assets(
                tokenSymbol, chainId, accountId, metaId,
                freeInPlanks, reservedInPlanks, miscFrozenInPlanks,
                feeFrozenInPlanks, bondedInPlanks,
                redeemableInPlanks, unbondingInPlanks
            ) VALUES(
                'UNMATCHED', 'missing-chain', ?, 999,
                'not-a-number', 'also-invalid', '0', '0', '0', '0', '0'
            )
            """.trimIndent(),
            arrayOf<Any>(ByteArray(4_096) { 0x6b })
        )
    }

    private fun assertReleasedVersion28Asset() {
        val schemaBytes = instrumentation.context.assets
            .open(RELEASED_SCHEMA_ASSET)
            .use { it.readBytes() }

        assertEquals(
            RELEASED_SCHEMA_SHA256,
            digestHex("SHA-256", schemaBytes)
        )
        val gitHeader =
            "blob ${schemaBytes.size}\u0000".toByteArray(Charsets.UTF_8)
        assertEquals(
            RELEASED_SCHEMA_GIT_BLOB,
            digestHex("SHA-1", gitHeader + schemaBytes)
        )
    }

    private fun digestHex(
        algorithm: String,
        bytes: ByteArray
    ): String {
        return MessageDigest.getInstance(algorithm)
            .digest(bytes)
            .joinToString(separator = "") {
                "%02x".format(it.toInt() and 0xff)
            }
    }

    private fun SupportSQLiteDatabase.assertReleasedVersion28Topology() {
        assertEquals(
            RELEASED_ROOM_IDENTITY_HASH,
            singleString(
                "SELECT identity_hash FROM room_master_table WHERE id = 42"
            )
        )

        assertEquals(
            RELEASED_TABLES,
            stringSet(
                """
                SELECT name
                FROM sqlite_master
                WHERE type = 'table'
                  AND name NOT LIKE 'sqlite_%'
                  AND name NOT IN ('android_metadata', 'room_master_table')
                """.trimIndent()
            )
        )
        assertEquals(
            RELEASED_INDEXES.keys,
            stringSet(
                """
                SELECT name
                FROM sqlite_master
                WHERE type = 'index' AND sql IS NOT NULL
                """.trimIndent()
            )
        )
        RELEASED_INDEXES.forEach { (indexName, topology) ->
            assertReleasedIndex(
                tableName = topology.table,
                indexName = indexName,
                expectedColumns = topology.columns
            )
        }
        update("meta_accounts", SQLiteDatabase.CONFLICT_REPLACE, values, "id=?", arrayOf(metaId))
    }

        val actualForeignKeys = RELEASED_TABLES.flatMapTo(linkedSetOf()) {
            tableName -> foreignKeyEdges(tableName)
        }
        assertEquals(RELEASED_FOREIGN_KEYS, actualForeignKeys)
        assertTrue(
            singleString(
                """
                SELECT sql
                FROM sqlite_master
                WHERE type = 'table' AND name = 'chain_accounts'
                """.trimIndent()
            ).contains(
                "DEFERRABLE INITIALLY DEFERRED",
                ignoreCase = true
            )
        )
    }

    private fun SupportSQLiteDatabase.assertReleasedIndex(
        tableName: String,
        indexName: String,
        expectedColumns: List<String>
    ) {
        var found = false
        query("PRAGMA index_list(`$tableName`)").use { cursor ->
            val nameColumn = cursor.getColumnIndexOrThrow("name")
            val uniqueColumn = cursor.getColumnIndexOrThrow("unique")
            while (cursor.moveToNext()) {
                if (cursor.getString(nameColumn) == indexName) {
                    assertEquals(
                        "Released index $indexName unexpectedly became unique",
                        0,
                        cursor.getInt(uniqueColumn)
                    )
                    found = true
                }
            }
        }
        assertTrue(
            "Released index $indexName is absent from $tableName",
            found
        )

        val actualColumns = mutableListOf<String>()
        query("PRAGMA index_info(`$indexName`)").use { cursor ->
            val nameColumn = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) {
                actualColumns += cursor.getString(nameColumn)
            }
        }
        assertEquals(expectedColumns, actualColumns)
    }

    private fun SupportSQLiteDatabase.foreignKeyEdges(
        tableName: String
    ): Set<String> {
        return buildSet {
            query("PRAGMA foreign_key_list(`$tableName`)").use { cursor ->
                val referencedTable =
                    cursor.getColumnIndexOrThrow("table")
                val sourceColumn = cursor.getColumnIndexOrThrow("from")
                val targetColumn = cursor.getColumnIndexOrThrow("to")
                val onUpdate = cursor.getColumnIndexOrThrow("on_update")
                val onDelete = cursor.getColumnIndexOrThrow("on_delete")
                while (cursor.moveToNext()) {
                    add(
                        "$tableName.${cursor.getString(sourceColumn)}->" +
                            "${cursor.getString(referencedTable)}." +
                            "${cursor.getString(targetColumn)}:" +
                            "${cursor.getString(onUpdate)}:" +
                            cursor.getString(onDelete)
                    )
                }
            }
        }
    }

    private fun SupportSQLiteDatabase.stringSet(sql: String): Set<String> {
        return buildSet {
            query(sql).use { cursor ->
                while (cursor.moveToNext()) {
                    add(cursor.getString(0))
                }
            }
        }
    }

    private fun SupportSQLiteDatabase.insertReleasedLegacyRows() {
        execSQL(
            """
            INSERT INTO chains(
                id, name, icon, prefix, isEthereumBased, isTestNet,
                hasCrowdloans
            ) VALUES(
                'released-chain', 'Released chain', 'released-icon', 42, 0, 0, 0
            )
            """.trimIndent()
        )
        execSQL(
            """
            INSERT INTO nodes(
                id, name, link, networkType, isDefault, isActive
            ) VALUES(
                7, 'Released node', 'wss://legacy-node.invalid', 0, 1, 1
            )
            """.trimIndent()
        )
        execSQL(
            """
            INSERT INTO assets(
                tokenSymbol, chainId, accountId, metaId, freeInPlanks,
                reservedInPlanks, miscFrozenInPlanks, feeFrozenInPlanks,
                bondedInPlanks, redeemableInPlanks, unbondingInPlanks
            ) VALUES(
                'REL', 'released-chain', X'0102', 41, '1', '2', '3', '4',
                '5', '6', '7'
            )
            """.trimIndent()
        )
        execSQL(
            """
            INSERT INTO tokens(symbol, dollarRate, recentRateChange)
            VALUES('REL', '2.5', '-0.1')
            """.trimIndent()
        )
        execSQL(
            """
            INSERT INTO phishing_addresses(publicKey)
            VALUES('released-phishing-key')
            """.trimIndent()
        )
        execSQL(
            """
            INSERT INTO storage(storageKey, content, chainId)
            VALUES('released-key', 'released-content', 'released-chain')
            """.trimIndent()
        )
        execSQL(
            """
            INSERT INTO account_staking_accesses(
                chainId, chainAssetId, accountId, stashId, controllerId
            ) VALUES(
                'released-chain', 'REL', X'0102', X'0304', X'0506'
            )
            """.trimIndent()
        )
        execSQL(
            """
            INSERT INTO total_reward(accountAddress, totalReward)
            VALUES('released-address', '123456789')
            """.trimIndent()
        )
        execSQL(
            """
            INSERT INTO operations(
                id, address, chainId, chainAssetId, time, status, source,
                operationType, amount, sender, receiver, hash, fee, isReward
            ) VALUES(
                'released-operation', 'released-address', 'released-chain',
                'REL', 123, 1, 2, 3, '9', 'sender', 'receiver', 'hash', '1', 0
            )
            """.trimIndent()
        )
        execSQL(
            """
            INSERT INTO chain_nodes(chainId, url, name)
            VALUES(
                'released-chain', 'wss://released.invalid', 'Released endpoint'
            )
            """.trimIndent()
        )
        execSQL(
            """
            INSERT INTO chain_assets(
                id, chainId, name, icon, staking, precision
            ) VALUES(
                'released-asset', 'released-chain', 'Released asset',
                'released-asset-icon', 'Unsupported', 12
            )
            """.trimIndent()
        )
        execSQL(
            """
            INSERT INTO chain_runtimes(chainId, syncedVersion, remoteVersion)
            VALUES('released-chain', 100, 101)
            """.trimIndent()
        )
        execSQL(
            """
            INSERT INTO meta_accounts(
                id, substratePublicKey, substrateCryptoType,
                substrateAccountId, ethereumPublicKey, ethereumAddress,
                name, isSelected, position
            ) VALUES(?, ?, ?, ?, NULL, NULL, ?, 0, 41)
            """.trimIndent(),
            arrayOf<Any>(
                RELEASED_EXISTING_META_ID,
                RELEASED_EXISTING_KEYPAIR.publicKey,
                CryptoType.SR25519.name,
                RELEASED_EXISTING_KEYPAIR.publicKey.substrateAccountId(),
                "Released existing wallet"
            )
        )
        execSQL(
            """
            INSERT INTO chain_accounts(
                metaId, chainId, publicKey, accountId, cryptoType
            ) VALUES(?, 'released-chain', ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any>(
                RELEASED_EXISTING_META_ID,
                RELEASED_EXISTING_KEYPAIR.publicKey,
                RELEASED_EXISTING_KEYPAIR.publicKey.substrateAccountId(),
                CryptoType.SR25519.name
            )
        )
    }

    private fun SupportSQLiteDatabase.assertHistoricalPublicKeyStorage() {
        query("SELECT typeof(publicKey), publicKey FROM users").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("text", cursor.getString(0))
            assertEquals(
                SUBSTRATE_KEYPAIR.publicKey.toPlainHexString(withPrefix = false),
                cursor.getString(1)
            )
        }
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

    private fun SupportSQLiteDatabase.singleLong(sql: String): Long {
        return query(sql).use { cursor ->
            assertTrue(cursor.moveToFirst())
            val value = cursor.getLong(0)
            assertFalse(cursor.moveToNext())
            value
        }
    }

    private fun SupportSQLiteDatabase.singleString(sql: String): String {
        return query(sql).use { cursor ->
            assertTrue(cursor.moveToFirst())
            val value = cursor.getString(0)
            assertFalse(cursor.moveToNext())
            value
        }
    }

    private fun SupportSQLiteDatabase.singleBlob(sql: String): ByteArray {
        return query(sql).use { cursor ->
            assertTrue(cursor.moveToFirst())
            val value = cursor.getBlob(0)
            assertFalse(cursor.moveToNext())
            value
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
            put("publicKey", publicKey.toPlainHexString(withPrefix = false))
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

    private data class ReleasedIndexTopology(
        val table: String,
        val columns: List<String>
    )

    private companion object {
        const val TEST_DB = "v2-migration-test"
        const val FULL_UPGRADE_DB = "v2-full-production-upgrade-test"
        const val STALE_V31_DB = "v2-stale-v31-cache-test"
        const val STALE_V34_DB = "v2-stale-v34-cache-test"
        const val QUARANTINE_V31_DB =
            "v2-late-v31-quarantine-conflict-test"
        const val QUARANTINE_V71_DB =
            "v2-late-v71-quarantine-conflict-test"
        const val V2_START_VERSION = 28
        const val V2_END_VERSION = 29
        const val RELEASED_EXISTING_META_ID = 41L
        const val MIGRATED_USER_META_ID = RELEASED_EXISTING_META_ID + 1
        const val RELEASED_DATABASE_CANONICAL_NAME =
            "jp.co.soramitsu.core_db.AppDatabase"
        const val RELEASED_SCHEMA_ASSET =
            "$RELEASED_DATABASE_CANONICAL_NAME/28.json"
        const val RELEASED_SCHEMA_GIT_BLOB =
            "0fdd6ffff03bf2cde18bcf7f9a66d58469b97e0e"
        const val RELEASED_SCHEMA_SHA256 =
            "5cd7815673834c2d0039fe686b176ac09116a7a1056f48109809df9dd1ae4219"
        const val RELEASED_ROOM_IDENTITY_HASH =
            "11b78abe23c541401fca8e23fc427a86"
        const val PREFERENCE_SENTINEL_KEY = "v2-migration-sentinel"
        const val PREFERENCE_SENTINEL_VALUE = "must-remain-byte-identical"

        val RELEASED_TABLES = setOf(
            "users",
            "nodes",
            "assets",
            "tokens",
            "phishing_addresses",
            "storage",
            "account_staking_accesses",
            "total_reward",
            "operations",
            "chains",
            "chain_nodes",
            "chain_assets",
            "chain_runtimes",
            "meta_accounts",
            "chain_accounts"
        )

        val RELEASED_INDEXES = mapOf(
            "index_assets_metaId" to ReleasedIndexTopology(
                table = "assets",
                columns = listOf("metaId")
            ),
            "index_chain_nodes_chainId" to ReleasedIndexTopology(
                table = "chain_nodes",
                columns = listOf("chainId")
            ),
            "index_chain_assets_chainId" to ReleasedIndexTopology(
                table = "chain_assets",
                columns = listOf("chainId")
            ),
            "index_chain_runtimes_chainId" to ReleasedIndexTopology(
                table = "chain_runtimes",
                columns = listOf("chainId")
            ),
            "index_meta_accounts_substrateAccountId" to
                ReleasedIndexTopology(
                    table = "meta_accounts",
                    columns = listOf("substrateAccountId")
                ),
            "index_meta_accounts_ethereumAddress" to ReleasedIndexTopology(
                table = "meta_accounts",
                columns = listOf("ethereumAddress")
            ),
            "index_chain_accounts_chainId" to ReleasedIndexTopology(
                table = "chain_accounts",
                columns = listOf("chainId")
            ),
            "index_chain_accounts_metaId" to ReleasedIndexTopology(
                table = "chain_accounts",
                columns = listOf("metaId")
            ),
            "index_chain_accounts_accountId" to ReleasedIndexTopology(
                table = "chain_accounts",
                columns = listOf("accountId")
            )
        )

        val RELEASED_FOREIGN_KEYS = setOf(
            "chain_nodes.chainId->chains.id:NO ACTION:CASCADE",
            "chain_assets.chainId->chains.id:NO ACTION:CASCADE",
            "chain_accounts.chainId->chains.id:NO ACTION:NO ACTION",
            "chain_accounts.metaId->meta_accounts.id:NO ACTION:CASCADE"
        )
    }
}
