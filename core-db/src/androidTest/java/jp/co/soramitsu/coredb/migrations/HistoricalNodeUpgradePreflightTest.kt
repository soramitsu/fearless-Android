package jp.co.soramitsu.coredb.migrations

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteException
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import jp.co.soramitsu.common.data.secrets.v1.SecretStoreV1Impl
import jp.co.soramitsu.common.data.secrets.v2.ChainAccountSecrets
import jp.co.soramitsu.common.data.secrets.v2.KeyPairSchema
import jp.co.soramitsu.common.data.secrets.v2.SecretStoreV2
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecretStore
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecretStore
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferenceSnapshot
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.common.data.storage.encrypt.WalletPublicIdentityRecovery
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretQuarantine
import jp.co.soramitsu.common.utils.ethereumAddressFromPublicKey
import jp.co.soramitsu.common.utils.invoke
import jp.co.soramitsu.common.utils.substrateAccountId
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.coredb.APP_DATABASE_VERSION
import jp.co.soramitsu.coredb.AppDatabase
import jp.co.soramitsu.coredb.migrations.fixtures.ReleasedSchemaFixtures
import jp.co.soramitsu.coredb.migrations.fixtures.ReleasedV200DatabaseFixture
import jp.co.soramitsu.fearless_utils.extensions.toHexString as toPlainHexString
import jp.co.soramitsu.fearless_utils.scale.toHexString
import jp.co.soramitsu.testshared.HashMapEncryptedPreferences
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exact released DB30 schema followed by the real 30 -> 31 edge, then
 * synthetic public endpoints and the pinned synthetic six-field wallet.
 * Required previews use production migration FK-off mode. FK-on tests prove
 * rejection before preview mutation. No network or real secrets.
 */
class HistoricalNodeUpgradePreflightTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val createdDatabases = mutableSetOf<String>()
    private val legacySecret by lazy { ReleasedV200DatabaseFixture.legacySecretHex(context) }
    private val legacySchema = TonMigration.MetaAccountSecretsV69
    private val legacyWallet by lazy { legacySchema.read(legacySecret) }
    private val substrateKeypair by lazy { legacyWallet[legacySchema.SubstrateKeypair] }
    private val ethereumKeypair by lazy { checkNotNull(legacyWallet[legacySchema.EthereumKeypair]) }
    private val substratePublicKey by lazy { substrateKeypair[KeyPairSchema.PublicKey] }
    private val ethereumPublicKey by lazy { ethereumKeypair[KeyPairSchema.PublicKey] }
    private val chainSecretKey by lazy {
        "$META_ID:${substratePublicKey.substrateAccountId().toPlainHexString(withPrefix = false)}:ACCESS_SECRETS"
    }
    private val chainSecret by lazy {
        ChainAccountSecrets { secret ->
            secret[ChainAccountSecrets.Entropy] = legacyWallet[legacySchema.Entropy]
            secret[ChainAccountSecrets.Seed] = legacyWallet[legacySchema.Seed]
            secret[ChainAccountSecrets.Keypair] = substrateKeypair
            secret[ChainAccountSecrets.DerivationPath] = legacyWallet[legacySchema.SubstrateDerivationPath]
        }.toHexString()
    }

    @After
    fun removeOwnedTestDatabases() {
        createdDatabases.forEach(context::deleteDatabase)
    }

    @Test
    fun healthyProductionBuilderPreservesOwnedNodesWalletAndReadableSecrets() = runBlocking {
        val fixture = install("production-healthy")
        var firstPreferences: Map<String, String>? = null
        // Two opens share a process: this does not qualify process-death U9.
        repeat(2) {
            val database = AppDatabase.create(
                context = context, databaseName = fixture.name,
                storeV1 = SecretStoreV1Impl(fixture.preferences), storeV2 = SecretStoreV2(fixture.preferences),
                encryptedPreferences = fixture.preferences,
                substrateSecretStore = SubstrateSecretStore(fixture.preferences),
                ethereumSecretStore = EthereumSecretStore(fixture.preferences)
            )
            try {
                val sql = database.openHelper.writableDatabase
                assertEquals(APP_DATABASE_VERSION, sql.version)
                assertEquals("ok", sql.string("PRAGMA integrity_check"))
                sql.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
                val wallet = checkNotNull(database.metaAccountDao().getMetaAccount(META_ID))
                assertEquals(WALLET_NAME, wallet.name)
                assertArrayEquals(substratePublicKey, sql.blob("SELECT substratePublicKey FROM meta_accounts"))
                val custom = database.chainDao().getNode(CUSTOM_CHAIN, CUSTOM_URL)
                assertEquals(CUSTOM_NAME, custom.name)
                assertTrue(custom.isActive)
                assertFalse(custom.isDefault)
                val selectedDefault = database.chainDao().getNode(DEFAULT_CHAIN, DEFAULT_URL)
                assertEquals(DEFAULT_NAME, selectedDefault.name)
                assertTrue(selectedDefault.isActive)
                assertTrue(selectedDefault.isDefault)
                assertEquals(2L, sql.scalar("SELECT COUNT(*) FROM chain_nodes"))
                assertEquals(1L, sql.scalar("SELECT COUNT(*) FROM chain_accounts"))
                checkNotNull(SubstrateSecretStore(fixture.preferences).get(
                    META_ID, substratePublicKey, CryptoType.SR25519, substratePublicKey.substrateAccountId()
                ))
                checkNotNull(EthereumSecretStore(fixture.preferences).get(
                    META_ID, sql.blob("SELECT ethereumPublicKey FROM meta_accounts"),
                    sql.blob("SELECT ethereumAddress FROM meta_accounts")
                ))
                assertEquals(chainSecret, fixture.preferences.getDecryptedString(chainSecretKey))
                assertFalse(fixture.preferences.hasKey(LEGACY_SECRET_KEY))
                assertFalse(fixture.preferences.hasKeyWithPrefix(WalletSecretQuarantine.KEY_PREFIX))
                assertFalse(fixture.preferences.hasKeyWithPrefix(WalletPublicIdentityRecovery.KEY_PREFIX))
                assertEquals(SENTINEL_VALUE, fixture.preferences.getDecryptedString(SENTINEL_KEY))
                val after = fixture.preferences.snapshot()
                firstPreferences?.let { assertEquals(it, after) }
                firstPreferences = after
            } finally {
                database.close()
            }
        }
    }

    @Test
    fun healthyGuardRestoresNonemptyUsersAndAllowsOuterCommit() {
        val fixture = install("guard-healthy")
        withSource(fixture) {
            assertEquals(1L, scalar("SELECT COUNT(*) FROM users"))
            val before = snapshot()
            transaction {
                HistoricalNodeUpgradePreflight.requireSafeBeforeDb31SecretWrite(this)
                assertEquals(before, snapshot())
                execSQL("UPDATE meta_accounts SET name = 'outer commit after preview' WHERE id = $META_ID")
            }
            assertEquals("outer commit after preview", string("SELECT name FROM meta_accounts"))
            assertEquals("Legacy user 一", string("SELECT username FROM users"))
            assertEquals(31, version)
        }
    }

    @Test
    fun noRetainedNodesWithArchiveRequirePreviewTransaction() {
        val fixture = install("no-retention")
        withSource(fixture, foreignKeys = true) {
            execSQL("UPDATE chain_nodes SET isDefault = 1, isActive = 0")
            val before = snapshot()
            val audit = MutationAudit(this)
            assertFalse(inTransaction())
            assertFalse(HistoricalNodeUpgradePreflight.hasRetainedNodes(this))
            assertTrue(LegacyNodeArchive.requireSafe(this))
            val failure = assertThrows(IllegalStateException::class.java) {
                HistoricalNodeUpgradePreflight.requireSafeBeforeDb31SecretWrite(
                    audit, stagingPayloadLimits = TonUpgradePayloadLimits(1, 1, 1),
                    tonPayloadLimits = TonUpgradePayloadLimits(1, 1, 1)
                )
            }
            assertTrue(failure.message.orEmpty().contains("transaction"))
            assertTrue(audit.statements.isEmpty())
            assertEquals(before, snapshot())
            assertEquals(1L, scalar("PRAGMA foreign_keys"))
        }
    }

    @Test
    fun noArchiveAndNoRetainedNodesStillRequirePreviewTransaction() {
        val fixture = install("no-archive-or-retention")
        withSource(fixture, foreignKeys = true) {
            // Fixture-only: represent a DB31 created by the earlier shipped
            // edge that did not keep its legacy source table.
            execSQL("DROP TABLE nodes")
            execSQL("UPDATE chain_nodes SET isDefault = 1, isActive = 0")
            val before = snapshot()
            val audit = MutationAudit(this)
            assertFalse(inTransaction())
            assertFalse(LegacyNodeArchive.requireSafe(this))
            assertFalse(HistoricalNodeUpgradePreflight.hasRetainedNodes(this))
            val failure = assertThrows(IllegalStateException::class.java) {
                HistoricalNodeUpgradePreflight.requireSafeBeforeDb31SecretWrite(
                    audit, stagingPayloadLimits = TonUpgradePayloadLimits(1, 1, 1),
                    tonPayloadLimits = TonUpgradePayloadLimits(1, 1, 1)
                )
            }
            assertTrue(failure.message.orEmpty().contains("transaction"))
            assertTrue(audit.statements.isEmpty())
            assertEquals(before, snapshot())
            assertEquals(1L, scalar("PRAGMA foreign_keys"))
        }
    }

    @Test
    fun noRetainedNodesWithArchiveRunFullPreviewAndRestoreOuterTransaction() {
        val fixture = install("archive-without-retention")
        withSource(fixture) {
            execSQL("UPDATE chain_nodes SET isDefault = 1, isActive = 0")
            assertFalse(HistoricalNodeUpgradePreflight.hasRetainedNodes(this))
            assertTrue(LegacyNodeArchive.requireSafe(this))
            assertEquals(0L, scalar("SELECT COUNT(*) FROM nodes"))
            val before = snapshot()
            val preferencesBefore = fixture.preferences.snapshot()
            transaction {
                val audit = MutationAudit(this)
                HistoricalNodeUpgradePreflight.requireSafeBeforeDb31SecretWrite(audit)
                assertTrue(audit.statements.any { it.startsWith("UPDATE meta_accounts SET ethereumPublicKey") })
                assertTrue(audit.statements.any { it.contains("remoteAssetsSource") })
                assertTrue(inTransaction())
                assertEquals(before, snapshot())
                val boundedAudit = MutationAudit(this)
                assertThrows(MigrationPayloadLimitException::class.java) {
                    HistoricalNodeUpgradePreflight.requireSafeBeforeDb31SecretWrite(
                        boundedAudit, stagingPayloadLimits = TonUpgradePayloadLimits(1, 1, 1),
                        tonPayloadLimits = TonUpgradePayloadLimits(1, 1, 1)
                    )
                }
                assertTrue(boundedAudit.statements.isEmpty())
                assertEquals(before, snapshot())
                assertEquals(preferencesBefore, fixture.preferences.snapshot())
                execSQL("UPDATE meta_accounts SET name = 'outer commit after archive preview' WHERE id = $META_ID")
            }
            assertEquals("outer commit after archive preview", string("SELECT name FROM meta_accounts"))
            assertTrue(LegacyNodeArchive.requireSafe(this))
            assertEquals(0L, scalar("SELECT COUNT(*) FROM nodes"))
            assertEquals("Legacy user 一", string("SELECT username FROM users"))
            assertEquals(preferencesBefore, fixture.preferences.snapshot())
            assertEquals(31, version)
        }
    }

    @Test
    fun retainedNodesRequireAnExistingMigrationTransactionBeforeMutation() {
        val fixture = install("missing-transaction")
        withSource(fixture) {
            val before = snapshot()
            val audit = MutationAudit(this)
            val failure = assertThrows(IllegalStateException::class.java) {
                HistoricalNodeUpgradePreflight.requireSafeBeforeDb31SecretWrite(audit)
            }
            assertTrue(failure.message.orEmpty().contains("transaction"))
            assertTrue(audit.statements.isEmpty())
            assertEquals(before, snapshot())
        }
    }

    @Test
    fun requiredPreviewWithForeignKeysOnRejectsBeforePreferenceAccess() {
        assertMigrationRejected("fk-on", "foreign keys", foreignKeys = true) {}
    }

    @Test
    fun nodeAnomaliesRemainExactAfterFullPreviewAndAllowOuterCommit() {
        listOf(Db31NodeAnomaly.UNKNOWN_FLAG, Db31NodeAnomaly.ORPHAN).forEach { anomaly ->
            val fixture = db31Fixture(anomaly, "preview-rollback")
            withSource(fixture) {
                val before = snapshot()
                val originalNodes = db31RawRows("chain_nodes", NODE_COLUMNS)
                val originalSchema = db31NodeSchema(archived = false)
                val preferencesBefore = fixture.preferences.snapshot()
                transaction {
                    val audit = MutationAudit(this)
                    HistoricalNodeUpgradePreflight.requireSafeBeforeDb31SecretWrite(audit)
                    assertTrue(audit.statements.any { it.contains("endpoint_archive_v1") })
                    assertTrue(audit.statements.any { it.contains("remoteAssetsSource") })
                    assertTrue(inTransaction())
                    assertEquals(before, snapshot())
                    assertEquals(originalNodes, db31RawRows("chain_nodes", NODE_COLUMNS))
                    assertEquals(originalSchema, db31NodeSchema(archived = false))
                    assertEquals(preferencesBefore, fixture.preferences.snapshot())
                    execSQL("UPDATE meta_accounts SET name = 'outer commit after node preview' WHERE id = $META_ID")
                }
                assertEquals("outer commit after node preview", string("SELECT name FROM meta_accounts"))
                assertEquals(originalNodes, db31RawRows("chain_nodes", NODE_COLUMNS))
                assertEquals(originalSchema, db31NodeSchema(archived = false))
                assertEquals(preferencesBefore, fixture.preferences.snapshot())
                assertEquals(0L, scalar("SELECT COUNT(*) FROM sqlite_master WHERE name LIKE 'endpoint_archive%'"))
                assertEquals(31, version)
            }
        }
    }

    @Test
    fun oversizedRetainedNodeRejectsBeforeSqlCopyOrPreferenceAccess() {
        assertMigrationRejected("node-cell", "chain_nodes.name") {
            execSQL("UPDATE chain_nodes SET name = ? WHERE url = ?", arrayOf(oversizedCell(), CUSTOM_URL))
        }
    }

    @Test
    fun oversizedRetainedParentRejectsBeforeSqlCopyOrPreferenceAccess() {
        assertMigrationRejected("parent-cell", "chains.icon") {
            execSQL("UPDATE chains SET icon = ? WHERE id = ?", arrayOf(oversizedCell(), CUSTOM_CHAIN))
        }
    }

    @Test
    fun oversizedUsersPayloadRejectsBeforeVersion35CopyOrPreferenceAccess() {
        assertMigrationRejected("user-cell", "users.username") {
            execSQL("UPDATE users SET username = ?", arrayOf(oversizedCell()))
        }
    }

    @Test
    fun oversizedChainAccountRejectsBeforeVersion35CopyOrPreferenceAccess() {
        assertMigrationRejected("chain-account-cell", "chain_accounts.publicKey") {
            execSQL("UPDATE chain_accounts SET publicKey = zeroblob(${TON_UPGRADE_MAX_CELL_PAYLOAD_BYTES + 1})")
        }
    }

    @Test
    fun usersRowLimitRejectsBeforeVersion35CopyOrPreferenceAccess() {
        assertMigrationRejected("users-count", "users rows") {
            transaction {
                repeat(V2MigrationLimits.PRODUCTION.maxAccountPlans) { index ->
                    execSQL(
                        "INSERT INTO users(address, username, publicKey, cryptoType, position, networkType) " +
                            "VALUES (?, 'extra', '00', 0, ?, 0)",
                        arrayOf("synthetic-extra-$index", index + 1)
                    )
                }
            }
            assertEquals(4097L, scalar("SELECT COUNT(*) FROM users"))
        }
    }

    @Test
    fun paddedEthereumMinimumIsCountedBeforeFirstPreviewWrite() {
        val fixture = install("padding-bound")
        withSource(fixture) {
            execSQL("UPDATE meta_accounts SET ethereumPublicKey = NULL, ethereumAddress = NULL")
            assertGuardRejectedBeforeMutation(
                stagingLimits = TonUpgradePayloadLimits.PRODUCTION.copy(maximumCellBytes = 64),
                expectedMessage = "meta_accounts.ethereumPublicKey"
            )
            val before = snapshot()
            transaction {
                HistoricalNodeUpgradePreflight.requireSafeBeforeDb31SecretWrite(
                    this, stagingPayloadLimits = TonUpgradePayloadLimits.PRODUCTION.copy(maximumCellBytes = 65)
                )
                assertEquals(before, snapshot())
            }
            assertEquals(before, snapshot())
        }
    }

    @Test
    fun perRowLimitRejectsCombinedNodeCellsBeforeFirstPreviewWrite() {
        val fixture = install("row-bound")
        withSource(fixture) {
            execSQL("UPDATE chain_nodes SET name = ? WHERE url = ?", arrayOf("n".repeat(128), CUSTOM_URL))
            assertGuardRejectedBeforeMutation(
                stagingLimits = TonUpgradePayloadLimits(128, 180, 10_000),
                expectedMessage = "chain_nodes row"
            )
        }
    }

    @Test
    fun stagingAggregateIncludesEveryCopiedGroupAndAcceptsExactBoundary() {
        val fixture = install("aggregate-bound")
        withSource(fixture) {
            val expected = expectedStagingBytes()
            val before = snapshot()
            transaction {
                HistoricalNodeUpgradePreflight.requireSafeBeforeDb31SecretWrite(
                    this, stagingPayloadLimits = TonUpgradePayloadLimits.PRODUCTION.copy(maximumTotalBytes = expected)
                )
                assertEquals(before, snapshot())
            }
            assertGuardRejectedBeforeMutation(
                stagingLimits = TonUpgradePayloadLimits.PRODUCTION.copy(maximumTotalBytes = expected - 1),
                expectedMessage = "Historical preview staging"
            )
        }
    }

    @Test
    fun retainedParentUnionCountsSharedNodeAndAccountParentOnlyOnce() {
        val fixture = install("parent-union")
        withSource(fixture) {
            assertEquals(1L, scalar("SELECT COUNT(*) FROM chain_accounts WHERE chainId = '$CUSTOM_CHAIN'"))
            assertEquals(1L, scalar("SELECT COUNT(*) FROM chain_nodes WHERE chainId = '$CUSTOM_CHAIN' AND isDefault = 0"))
            val expected = payloadBytes("chain_nodes", NODE_COLUMNS, "isDefault = 0 OR isActive = 1") +
                payloadBytes("chains", CHAIN_COLUMNS)
            assertEquals(expected, HistoricalNodeUpgradePreflight.requireBoundedRetentionPayloads(this))
        }
    }

    @Test
    fun actualTonAggregateBoundaryRollsBackEntirePreviewOnLateRejection() {
        val fixture = install("ton-aggregate")
        withSource(fixture) {
            val before = snapshot()
            var actualTonBytes = 0L
            transaction {
                withRollbackOnlySqlPreview(this) {
                    // Independent length-only construction of the documented
                    // 65/20-byte upper bound; the preview rolls these bytes back.
                    execSQL(
                        "UPDATE meta_accounts SET ethereumPublicKey = zeroblob(MAX(" +
                            "COALESCE(length(CAST(ethereumPublicKey AS BLOB)), 0), 65)), " +
                            "ethereumAddress = zeroblob(MAX(COALESCE(length(CAST(ethereumAddress AS BLOB)), 0), 20))"
                    )
                    HISTORICAL_SQL_MIGRATIONS_32_TO_71.forEach { it.migrate(this) }
                    TonUpgradeSqlPreflight.requireSafeToMutate(this)
                    actualTonBytes = TON_PROTECTED_PAYLOAD_TABLES.sumOf { table -> payloadBytes(table, columns(table)) }
                }
                assertEquals(before, snapshot())
                HistoricalNodeUpgradePreflight.requireSafeBeforeDb31SecretWrite(
                    this, tonPayloadLimits = TonUpgradePayloadLimits.PRODUCTION.copy(maximumTotalBytes = actualTonBytes)
                )
                assertEquals(before, snapshot())
                val audit = MutationAudit(this)
                val failure = assertThrows(TonUpgradeSqlIntegrityException::class.java) {
                    HistoricalNodeUpgradePreflight.requireSafeBeforeDb31SecretWrite(
                        audit, tonPayloadLimits = TonUpgradePayloadLimits.PRODUCTION.copy(maximumTotalBytes = actualTonBytes - 1)
                    )
                }
                assertTrue(failure.message.orEmpty().contains("Version 71 copied tables"))
                assertTrue(audit.statements.any { it.startsWith("UPDATE meta_accounts SET ethereumPublicKey") })
                assertTrue(audit.statements.any { it.contains("remoteAssetsSource") })
                assertEquals(before, snapshot())
            }
            assertEquals(before, snapshot())
        }
    }

    @Test
    fun version39TemporaryCollisionRollsBackEarlierCopiesBeforePreferenceAccess() {
        assertMigrationRejected("temp-collision", "work objects", expectPreviewMutation = true) {
            execSQL("CREATE TEMP TABLE _migration_39_retained_nodes(note TEXT)")
            execSQL("INSERT INTO temp._migration_39_retained_nodes VALUES ('must remain exact')")
        }
    }

    @Test
    fun latePreviewSqlFailureRestoresExactSqlAndNeverAccessesPreferences() {
        val fixture = install("late-sql-failure")
        withSource(fixture) {
            val before = snapshot()
            val preferencesBefore = fixture.preferences.snapshot()
            val tracking = TrackingPreferences(fixture.preferences)
            val original = SQLiteException("owned final-edge SQL failure")
            var injected = false
            val audit = MutationAudit(this) { sql ->
                if (sql.contains("remoteAssetsSource")) {
                    injected = true
                    throw original
                }
            }
            transaction {
                val actual = assertThrows(SQLiteException::class.java) {
                    EthereumDerivationPathMigration(tracking).migrate(audit)
                }
                assertSame(original, actual)
                assertTrue(injected)
                assertTrue(actual.suppressed.isEmpty())
                assertEquals(before, snapshot())
                assertEquals(0, tracking.accessCount)
                assertEquals(preferencesBefore, fixture.preferences.snapshot())
                assertTrue(inTransaction())
            }
            assertEquals(before, snapshot())
        }
    }

    @Test
    fun localVersion39RejectsOversizedRetainedPayloadBeforeFirstMutation() {
        val fixture = install("local39")
        withSource(fixture) {
            transaction {
                HISTORICAL_SQL_MIGRATIONS_32_TO_71.filter { it.startVersion < 39 }.forEach { it.migrate(this) }
                version = 39
            }
            execSQL("UPDATE chain_nodes SET name = ? WHERE url = ?", arrayOf(oversizedCell(), CUSTOM_URL))
            val before = snapshot()
            val audit = MutationAudit(this)
            transaction {
                val failure = assertThrows(TonUpgradeSqlIntegrityException::class.java) {
                    ChainAssetsMigration_39_40.migrate(audit)
                }
                assertTrue(failure.message.orEmpty().contains("chain_nodes.name"))
                assertTrue(audit.statements.isEmpty())
                assertEquals(before, snapshot())
            }
            assertEquals(before, snapshot())
        }
    }

    @Test
    fun future83FailurePrecedesActualDb31PreferenceWriterAndAllowsRetry() {
        assertFutureDecisionFailureBeforeWrites(31)
    }

    @Test
    fun future83FailurePrecedesActualTonPreferenceWriterAndAllowsRetry() {
        assertFutureDecisionFailureBeforeWrites(71)
    }

    @Test
    fun future83FailurePrecedesActualWallet76PreferenceWriterAndAllowsRetry() {
        assertFutureDecisionFailureBeforeWrites(76)
    }

    private fun assertFutureDecisionFailureBeforeWrites(edge: Int) {
        val fixture = install("future83-before-writes-$edge")
        withSource(fixture) {
            if (edge > 31) transaction {
                // Bring the healthy fixture to the shipped writer entry; secret conversion remains pending.
                EthereumDerivationPathMigration(fixture.preferences).migrate(this)
                HISTORICAL_SQL_MIGRATIONS_32_TO_71.forEach { it.migrate(this) }
                if (edge == 76) {
                    TonSchemaMigration.migrate(this)
                    Migration_72_73.migrate(this)
                    Migration_73_74.migrate(this)
                    Migration_74_75.migrate(this)
                    Migration_75_76.migrate(this)
                }
                // Shipped71/76 sources predate this candidate's entry snapshot. Preserve the
                // existing historical archive, but remove candidate-only setup artifacts.
                execSQL("DROP TABLE IF EXISTS upgrade_entry_endpoint_archive_header_v1")
                execSQL("DROP TABLE IF EXISTS upgrade_entry_endpoint_archive_schema_v1")
                execSQL("DROP TABLE IF EXISTS upgrade_entry_endpoint_archive_v1")
                version = edge
            }
            val before = futureOwnershipSqlImage()
            val preferencesBefore = fixture.preferences.snapshot()
            val writes = Db31PreferenceWriteAudit(fixture.preferences)
            val expected = SQLiteException("OWNED_FUTURE_83_DECISION_FAILURE")
            var injected = false
            val audited = MutationAudit(this) { sql ->
                if (sql.startsWith("CREATE TABLE `endpoint_recovery_decisions`")) {
                    assertTrue(CurrentEndpointArchive.requireSafe(this))
                    assertTrue(jp.co.soramitsu.coredb.recovery.requireOwnedEndpointSchema(this))
                    injected = true
                    throw expected
                }
            }
            fun runWriter(database: SupportSQLiteDatabase) {
                when (edge) {
                    31 -> EthereumDerivationPathMigration(writes).migrate(database)
                    71 -> TonMigration(writes).migrate(database)
                    76 -> WalletSecretIntegrityMigration(writes).migrate(database)
                    else -> error("Unsupported test writer")
                }
            }
            beginTransaction()
            try {
                assertSame(expected,assertThrows(SQLiteException::class.java) { runWriter(audited) })
                assertTrue(injected)
                assertEquals(0,writes.writeAttempts)
                assertEquals(preferencesBefore,fixture.preferences.snapshot())
                // Earlier public schema work belongs to the outer migration transaction.
            } finally { endTransaction() }
            assertEquals(before,futureOwnershipSqlImage())
            assertEquals(0L,scalar("SELECT COUNT(*) FROM sqlite_temp_master"))
            transaction { runWriter(this) }
            assertTrue("Healthy retry must reach the actual preference writer at edge $edge",writes.writeAttempts > 0)
            assertEquals(SENTINEL_VALUE,fixture.preferences.getDecryptedString(SENTINEL_KEY))
        }
    }

    @Test
    fun future84FailurePrecedesActualDb31PreferenceWriterAndAllowsRetry() {
        assertFutureCurrencyFailureBeforeWrites(31)
    }

    @Test
    fun future84FailurePrecedesActualTonPreferenceWriterAndAllowsRetry() {
        assertFutureCurrencyFailureBeforeWrites(71)
    }

    @Test
    fun future84FailurePrecedesActualWallet76PreferenceWriterAndAllowsRetry() {
        assertFutureCurrencyFailureBeforeWrites(76)
    }

    private fun assertFutureCurrencyFailureBeforeWrites(edge: Int) {
        val fixture = install("future84-before-writes-$edge")
        withSource(fixture) {
            if (edge > 31) transaction {
                // Bring the healthy fixture to the shipped writer entry; secret conversion remains pending.
                EthereumDerivationPathMigration(fixture.preferences).migrate(this)
                HISTORICAL_SQL_MIGRATIONS_32_TO_71.forEach { it.migrate(this) }
                if (edge == 76) {
                    TonSchemaMigration.migrate(this)
                    Migration_72_73.migrate(this)
                    Migration_73_74.migrate(this)
                    Migration_74_75.migrate(this)
                    Migration_75_76.migrate(this)
                }
                // Shipped71/76 sources predate this candidate's entry snapshot. Preserve the
                // existing historical archive, but remove candidate-only setup artifacts.
                execSQL("DROP TABLE IF EXISTS upgrade_entry_endpoint_archive_header_v1")
                execSQL("DROP TABLE IF EXISTS upgrade_entry_endpoint_archive_schema_v1")
                execSQL("DROP TABLE IF EXISTS upgrade_entry_endpoint_archive_v1")
                version = edge
            }
            val before = futureCurrencySqlImage()
            val preferencesBefore = fixture.preferences.snapshot()
            val writes = Db31PreferenceWriteAudit(fixture.preferences)
            val expected = SQLiteException("OWNED_FUTURE_84_CURRENCY_FAILURE")
            var injected = false
            val audited = MutationAudit(this) { sql ->
                if (sql == FiatCurrencyIdentityMigration.ADD_CURRENCY_ID) {
                    assertTrue(inTransaction())
                    assertEquals(edge, version)
                    assertEquals(before.foreignKeys, scalar("PRAGMA foreign_keys"))
                    assertTrue(CurrentEndpointArchive.requireSafe(this))
                    assertTrue(jp.co.soramitsu.coredb.recovery.requireOwnedEndpointSchema(this))
                    injected = true
                    throw expected
                }
            }
            fun runWriter(database: SupportSQLiteDatabase) {
                when (edge) {
                    31 -> EthereumDerivationPathMigration(writes).migrate(database)
                    71 -> TonMigration(writes).migrate(database)
                    76 -> WalletSecretIntegrityMigration(writes).migrate(database)
                    else -> error("Unsupported test writer")
                }
            }
            beginTransaction()
            try {
                val actual = assertThrows(SQLiteException::class.java) { runWriter(audited) }
                assertSame(expected, actual)
                assertTrue(actual.suppressed.isEmpty())
                assertTrue(injected)
                assertEquals(0,writes.writeAttempts)
                assertEquals(preferencesBefore,fixture.preferences.snapshot())
                // Earlier public schema work belongs to the outer migration transaction.
            } finally { endTransaction() }
            assertFalse(inTransaction())
            assertEquals(before, futureCurrencySqlImage())
            assertEquals(edge, version)
            assertEquals(preferencesBefore, fixture.preferences.snapshot())
            assertEquals(0, writes.writeAttempts)
            assertEquals(0L,scalar("SELECT COUNT(*) FROM sqlite_temp_master"))
            transaction { runWriter(this) }
            assertTrue("Healthy retry must reach the actual preference writer at edge $edge",writes.writeAttempts > 0)
            assertEquals(SENTINEL_VALUE,fixture.preferences.getDecryptedString(SENTINEL_KEY))
        }
    }

    private data class FutureCurrencySqlImage(
        val userVersion: Int,
        val foreignKeys: Long,
        val typedSchemaAndRows: Map<String, List<Db31RawRow>>
    )

    private fun SupportSQLiteDatabase.futureCurrencySqlImage() = FutureCurrencySqlImage(
        userVersion = version,
        foreignKeys = scalar("PRAGMA foreign_keys"),
        typedSchemaAndRows = futureOwnershipSqlImage()
    )

    @Test
    fun future82FailurePrecedesActualDb31PreferenceWriterAndAllowsRetry() {
        assertFutureOwnershipFailureBeforeWrites(31)
    }

    @Test
    fun future82FailurePrecedesActualTonPreferenceWriterAndAllowsRetry() {
        assertFutureOwnershipFailureBeforeWrites(71)
    }

    @Test
    fun future82FailurePrecedesActualWallet76PreferenceWriterAndAllowsRetry() {
        assertFutureOwnershipFailureBeforeWrites(76)
    }

    private fun assertFutureOwnershipFailureBeforeWrites(edge: Int) {
        val fixture = install("future82-before-writes-$edge")
        withSource(fixture) {
            if (edge > 31) transaction {
                // Bring the healthy fixture to the shipped writer entry; secret conversion remains pending.
                EthereumDerivationPathMigration(fixture.preferences).migrate(this)
                HISTORICAL_SQL_MIGRATIONS_32_TO_71.forEach { it.migrate(this) }
                if (edge == 76) {
                    TonSchemaMigration.migrate(this)
                    Migration_72_73.migrate(this)
                    Migration_73_74.migrate(this)
                    Migration_74_75.migrate(this)
                    Migration_75_76.migrate(this)
                }
                // Shipped71/76 sources predate this candidate's entry snapshot. Preserve the
                // existing historical archive, but remove candidate-only setup artifacts.
                execSQL("DROP TABLE IF EXISTS upgrade_entry_endpoint_archive_header_v1")
                execSQL("DROP TABLE IF EXISTS upgrade_entry_endpoint_archive_schema_v1")
                execSQL("DROP TABLE IF EXISTS upgrade_entry_endpoint_archive_v1")
                version = edge
            }
            val before = futureOwnershipSqlImage()
            val preferencesBefore = fixture.preferences.snapshot()
            val writes = Db31PreferenceWriteAudit(fixture.preferences)
            val expected = SQLiteException("OWNED_FUTURE_82_COPY_FAILURE")
            var injected = false
            val audited = MutationAudit(this) { sql ->
                if (sql.startsWith("CREATE TABLE `custom_endpoint_records`")) {
                    assertTrue(CurrentEndpointArchive.requireSafe(this))
                    injected = true
                    throw expected
                }
            }
            fun runWriter(database: SupportSQLiteDatabase) {
                when (edge) {
                    31 -> EthereumDerivationPathMigration(writes).migrate(database)
                    71 -> TonMigration(writes).migrate(database)
                    76 -> WalletSecretIntegrityMigration(writes).migrate(database)
                    else -> error("Unsupported test writer")
                }
            }
            beginTransaction()
            try {
                assertSame(expected,assertThrows(SQLiteException::class.java) { runWriter(audited) })
                assertTrue(injected)
                assertEquals(0,writes.writeAttempts)
                assertEquals(preferencesBefore,fixture.preferences.snapshot())
                // Earlier public schema work belongs to the outer migration transaction.
            } finally { endTransaction() }
            assertEquals(before,futureOwnershipSqlImage())
            assertEquals(0L,scalar("SELECT COUNT(*) FROM sqlite_temp_master"))
            transaction { runWriter(this) }
            assertTrue("Healthy retry must reach the actual preference writer at edge $edge",writes.writeAttempts > 0)
            assertEquals(SENTINEL_VALUE,fixture.preferences.getDecryptedString(SENTINEL_KEY))
        }
    }

    private fun SupportSQLiteDatabase.futureOwnershipSqlImage(): Map<String,List<Db31RawRow>> = buildMap {
        for ((scope,catalog) in listOf("main" to "sqlite_master","temp" to "sqlite_temp_master")) {
            put(catalog,db31RawRows(catalog,listOf("type","name","tbl_name","rootpage","sql")))
            val names=query("SELECT name FROM $catalog WHERE type='table' ORDER BY name").use { cursor ->
                buildList { while(cursor.moveToNext()) add(cursor.getString(0)) }
            }
            for(name in names) {
                val quoted="`${name.replace("`","``")}`"
                val fields=query("PRAGMA $scope.table_info($quoted)").use { cursor ->
                    buildList { while(cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name"))) }
                }
                put("$scope.$name",db31RawRows("$scope.$quoted",fields))
            }
        }
    }

    private fun assertMigrationRejected(
        suffix: String,
        expectedMessage: String,
        foreignKeys: Boolean = false,
        expectPreviewMutation: Boolean = false,
        prepare: SupportSQLiteDatabase.() -> Unit
    ) {
        val fixture = install(suffix)
        withSource(fixture, foreignKeys) {
            prepare()
            Db31UpgradeSqlPreflight.requireSafeBeforePreferenceAccess(this)
            val before = snapshot()
            val preferencesBefore = fixture.preferences.snapshot()
            val tracking = TrackingPreferences(fixture.preferences)
            val audit = MutationAudit(this)
            transaction {
                val failure = assertThrows(IllegalStateException::class.java) {
                    EthereumDerivationPathMigration(tracking).migrate(audit)
                }
                assertTrue(failure.message.orEmpty(), failure.message.orEmpty().contains(expectedMessage))
                assertEquals(expectPreviewMutation, audit.statements.isNotEmpty())
                assertEquals(before, snapshot())
                assertEquals(0, tracking.accessCount)
                assertEquals(preferencesBefore, fixture.preferences.snapshot())
            }
            assertEquals(before, snapshot())
        }
    }

    private fun SupportSQLiteDatabase.assertGuardRejectedBeforeMutation(
        stagingLimits: TonUpgradePayloadLimits,
        expectedMessage: String
    ) {
        val before = snapshot()
        val audit = MutationAudit(this)
        transaction {
            val failure = assertThrows(TonUpgradeSqlIntegrityException::class.java) {
                HistoricalNodeUpgradePreflight.requireSafeBeforeDb31SecretWrite(audit, stagingLimits)
            }
            assertTrue(failure.message.orEmpty(), failure.message.orEmpty().contains(expectedMessage))
            assertTrue(audit.statements.isEmpty())
            assertEquals(before, snapshot())
        }
        assertEquals(before, snapshot())
    }

    private data class Fixture(val name: String, val preferences: HashMapEncryptedPreferences)

    private fun install(suffix: String): Fixture {
        val name = "historical-node-preflight-$suffix"
        check(createdDatabases.add(name))
        val released = ReleasedSchemaFixtures.load(context, "2.0.0")
        assertEquals(30, released.databaseVersion)
        ReleasedSchemaFixtures.createDatabase(context, name, released)
        val preferences = HashMapEncryptedPreferences().apply {
            putEncryptedString(LEGACY_SECRET_KEY, legacySecret)
            putEncryptedString(chainSecretKey, chainSecret)
            putEncryptedString(SENTINEL_KEY, SENTINEL_VALUE)
        }
        val fixture = Fixture(name, preferences)
        withSource(fixture, sourceVersion = 30) {
            assertEquals(released.roomIdentityHash, string("SELECT identity_hash FROM room_master_table WHERE id = 42"))
            transaction {
                MigrateTablesToV2_30_31.migrate(this)
                version = 31
                execSQL(
                    "INSERT INTO meta_accounts(id, substratePublicKey, substrateCryptoType, substrateAccountId, " +
                        "ethereumPublicKey, ethereumAddress, name, isSelected, position) VALUES (?, ?, 'SR25519', ?, ?, ?, ?, 1, 0)",
                    arrayOf(META_ID, substratePublicKey, substratePublicKey.substrateAccountId(),
                        ethereumPublicKey, ethereumPublicKey.ethereumAddressFromPublicKey(), WALLET_NAME)
                )
                listOf(CUSTOM_CHAIN, DEFAULT_CHAIN).forEach { chain ->
                    execSQL(
                        "INSERT INTO chains(id, name, icon, prefix, isEthereumBased, isTestNet, hasCrowdloans) " +
                            "VALUES (?, ?, 'synthetic-icon', 2, 0, 0, 0)",
                        arrayOf(chain, "Synthetic chain ${chain.first()}")
                    )
                }
                execSQL(
                    "INSERT INTO chain_nodes(chainId, url, name, isActive, isDefault) VALUES (?, ?, ?, 1, 0)",
                    arrayOf(CUSTOM_CHAIN, CUSTOM_URL, CUSTOM_NAME)
                )
                execSQL(
                    "INSERT INTO chain_nodes(chainId, url, name, isActive, isDefault) VALUES (?, ?, ?, 1, 1)",
                    arrayOf(DEFAULT_CHAIN, DEFAULT_URL, DEFAULT_NAME)
                )
                execSQL(
                    "INSERT INTO chain_nodes(chainId, url, name, isActive, isDefault) VALUES (?, ?, 'Refreshable', 0, 1)",
                    arrayOf(DEFAULT_CHAIN, "wss://refreshable.invalid/rpc")
                )
                execSQL(
                    "INSERT INTO chain_accounts(metaId, chainId, publicKey, accountId, cryptoType) VALUES (?, ?, ?, ?, 'SR25519')",
                    arrayOf(META_ID, CUSTOM_CHAIN, substratePublicKey, substratePublicKey.substrateAccountId())
                )
                execSQL(
                    "INSERT INTO users(address, username, publicKey, cryptoType, position, networkType) " +
                        "VALUES ('synthetic-legacy-user', 'Legacy user 一', ?, 0, 0, 0)",
                    arrayOf(substratePublicKey.toPlainHexString(withPrefix = false))
                )
            }
            assertEquals(31, version)
            Db31UpgradeSqlPreflight.requireSafeBeforePreferenceAccess(this)
            query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        }
        return fixture
    }

    private fun withSource(
        fixture: Fixture,
        foreignKeys: Boolean = false,
        sourceVersion: Int = 31,
        action: SupportSQLiteDatabase.() -> Unit
    ) {
        val callback = object : SupportSQLiteOpenHelper.Callback(sourceVersion) {
            override fun onCreate(db: SupportSQLiteDatabase) = error("Released fixture was not installed")
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) =
                error("Unexpected setup migration: $oldVersion to $newVersion")
        }
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(fixture.name).callback(callback).build()
        )
        try {
            val database = helper.writableDatabase
            database.setForeignKeyConstraintsEnabled(foreignKeys)
            database.action()
        } finally {
            helper.close()
        }
    }

    private class MutationAudit(
        private val database: SupportSQLiteDatabase,
        private val beforeExec: (String) -> Unit = {}
    ) : SupportSQLiteDatabase by database {
        val statements = mutableListOf<String>()
        override fun execSQL(sql: String) {
            statements += sql
            beforeExec(sql)
            database.execSQL(sql)
        }
        override fun execSQL(sql: String, bindArgs: Array<out Any?>) {
            statements += sql
            beforeExec(sql)
            database.execSQL(sql, bindArgs)
        }
    }

    private class TrackingPreferences(private val delegate: HashMapEncryptedPreferences) : EncryptedPreferences by delegate {
        var accessCount = 0
            private set
        override fun requireDurableStorageHealthy() { accessCount += 1; delegate.requireDurableStorageHealthy() }
        override fun getDecryptedString(field: String): String? { accessCount += 1; return delegate.getDecryptedString(field) }
        override fun hasKey(field: String): Boolean { accessCount += 1; return delegate.hasKey(field) }
        override fun hasKeyWithPrefix(prefix: String): Boolean { accessCount += 1; return delegate.hasKeyWithPrefix(prefix) }
        override fun putEncryptedString(field: String, value: String) { accessCount += 1; delegate.putEncryptedString(field, value) }
        override fun removeKey(field: String) { accessCount += 1; delegate.removeKey(field) }
        override fun keysWithPrefixes(
            prefixes: Set<String>, maxResultCount: Int, maxKeyBytes: Int, maxTotalKeyBytes: Int, failOnOversizedMatch: Boolean
        ): Set<String> {
            accessCount += 1
            return delegate.keysWithPrefixes(prefixes, maxResultCount, maxKeyBytes, maxTotalKeyBytes, failOnOversizedMatch)
        }
        override fun replaceEncryptedStringsDurably(valuesToPut: Map<String, String>, keysToRemove: Set<String>) {
            accessCount += 1
            delegate.replaceEncryptedStringsDurably(valuesToPut, keysToRemove)
        }
        override fun quarantineEncryptedStringDurably(
            sourceKey: String, quarantineKey: String, expectedSnapshot: EncryptedPreferenceSnapshot
        ): Boolean {
            accessCount += 1
            return delegate.quarantineEncryptedStringDurably(sourceKey, quarantineKey, expectedSnapshot)
        }
    }

    private fun SupportSQLiteDatabase.expectedStagingBytes(): Long =
        payloadBytes("chain_nodes", NODE_COLUMNS) +
            payloadBytes("chains", CHAIN_COLUMNS) + payloadBytes("chain_accounts", CHAIN_ACCOUNT_COLUMNS) +
            payloadBytes("users", USER_COLUMNS) +
            payloadBytes("meta_accounts", columns("meta_accounts") - setOf("ethereumPublicKey", "ethereumAddress")) +
            scalar("SELECT SUM(MAX(COALESCE(length(CAST(ethereumPublicKey AS BLOB)), 0), 65) + " +
                "MAX(COALESCE(length(CAST(ethereumAddress AS BLOB)), 0), 20)) FROM meta_accounts")

    private fun SupportSQLiteDatabase.payloadBytes(table: String, columns: List<String>, where: String? = null): Long {
        val sum = columns.joinToString(" + ") { "COALESCE(length(CAST(`$it` AS BLOB)), 0)" }
        return scalar("SELECT COALESCE(SUM($sum), 0) FROM `$table`" + where?.let { " WHERE ($it)" }.orEmpty())
    }

    private fun SupportSQLiteDatabase.columns(table: String): List<String> = query("PRAGMA table_info(`$table`)").use { cursor ->
        val name = cursor.getColumnIndexOrThrow("name")
        buildList { while (cursor.moveToNext()) add(cursor.getString(name)) }
    }

    private fun SupportSQLiteDatabase.transaction(action: SupportSQLiteDatabase.() -> Unit) {
        beginTransaction()
        try { action(); setTransactionSuccessful() } finally { endTransaction() }
    }

    private data class Cell(val type: Int, val value: String?)
    private data class DatabaseSnapshot(
        val version: Int, val foreignKeys: Long,
        val schemas: Map<String, List<List<Cell>>>, val tables: Map<String, List<List<Cell>>>
    )

    private fun SupportSQLiteDatabase.snapshot(): DatabaseSnapshot {
        val schemas = linkedMapOf<String, List<List<Cell>>>()
        val tables = linkedMapOf<String, List<List<Cell>>>()
        listOf("main" to "sqlite_master", "temp" to "sqlite_temp_master").forEach { (schema, master) ->
            schemas[schema] = readRows("SELECT type, name, tbl_name, rootpage, sql FROM $master")
            query("SELECT name FROM $master WHERE type = 'table' ORDER BY name").use { cursor ->
                while (cursor.moveToNext()) {
                    val name = cursor.getString(0).replace("`", "``")
                    tables["$schema.$name"] = readRows("SELECT * FROM `$schema`.`$name`")
                }
            }
        }
        return DatabaseSnapshot(version, scalar("PRAGMA foreign_keys"), schemas, tables)
    }

    private fun SupportSQLiteDatabase.readRows(sql: String): List<List<Cell>> = query(sql).use { cursor ->
        buildList {
            while (cursor.moveToNext()) add(List(cursor.columnCount) { index ->
                val type = cursor.getType(index)
                Cell(type, when (type) {
                    Cursor.FIELD_TYPE_NULL -> null
                    Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index).toString()
                    Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(index).toRawBits().toString()
                    Cursor.FIELD_TYPE_STRING -> cursor.getString(index)
                    Cursor.FIELD_TYPE_BLOB -> cursor.getBlob(index).toPlainHexString(withPrefix = false)
                    else -> error("Unexpected SQLite storage class")
                })
            })
        }.sortedBy { row -> row.joinToString("") { cell -> "${cell.type}:${cell.value?.length}:${cell.value};" } }
    }

    private fun SupportSQLiteDatabase.scalar(sql: String): Long = query(sql).use { cursor ->
        assertTrue(cursor.moveToFirst()); val result = cursor.getLong(0); assertFalse(cursor.moveToNext()); result
    }
    private fun SupportSQLiteDatabase.string(sql: String): String = query(sql).use { cursor ->
        assertTrue(cursor.moveToFirst()); val result = cursor.getString(0); assertFalse(cursor.moveToNext()); result
    }
    private fun SupportSQLiteDatabase.blob(sql: String): ByteArray = query(sql).use { cursor ->
        assertTrue(cursor.moveToFirst()); val result = cursor.getBlob(0); assertFalse(cursor.moveToNext()); result
    }
    private fun HashMapEncryptedPreferences.snapshot(): Map<String, String> = keysWithPrefixes(
        setOf(""), 256, 1024, 65_536, true
    ).associateWith { checkNotNull(getDecryptedString(it)) }
    private fun oversizedCell() = "x".repeat((TON_UPGRADE_MAX_CELL_PAYLOAD_BYTES + 1).toInt())

    // DB31 availability baselines. Keep the earlier helper-level rejection tests
    // intact: these measure the complete production builder, not a direct preview.
    @Test
    fun db31HealthyBuilderArchivesOriginalNodesAndOpensTwice() = runBlocking {
        assertDb31NodeAvailability(Db31NodeAnomaly.NONE)
    }

    @Test
    fun db31UnknownNodeFlagDoesNotBlockHealthyWalletAcrossTwoOpens() = runBlocking {
        assertDb31NodeAvailability(Db31NodeAnomaly.UNKNOWN_FLAG)
    }

    @Test
    fun db31OrphanNodeDoesNotBlockHealthyWalletAcrossTwoOpens() = runBlocking {
        assertDb31NodeAvailability(Db31NodeAnomaly.ORPHAN)
    }

    @Test
    fun db31HealthyNodesStillRejectLateCorruptRecoveryMarkerBeforeWrites() {
        assertDb31CorruptRecoveryMarkerRejected(Db31NodeAnomaly.NONE)
    }

    @Test
    fun db31UnknownNodeFlagDoesNotMaskLateCorruptRecoveryMarker() {
        assertDb31CorruptRecoveryMarkerRejected(Db31NodeAnomaly.UNKNOWN_FLAG)
    }

    @Test
    fun db31OrphanNodeDoesNotMaskLateCorruptRecoveryMarker() {
        assertDb31CorruptRecoveryMarkerRejected(Db31NodeAnomaly.ORPHAN)
    }

    @Test
    fun unselectedOversizedNodeRejectsBeforeSqlCopyOrPreferenceAccess() {
        assertMigrationRejected("unselected-node-cell", "chain_nodes.name") {
            execSQL("UPDATE chain_nodes SET name = ? WHERE isDefault = 1 AND isActive = 0", arrayOf(oversizedCell()))
            assertEquals(1L, scalar("SELECT COUNT(*) FROM chain_nodes WHERE isDefault = 1 AND isActive = 0"))
        }
    }

    @Test
    fun nodeOrphanNeverToleratesEitherWalletChainAccountForeignKeyViolation() {
        listOf("chainId", "metaId").forEach { column ->
            val fixture = db31Fixture(Db31NodeAnomaly.ORPHAN, "wallet-fk-$column")
            withSource(fixture) {
                if (column == "chainId") {
                    execSQL("UPDATE chain_accounts SET chainId = ?", arrayOf("d".repeat(64)))
                } else {
                    execSQL("UPDATE chain_accounts SET metaId = 99999")
                }
                val violations = readRows("PRAGMA foreign_key_check")
                assertEquals(2, violations.size)
            }
            val before = db31SourceImage(fixture, 31)
            val preferencesBefore = fixture.preferences.snapshot()
            val writes = Db31PreferenceWriteAudit(fixture.preferences)
            val failure = assertThrows(
                jp.co.soramitsu.common.data.storage.encrypt.WalletPublicIdentityIntegrityException::class.java
            ) {
                db31OpenOrVerifyRollback(fixture, before, preferencesBefore, writes).close()
            }
            assertEquals("Version 31 The database contains a foreign-key violation", failure.message)
            assertEquals(0, writes.writeAttempts)
            assertEquals(preferencesBefore, fixture.preferences.snapshot())
            assertEquals(before, db31SourceImage(fixture, 31))
        }
    }

    @Test
    fun extraNodeForeignKeyDefinitionRejectsBeforePreferenceWrites() {
        val fixture = db31Fixture(Db31NodeAnomaly.NONE, "extra-node-fk")
        withSource(fixture) {
            val tableSql = string("SELECT sql FROM sqlite_master WHERE name = 'chain_nodes'")
            val indexSql = string("SELECT sql FROM sqlite_master WHERE name = 'index_chain_nodes_chainId'")
            val rows = db31RawRows("chain_nodes", NODE_COLUMNS)
            val closing = tableSql.lastIndexOf(')')
            check(closing > 0)
            val altered = tableSql.substring(0, closing) +
                ", FOREIGN KEY(name) REFERENCES meta_accounts(id) ON UPDATE NO ACTION ON DELETE CASCADE" +
                tableSql.substring(closing)
            transaction {
                execSQL("CREATE TEMP TABLE owned_db31_fk_nodes AS SELECT rowid AS source_rowid, * FROM chain_nodes")
                execSQL("DROP TABLE chain_nodes")
                execSQL(altered)
                execSQL(indexSql)
                execSQL("INSERT INTO chain_nodes(rowid, chainId, url, name, isActive, isDefault) " +
                    "SELECT source_rowid, chainId, url, name, isActive, isDefault FROM temp.owned_db31_fk_nodes")
                execSQL("DROP TABLE temp.owned_db31_fk_nodes")
            }
            assertEquals(rows, db31RawRows("chain_nodes", NODE_COLUMNS))
            assertEquals(2, readRows("PRAGMA foreign_key_list(chain_nodes)").size)
        }
        val before = db31SourceImage(fixture, 31)
        val preferencesBefore = fixture.preferences.snapshot()
        val writes = Db31PreferenceWriteAudit(fixture.preferences)
        val failure = assertThrows(
            jp.co.soramitsu.common.data.storage.encrypt.WalletPublicIdentityIntegrityException::class.java
        ) {
            db31OpenOrVerifyRollback(fixture, before, preferencesBefore, writes).close()
        }
        assertEquals("Version 31 chain_nodes has missing, extra, or incompatible foreign-key definitions", failure.message)
        assertEquals(0, writes.writeAttempts)
        assertEquals(preferencesBefore, fixture.preferences.snapshot())
        assertEquals(before, db31SourceImage(fixture, 31))
    }

    private enum class Db31NodeAnomaly { NONE, UNKNOWN_FLAG, ORPHAN }

    private fun db31Fixture(anomaly: Db31NodeAnomaly, suffix: String): Fixture {
        val fixture = install("db31-admission-${anomaly.name.lowercase()}-$suffix")
        // install() only validates the healthy source. No preflight is invoked
        // after the anomaly is prepared, outside the measured builder below.
        withSource(fixture, foreignKeys = false) {
            when (anomaly) {
                Db31NodeAnomaly.NONE -> Unit
                Db31NodeAnomaly.UNKNOWN_FLAG -> execSQL(
                    "UPDATE chain_nodes SET isDefault = 2 WHERE url = ?", arrayOf(CUSTOM_URL)
                )
                Db31NodeAnomaly.ORPHAN -> execSQL(
                    "UPDATE chain_nodes SET chainId = ? WHERE url = ?", arrayOf("c".repeat(64), CUSTOM_URL)
                )
            }
            assertEquals(0L, scalar(
                "SELECT COUNT(*) FROM sqlite_master WHERE name IN " +
                    "('endpoint_archive_v1','endpoint_archive_header_v1','endpoint_archive_schema_v1')"
            ))
            assertEquals(3L, scalar("SELECT COUNT(*) FROM chain_nodes"))
            assertEquals(2L, scalar("SELECT COUNT(*) FROM chains"))
            assertEquals(1L, scalar("SELECT COUNT(*) FROM meta_accounts"))
            assertEquals(1L, scalar("SELECT COUNT(*) FROM chain_accounts"))
            query("PRAGMA foreign_key_check").use { cursor ->
                if (anomaly == Db31NodeAnomaly.ORPHAN) {
                    assertTrue(cursor.moveToFirst())
                    assertEquals("chain_nodes", cursor.getString(0))
                    assertEquals("chains", cursor.getString(2))
                    assertFalse(cursor.moveToNext())
                } else {
                    assertFalse(cursor.moveToFirst())
                }
            }
        }
        return fixture
    }

    private suspend fun assertDb31NodeAvailability(anomaly: Db31NodeAnomaly) {
        val fixture = db31Fixture(anomaly, "availability")
        val original = db31SourceImage(fixture, 31)
        val originalNodes = checkNotNull(original.rows["main.chain_nodes"])
        val originalNodeSchema = original.nodeSchema
        var firstPreferences: Map<String, String>? = null
        var firstArchiveHeader: List<Db31RawRow>? = null
        // Same-process reopen only; no process-death or live-network claim.
        repeat(2) { openIndex ->
            val sourceVersion = if (openIndex == 0) 31 else APP_DATABASE_VERSION
            val before = db31SourceImage(fixture, sourceVersion)
            val preferencesBefore = fixture.preferences.snapshot()
            val writes = Db31PreferenceWriteAudit(fixture.preferences)
            val database = db31OpenOrVerifyRollback(fixture, before, preferencesBefore, writes)
            try {
                val sql = database.openHelper.writableDatabase
                assertEquals(APP_DATABASE_VERSION, sql.version)
                assertEquals("ok", sql.string("PRAGMA integrity_check"))
                sql.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
                assertTrue(ModernEndpointArchive.requireSafe(sql))
                assertEquals(
                    "Earliest original endpoint rowids, storage classes and bytes must survive",
                    originalNodes, sql.db31RawRows("main.endpoint_archive_v1", NODE_COLUMNS, "source_rowid")
                )
                assertEquals(
                    "Original schema rowids and SQL bytes must survive",
                    originalNodeSchema, sql.db31NodeSchema(archived = true)
                )
                assertEquals(31L, sql.scalar("SELECT source_user_version FROM endpoint_archive_header_v1 WHERE id = 1"))
                assertEquals(3L, sql.scalar("SELECT source_row_count FROM endpoint_archive_header_v1 WHERE id = 1"))
                assertEquals(
                    originalNodeSchema.size.toLong(),
                    sql.scalar("SELECT source_schema_count FROM endpoint_archive_header_v1 WHERE id = 1")
                )
                // The edge is intentionally not fixed to 31: capture at 39 is
                // valid if the original node data/schema have survived intact.
                val header = sql.db31RawRows(
                    "main.endpoint_archive_header_v1", sql.columns("endpoint_archive_header_v1")
                )
                firstArchiveHeader?.let { assertEquals(it, header) }
                firstArchiveHeader = header
                val wallet = checkNotNull(database.metaAccountDao().getMetaAccount(META_ID))
                assertEquals(WALLET_NAME, wallet.name)
                assertEquals(1L, sql.scalar("SELECT COUNT(*) FROM meta_accounts"))
                assertArrayEquals(substratePublicKey, sql.blob("SELECT substratePublicKey FROM meta_accounts"))
                assertArrayEquals(substratePublicKey.substrateAccountId(), sql.blob("SELECT substrateAccountId FROM meta_accounts"))
                assertEquals(CryptoType.SR25519.name, sql.string("SELECT substrateCryptoType FROM meta_accounts"))
                assertEquals(1L, sql.scalar("SELECT COUNT(*) FROM chain_accounts"))
                assertEquals(CUSTOM_CHAIN, sql.string("SELECT chainId FROM chain_accounts"))
                assertEquals(META_ID, sql.scalar("SELECT metaId FROM chain_accounts"))
                assertArrayEquals(substratePublicKey, sql.blob("SELECT publicKey FROM chain_accounts"))
                assertArrayEquals(substratePublicKey.substrateAccountId(), sql.blob("SELECT accountId FROM chain_accounts"))
                assertEquals(CryptoType.SR25519.name, sql.string("SELECT cryptoType FROM chain_accounts"))
                checkNotNull(SubstrateSecretStore(fixture.preferences).get(
                    META_ID, substratePublicKey, CryptoType.SR25519, substratePublicKey.substrateAccountId()
                ))
                checkNotNull(EthereumSecretStore(fixture.preferences).get(
                    META_ID, sql.blob("SELECT ethereumPublicKey FROM meta_accounts"),
                    sql.blob("SELECT ethereumAddress FROM meta_accounts")
                ))
                assertEquals(chainSecret, fixture.preferences.getDecryptedString(chainSecretKey))
                assertFalse(fixture.preferences.hasKey(LEGACY_SECRET_KEY))
                assertFalse(fixture.preferences.hasKeyWithPrefix(WalletSecretQuarantine.KEY_PREFIX))
                assertFalse(fixture.preferences.hasKeyWithPrefix(WalletPublicIdentityRecovery.KEY_PREFIX))
                assertEquals(SENTINEL_VALUE, fixture.preferences.getDecryptedString(SENTINEL_KEY))
                val selectedDefault = database.chainDao().getNode(DEFAULT_CHAIN, DEFAULT_URL)
                assertEquals(DEFAULT_NAME, selectedDefault.name)
                assertTrue(selectedDefault.isActive)
                assertTrue(selectedDefault.isDefault)
                if (anomaly == Db31NodeAnomaly.NONE) {
                    val custom = database.chainDao().getNode(CUSTOM_CHAIN, CUSTOM_URL)
                    assertEquals(CUSTOM_NAME, custom.name)
                    assertTrue(custom.isActive)
                    assertFalse(custom.isDefault)
                    assertEquals(2L, sql.scalar("SELECT COUNT(*) FROM chain_nodes"))
                } else {
                    // A retained anomaly is never promoted into an operational
                    // endpoint, and the orphan must not manufacture a parent.
                    sql.query("SELECT 1 FROM chain_nodes WHERE url = ?", arrayOf(CUSTOM_URL)).use {
                        assertFalse(it.moveToFirst())
                    }
                    sql.query("SELECT 1 FROM chains WHERE id = ?", arrayOf("c".repeat(64))).use {
                        assertFalse(it.moveToFirst())
                    }
                    assertEquals(1L, sql.scalar("SELECT COUNT(*) FROM chain_nodes"))
                }
                val preferencesAfter = fixture.preferences.snapshot()
                firstPreferences?.let { assertEquals(it, preferencesAfter) }
                firstPreferences = preferencesAfter
            } finally {
                database.close()
            }
        }
    }

    private fun assertDb31CorruptRecoveryMarkerRejected(anomaly: Db31NodeAnomaly) {
        val fixture = db31Fixture(anomaly, "recovery-marker")
        val lateId = META_ID + 1L
        val lateActiveKey = "$lateId:ACCESS_SECRETS"
        val markerKey = WalletPublicIdentityRecovery.keyFor(lateId, lateActiveKey)
        val invalidMarker = "invalid-public-fixture-marker-for-wallet-$lateId"
        withSource(fixture) {
            execSQL(
                "INSERT INTO meta_accounts(id, substratePublicKey, substrateCryptoType, substrateAccountId, " +
                    "ethereumPublicKey, ethereumAddress, name, isSelected, position) " +
                    "SELECT ?, substratePublicKey, substrateCryptoType, substrateAccountId, " +
                    "ethereumPublicKey, ethereumAddress, 'Late recovery-state fixture', 0, 1 " +
                    "FROM meta_accounts WHERE id = ?", arrayOf(lateId, META_ID)
            )
            assertEquals(2L, scalar("SELECT COUNT(*) FROM meta_accounts"))
            assertEquals(lateId, scalar("SELECT id FROM meta_accounts ORDER BY rowid DESC LIMIT 1"))
        }
        fixture.preferences.putEncryptedString(lateActiveKey, legacySecret)
        fixture.preferences.putEncryptedString(markerKey, invalidMarker)
        assertFalse(fixture.preferences.hasKey(WalletPublicIdentityRecovery.keyFor(META_ID, LEGACY_SECRET_KEY)))
        assertEquals(setOf(markerKey), fixture.preferences.keysWithPrefixes(
            setOf(WalletPublicIdentityRecovery.KEY_PREFIX), 16, 1024, 4096, true
        ))
        assertEquals(invalidMarker, fixture.preferences.getDecryptedString(markerKey))
        val before = db31SourceImage(fixture, 31)
        val preferencesBefore = fixture.preferences.snapshot()
        val writes = Db31PreferenceWriteAudit(fixture.preferences)
        val failure = runCatching {
            db31OpenOrVerifyRollback(fixture, before, preferencesBefore, writes).close()
        }.exceptionOrNull() ?: error("DB31 admitted the corrupt recovery-state marker for wallet $lateId")
        val walletFailure = generateSequence(failure) { it.cause }.take(16).firstOrNull {
            it is jp.co.soramitsu.common.data.storage.encrypt.WalletRecoveryStateIntegrityException &&
                it.message == "A wallet public-identity recovery marker is invalid"
        }
        if (walletFailure == null) {
            throw AssertionError(
                "DB31_LATE_RECOVERY_MARKER_MASKED: expected wallet $lateId marker rejection; " +
                    "node anomaly=$anomaly; actual=${failure.javaClass.name}: ${failure.message}", failure
            )
        }
        // Diagnostic has no row ID, so bind it to the only marker and its exact
        // late row before and after the failed production open.
        assertEquals(invalidMarker, fixture.preferences.getDecryptedString(markerKey))
        assertEquals(preferencesBefore, fixture.preferences.snapshot())
        assertEquals(0, writes.writeAttempts)
        assertEquals(before, db31SourceImage(fixture, 31))
    }

    private fun db31OpenOrVerifyRollback(
        fixture: Fixture,
        before: Db31SourceImage,
        preferencesBefore: Map<String, String>,
        writes: Db31PreferenceWriteAudit
    ): AppDatabase {
        var database: AppDatabase? = null
        try {
            val opened = AppDatabase.create(
                context = context, databaseName = fixture.name,
                storeV1 = SecretStoreV1Impl(writes), storeV2 = SecretStoreV2(writes),
                encryptedPreferences = writes,
                substrateSecretStore = SubstrateSecretStore(writes),
                ethereumSecretStore = EthereumSecretStore(writes)
            )
            database = opened
            opened.openHelper.writableDatabase
            return opened
        } catch (failure: Throwable) {
            try {
                database?.close()
                assertEquals("Rejected DB31 open must attempt zero preference writes", 0, writes.writeAttempts)
                assertEquals(preferencesBefore, fixture.preferences.snapshot())
                assertEquals("Rejected builder must roll back every source cell and schema", before,
                    db31SourceImage(fixture, before.version))
                println("DB31_BUILDER_REJECTED: sourceVersion=${before.version}; zeroPreferenceWrites=true; " +
                    "exactSqlRollback=true; failure=${failure.javaClass.name}: ${failure.message}")
            } catch (preservationFailure: Throwable) {
                preservationFailure.addSuppressed(failure)
                throw preservationFailure
            }
            // Positive availability tests remain red on the current guard.
            // The preservation checks above must never turn rejection green.
            throw failure
        }
    }

    private data class Db31RawCell(val storageClass: String, val value: String?)
    private data class Db31RawRow(val rowid: Long, val cells: List<Db31RawCell>)
    private data class Db31SourceImage(
        val version: Int,
        val schemas: Map<String, List<Db31RawRow>>,
        val rows: Map<String, List<Db31RawRow>>,
        val nodeSchema: List<Db31RawRow>
    )

    private fun db31SourceImage(fixture: Fixture, sourceVersion: Int): Db31SourceImage {
        var image: Db31SourceImage? = null
        withSource(fixture, sourceVersion = sourceVersion) {
            val schemas = linkedMapOf<String, List<Db31RawRow>>()
            val rows = linkedMapOf<String, List<Db31RawRow>>()
            listOf("main" to "sqlite_master", "temp" to "sqlite_temp_master").forEach { (schema, master) ->
                schemas[schema] = db31RawRows(master, listOf("type", "name", "tbl_name", "rootpage", "sql"))
                query("SELECT name FROM $master WHERE type = 'table' ORDER BY name").use { cursor ->
                    while (cursor.moveToNext()) {
                        val name = cursor.getString(0)
                        val quoted = "`${name.replace("`", "``")}`"
                        val fields = query("PRAGMA $schema.table_info($quoted)").use { columns ->
                            buildList { while (columns.moveToNext()) add(columns.getString(columns.getColumnIndexOrThrow("name"))) }
                        }
                        rows["$schema.$name"] = db31RawRows("$schema.$quoted", fields)
                    }
                }
            }
            image = Db31SourceImage(version, schemas, rows, db31NodeSchema(archived = false))
        }
        return checkNotNull(image)
    }

    private fun SupportSQLiteDatabase.db31NodeSchema(archived: Boolean): List<Db31RawRow> = db31RawRows(
        table = if (archived) "main.endpoint_archive_schema_v1" else "main.sqlite_master",
        fields = listOf("type", "name", "tbl_name", "sql"),
        rowidColumn = if (archived) "source_schema_rowid" else "rowid",
        predicate = if (archived) "1" else "name = 'chain_nodes' COLLATE NOCASE OR tbl_name = 'chain_nodes' COLLATE NOCASE"
    )

    private fun SupportSQLiteDatabase.db31RawRows(
        table: String,
        fields: List<String>,
        rowidColumn: String = "rowid",
        predicate: String = "1"
    ): List<Db31RawRow> {
        val projection = fields.joinToString(",") { field ->
            val quoted = "`${field.replace("`", "``")}`"
            "typeof($quoted), CASE WHEN typeof($quoted) IN ('text','blob') " +
                "THEN hex(CAST($quoted AS BLOB)) ELSE $quoted END"
        }
        return query("SELECT $rowidColumn,$projection FROM $table WHERE ($predicate) ORDER BY $rowidColumn").use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(Db31RawRow(cursor.getLong(0), fields.indices.map { index ->
                    val storageClass = cursor.getString(1 + index * 2)
                    val valueIndex = 2 + index * 2
                    Db31RawCell(storageClass, when (storageClass) {
                        "null" -> null
                        "integer" -> cursor.getLong(valueIndex).toString()
                        "real" -> cursor.getDouble(valueIndex).toRawBits().toString()
                        "text", "blob" -> cursor.getString(valueIndex)
                        else -> error("Unexpected SQLite storage class: $storageClass")
                    })
                }))
            }
        }
    }

    private class Db31PreferenceWriteAudit(private val delegate: EncryptedPreferences) : EncryptedPreferences by delegate {
        var writeAttempts = 0
            private set
        override fun putEncryptedString(field: String, value: String) {
            writeAttempts += 1
            delegate.putEncryptedString(field, value)
        }
        override fun removeKey(field: String) {
            writeAttempts += 1
            delegate.removeKey(field)
        }
        override fun savePinCodeDurably(pin: String) {
            writeAttempts += 1
            delegate.savePinCodeDurably(pin)
        }
        override fun saveRecoveredPinCodeDurably(
            pin: String, expected: jp.co.soramitsu.common.data.storage.encrypt.PinResetSnapshot
        ) {
            writeAttempts += 1
            delegate.saveRecoveredPinCodeDurably(pin, expected)
        }
        override fun replaceEncryptedStringsDurably(valuesToPut: Map<String, String>, keysToRemove: Set<String>) {
            writeAttempts += 1
            delegate.replaceEncryptedStringsDurably(valuesToPut, keysToRemove)
        }
        override fun replaceEncryptedStringsDurablyIfStatesMatch(
            expectedStates: Map<String, EncryptedPreferenceSnapshot?>,
            valuesToPut: Map<String, String>,
            keysToRemove: Set<String>,
            snapshotMoves: List<jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferenceSnapshotMove>
        ): Boolean {
            writeAttempts += 1
            return delegate.replaceEncryptedStringsDurablyIfStatesMatch(expectedStates, valuesToPut, keysToRemove, snapshotMoves)
        }
        override fun quarantineEncryptedStringDurably(
            sourceKey: String, quarantineKey: String, expectedSnapshot: EncryptedPreferenceSnapshot
        ): Boolean {
            writeAttempts += 1
            return delegate.quarantineEncryptedStringDurably(sourceKey, quarantineKey, expectedSnapshot)
        }
    }

    @Test
    fun db31EmptyNodesWithoutLegacyArchivePreserveHealthyWalletAcrossTwoOpens() = runBlocking {
        val fixture = db31EmptyNodeFixture(commentedSchema = false)
        val original = db31SourceImage(fixture, 31)
        var firstPreferences: Map<String, String>? = null
        var firstHeader: List<Db31RawRow>? = null
        repeat(2) { openIndex ->
            val before = db31SourceImage(fixture, if (openIndex == 0) 31 else APP_DATABASE_VERSION)
            val writes = Db31PreferenceWriteAudit(fixture.preferences)
            val database = db31OpenOrVerifyRollback(fixture, before, fixture.preferences.snapshot(), writes)
            try {
                val sql = database.openHelper.writableDatabase
                assertEquals(APP_DATABASE_VERSION, sql.version)
                assertEquals("ok", sql.string("PRAGMA integrity_check"))
                sql.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
                assertTrue(ModernEndpointArchive.requireSafe(sql))
                assertEquals(emptyList<Db31RawRow>(), sql.db31RawRows("endpoint_archive_v1", NODE_COLUMNS, "source_rowid"))
                assertEquals(original.nodeSchema, sql.db31NodeSchema(archived = true))
                assertEquals(0L, sql.scalar("SELECT source_row_count FROM endpoint_archive_header_v1"))
                assertEquals(0L, sql.scalar("SELECT source_payload_bytes FROM endpoint_archive_header_v1"))
                assertEquals(31L, sql.scalar("SELECT source_user_version FROM endpoint_archive_header_v1"))
                val header = sql.db31RawRows("endpoint_archive_header_v1", sql.columns("endpoint_archive_header_v1"))
                firstHeader?.let { assertEquals(it, header) }
                firstHeader = header
                assertEquals(0L, sql.scalar("SELECT COUNT(*) FROM chain_nodes"))
                assertEquals(1L, sql.scalar("SELECT COUNT(*) FROM meta_accounts"))
                assertEquals(1L, sql.scalar("SELECT COUNT(*) FROM chain_accounts"))
                assertEquals(WALLET_NAME, checkNotNull(database.metaAccountDao().getMetaAccount(META_ID)).name)
                assertArrayEquals(substratePublicKey, sql.blob("SELECT substratePublicKey FROM meta_accounts"))
                assertArrayEquals(substratePublicKey.substrateAccountId(), sql.blob("SELECT substrateAccountId FROM meta_accounts"))
                assertEquals(CryptoType.SR25519.name, sql.string("SELECT substrateCryptoType FROM meta_accounts"))
                assertEquals(CUSTOM_CHAIN, sql.string("SELECT chainId FROM chain_accounts"))
                assertEquals(META_ID, sql.scalar("SELECT metaId FROM chain_accounts"))
                assertArrayEquals(substratePublicKey, sql.blob("SELECT publicKey FROM chain_accounts"))
                assertArrayEquals(substratePublicKey.substrateAccountId(), sql.blob("SELECT accountId FROM chain_accounts"))
                assertEquals(CryptoType.SR25519.name, sql.string("SELECT cryptoType FROM chain_accounts"))
                checkNotNull(SubstrateSecretStore(fixture.preferences).get(
                    META_ID, substratePublicKey, CryptoType.SR25519, substratePublicKey.substrateAccountId()
                ))
                checkNotNull(EthereumSecretStore(fixture.preferences).get(
                    META_ID, sql.blob("SELECT ethereumPublicKey FROM meta_accounts"),
                    sql.blob("SELECT ethereumAddress FROM meta_accounts")
                ))
                assertEquals(chainSecret, fixture.preferences.getDecryptedString(chainSecretKey))
                assertEquals(SENTINEL_VALUE, fixture.preferences.getDecryptedString(SENTINEL_KEY))
                assertFalse(fixture.preferences.hasKey(LEGACY_SECRET_KEY))
                assertFalse(fixture.preferences.hasKeyWithPrefix(WalletSecretQuarantine.KEY_PREFIX))
                assertFalse(fixture.preferences.hasKeyWithPrefix(WalletPublicIdentityRecovery.KEY_PREFIX))
                val afterPreferences = fixture.preferences.snapshot()
                firstPreferences?.let { assertEquals(it, afterPreferences) }
                firstPreferences = afterPreferences
            } finally {
                database.close()
            }
        }
    }

    @Test
    fun db31EmptyCommentedNodeSchemaRejectsBeforeAnyPreferenceWrite() {
        val fixture = db31EmptyNodeFixture(commentedSchema = true)
        val before = db31SourceImage(fixture, 31)
        val preferencesBefore = fixture.preferences.snapshot()
        val writes = Db31PreferenceWriteAudit(fixture.preferences)
        var database: AppDatabase? = null
        val failure = try {
            runCatching {
                val opened = AppDatabase.create(
                    context = context, databaseName = fixture.name,
                    storeV1 = SecretStoreV1Impl(writes), storeV2 = SecretStoreV2(writes),
                    encryptedPreferences = writes,
                    substrateSecretStore = SubstrateSecretStore(writes),
                    ethereumSecretStore = EthereumSecretStore(writes)
                )
                database = opened
                opened.openHelper.writableDatabase
            }.exceptionOrNull()
        } finally {
            database?.close()
        }
        // Record all observations before asserting the expected guard, so the
        // baseline exposes both SQL rollback and any irreversible preference
        // writes. Only synthetic fixture values exist; never print their bytes.
        val after = db31SourceImage(fixture, 31)
        val preferencesAfter = fixture.preferences.snapshot()
        val sqlExact = before == after
        val preferencesExact = preferencesBefore == preferencesAfter
        println("DB31_EMPTY_NODE_SCHEMA_ADMISSION: sqlExact=$sqlExact; preferencesExact=$preferencesExact; " +
            "preferenceWriteAttempts=${writes.writeAttempts}; failure=${failure?.javaClass?.name}: ${failure?.message}")
        val archiveFailure = failure?.let {
            generateSequence(it) { cause -> cause.cause }.take(16).firstOrNull { cause ->
                cause is IllegalStateException && cause.message == "Modern endpoint source has unsupported DDL tokens"
            }
        }
        if (archiveFailure == null) {
            throw AssertionError("Expected the strict archive comment-DDL diagnostic from actual DB31 builder", failure)
        }
        assertEquals("Source schema, all typed cells and rowids must roll back", before, after)
        if (writes.writeAttempts != 0 || !preferencesExact) {
            throw AssertionError(
                "DB31_EMPTY_ARCHIVE_REJECTION_AFTER_PREFERENCE_WRITE: attempts=${writes.writeAttempts}; " +
                    "preferencesExact=$preferencesExact; sqlExact=$sqlExact", failure
            )
        }
        assertEquals(preferencesBefore, preferencesAfter)
    }

    private fun db31EmptyNodeFixture(commentedSchema: Boolean): Fixture {
        val fixture = install(if (commentedSchema) "empty-commented-schema" else "empty-canonical-schema")
        withSource(fixture, foreignKeys = false) {
            // Only this owned fixture loses the optional legacy source. The
            // production change must neither drop nor normalize user data.
            execSQL("DROP TABLE nodes")
            execSQL("DELETE FROM chain_nodes")
            if (commentedSchema) {
                val tableSql = string("SELECT sql FROM sqlite_master WHERE type = 'table' AND name = 'chain_nodes'")
                val indexSql = string("SELECT sql FROM sqlite_master WHERE type = 'index' AND name = 'index_chain_nodes_chainId'")
                val opening = tableSql.indexOf('(')
                check(opening >= 0)
                val commentedSql = tableSql.substring(0, opening + 1) +
                    " /* owned empty-node fixture */ " + tableSql.substring(opening + 1)
                // Rebuild the empty table using exactly its original definition
                // plus a harmless comment; ordinary PRAGMA shape stays the same.
                val tableInfo = readRows("PRAGMA table_info(chain_nodes)")
                val foreignKeys = readRows("PRAGMA foreign_key_list(chain_nodes)")
                val indexes = readRows("PRAGMA index_list(chain_nodes)")
                execSQL("DROP TABLE chain_nodes")
                execSQL(commentedSql)
                execSQL(indexSql)
                assertEquals(tableInfo, readRows("PRAGMA table_info(chain_nodes)"))
                assertEquals(foreignKeys, readRows("PRAGMA foreign_key_list(chain_nodes)"))
                assertEquals(indexes, readRows("PRAGMA index_list(chain_nodes)"))
                assertTrue(string("SELECT sql FROM sqlite_master WHERE name = 'chain_nodes'").contains("/* owned empty-node fixture */"))
            }
            assertEquals(31, version)
            assertEquals(0L, scalar("SELECT COUNT(*) FROM chain_nodes"))
            assertEquals(0L, scalar("SELECT COUNT(*) FROM sqlite_master WHERE name = 'nodes'"))
            assertEquals(0L, scalar("SELECT COUNT(*) FROM sqlite_master WHERE name LIKE 'endpoint_archive%'") )
            assertEquals(1L, scalar("SELECT COUNT(*) FROM meta_accounts"))
            assertEquals(1L, scalar("SELECT COUNT(*) FROM chain_accounts"))
            query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
            // No DB31 or archive preflight is called here; both run only via
            // the measured production builder after the snapshots are captured.
        }
        return fixture
    }

    private companion object {
        const val META_ID = 30L
        const val LEGACY_SECRET_KEY = "$META_ID:ACCESS_SECRETS"
        const val SENTINEL_KEY = "node-preflight-unrelated"
        const val SENTINEL_VALUE = "must remain exact"
        const val WALLET_NAME = "Historical node preflight wallet"
        val CUSTOM_CHAIN = "a".repeat(64)
        val DEFAULT_CHAIN = "b".repeat(64)
        const val CUSTOM_URL = "wss://custom-preflight.invalid/rpc"
        const val DEFAULT_URL = "wss://selected-default.invalid/rpc"
        const val CUSTOM_NAME = "Owned custom 一"
        const val DEFAULT_NAME = "Selected default"
        val NODE_COLUMNS = listOf("chainId", "url", "name", "isActive", "isDefault")
        val CHAIN_COLUMNS = listOf(
            "id", "parentId", "name", "icon", "prefix", "isEthereumBased", "isTestNet", "hasCrowdloans", "url", "overridesCommon",
            "staking_url", "staking_type", "history_url", "history_type", "crowdloans_url", "crowdloans_type"
        )
        val CHAIN_ACCOUNT_COLUMNS = listOf("metaId", "chainId", "publicKey", "accountId", "cryptoType")
        val USER_COLUMNS = listOf("address", "username", "publicKey", "cryptoType", "position")
        // Independent protected/mixed-table aggregate; pure caches do not consume
        // the TON payload budget. Archive and retained-node budgets remain separate.
        val TON_PROTECTED_PAYLOAD_TABLES = listOf(
            "meta_accounts", "chain_accounts", "favorite_chains", "assets"
        )
    }
}
