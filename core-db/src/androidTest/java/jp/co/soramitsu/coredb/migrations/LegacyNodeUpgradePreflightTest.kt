package jp.co.soramitsu.coredb.migrations

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteException
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import java.security.MessageDigest
import jp.co.soramitsu.common.data.secrets.v1.SecretStoreV1Impl
import jp.co.soramitsu.common.data.secrets.v2.KeyPairSchema
import jp.co.soramitsu.common.data.secrets.v2.SecretStoreV2
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecretStore
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecretStore
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecrets
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferenceSnapshot
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferenceSnapshotMove
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.common.utils.substrateAccountId
import jp.co.soramitsu.core.model.SecuritySource
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.coredb.APP_DATABASE_VERSION
import jp.co.soramitsu.coredb.AppDatabase
import jp.co.soramitsu.coredb.migrations.fixtures.ReleasedSchemaFixtures
import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAddress
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

/** Exact two released DB27 layouts, real registry creation, and public synthetic keys only. */
class LegacyNodeUpgradePreflightTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val ownedDatabases = mutableSetOf<String>()
    private val keypair by lazy {
        SubstrateKeypairFactory.generate(
            EncryptionType.SR25519, ByteArray(32) { (it + 37).toByte() }, emptyList()
        )
    }
    private val address by lazy { keypair.publicKey.toAddress(2) }
    private val publicHex by lazy { keypair.publicKey.toHexString(withPrefix = false) }

    @After
    fun removeOwnedDatabases() { ownedDatabases.forEach(context::deleteDatabase) }

    @Test
    fun bothReleasedVersion27LayoutsPreserveNodesWalletAndExactLegacyUserLedgerAcrossTwoOpens() = runBlocking {
        listOf("1.9.13", "1.9.15").forEach { release ->
            val fixture = install(release, "full")
            val legacySecret = fixture.preferences.getDecryptedString("security_source_$address")
            var firstPreferences: Map<String, String>? = null
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
                    assertEquals("ok", sql.text("PRAGMA integrity_check"))
                    sql.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
                    assertEquals(1L, sql.scalar("SELECT COUNT(*) FROM meta_accounts"))
                    val id = sql.scalar("SELECT id FROM meta_accounts")
                    assertEquals(USER_NAME, sql.text("SELECT name FROM meta_accounts"))
                    sql.query("SELECT substratePublicKey FROM meta_accounts").use {
                        assertTrue(it.moveToFirst()); assertArrayEquals(keypair.publicKey, it.getBlob(0))
                    }
                    checkNotNull(SubstrateSecretStore(fixture.preferences).get(
                        id, keypair.publicKey, CryptoType.SR25519, keypair.publicKey.substrateAccountId()
                    ))
                    assertEquals(legacySecret, fixture.preferences.getDecryptedString("security_source_$address"))
                    assertEquals(SENTINEL_VALUE, fixture.preferences.getDecryptedString(SENTINEL_KEY))
                    assertEquals(
                        listOf(listOf(address, USER_NAME, publicHex, CryptoType.SR25519.ordinal.toString(), "0", "PRESERVED_PENDING_REVIEW")),
                        sql.strings("SELECT address, username, publicKey, cryptoType, position, recoveryState FROM legacy_users_recovery")
                    )
                    assertEquals(
                        listOf(
                            listOf(KUSAMA, CUSTOM_URL, CUSTOM_NAME, "1", "0"),
                            listOf(POLKADOT, DEFAULT_URL, DEFAULT_NAME, "1", "1")
                        ).sortedBy { it[0] },
                        sql.strings("SELECT chainId, url, name, isActive, isDefault FROM chain_nodes ORDER BY chainId")
                    )
                    assertEquals(2L, sql.scalar("SELECT COUNT(*) FROM chains"))
                    val after = fixture.preferences.snapshot()
                    firstPreferences?.let { previous -> assertEquals(previous, after) }
                    firstPreferences = after
                } finally { database.close() }
            }
        }
    }

    @Test
    fun nestedPreviewRestoresAllSqlAndAllowsTheOuterTransactionToCommit() {
        val fixture = prepared28("preview-success")
        withSource(fixture) {
            val before = snapshot()
            transaction {
                HistoricalNodeUpgradePreflight.requireSafeBeforeV2SecretWrite(this)
                assertEquals(before, snapshot())
                assertTrue(inTransaction())
                execSQL("UPDATE temp.owned_legacy_marker SET note = 'committed after preview'")
            }
            assertEquals("committed after preview", text("SELECT note FROM temp.owned_legacy_marker"))
            assertEquals(28, version)
            assertEquals(0L, scalar("SELECT COUNT(*) FROM chains"))
            assertEquals(3L, scalar("SELECT COUNT(*) FROM nodes"))
        }
    }

    @Test
    fun lateNestedPreviewFailureRestoresAllSqlAndAllowsTheOuterTransactionToCommit() {
        val fixture = prepared28("preview-failure")
        withSource(fixture) {
            val before = snapshot()
            val original = SQLiteException("owned late legacy preview failure")
            var injected = false
            val audit = MutationAudit(this) { sql ->
                if (sql.contains("remoteAssetsSource")) { injected = true; throw original }
            }
            transaction {
                val actual = assertThrows(SQLiteException::class.java) {
                    HistoricalNodeUpgradePreflight.requireSafeBeforeV2SecretWrite(audit)
                }
                assertSame(original, actual)
                assertTrue(injected)
                assertTrue(actual.suppressed.isEmpty())
                assertEquals(before, snapshot())
                assertTrue(inTransaction())
                execSQL("UPDATE temp.owned_legacy_marker SET note = 'committed after failure'")
            }
            assertEquals("committed after failure", text("SELECT note FROM temp.owned_legacy_marker"))
        }
    }

    @Test
    fun actualV2LatePreviewFailureOccursBeforeAnyPreferenceWriteAndOuterRollbackIsExact() {
        val fixture = version28("v2-late")
        withSource(fixture) {
            val before = snapshot()
            val preferencesBefore = fixture.preferences.snapshot()
            val tracking = TrackingPreferences(fixture.preferences)
            val original = SQLiteException("owned V2 final-preview-edge failure")
            var injected = false
            var prepared: Map<String, List<List<String>>>? = null
            val audit = MutationAudit(this) { sql ->
                if (sql == "SAVEPOINT fearless_upgrade_preview" && prepared == null) prepared = snapshot()
                if (sql.contains("remoteAssetsSource")) { injected = true; throw original }
            }
            beginTransaction()
            try {
                val actual = assertThrows(SQLiteException::class.java) {
                    V2Migration(SecretStoreV1Impl(tracking), tracking).migrate(audit)
                }
                assertSame(original, actual)
                assertTrue(injected)
                assertTrue(tracking.reads > 0)
                assertEquals(0, tracking.writes)
                assertEquals(checkNotNull(prepared), snapshot())
                assertEquals(preferencesBefore, fixture.preferences.snapshot())
            } finally { endTransaction() }
            assertEquals(before, snapshot())
        }
    }

    @Test
    fun actualV2UnknownLegacyNetworkPreservesArchiveAndHealthyWalletStillOpens() {
        assertHealthyArchiveUpgrade("unknown") {
            execSQL("UPDATE nodes SET networkType = 15 WHERE isDefault = 0")
        }
    }

    @Test
    fun actualV2ConflictingLegacyNamesPreserveArchiveAndHealthyWalletStillOpens() {
        assertHealthyArchiveUpgrade("conflicting") {
            execSQL(
                "INSERT INTO nodes(name, link, networkType, isDefault, isActive) VALUES ('different user label', ?, 0, 0, 0)",
                arrayOf(CUSTOM_URL)
            )
        }
    }

    @Test
    fun actualV2OversizedLegacyNodePreservesArchiveAndHealthyWalletStillOpens() {
        assertHealthyArchiveUpgrade("oversized") {
            execSQL("UPDATE nodes SET name = ? WHERE isDefault = 0", arrayOf("x".repeat((TON_UPGRADE_MAX_CELL_PAYLOAD_BYTES + 1).toInt())))
        }
    }

    @Test
    fun previewRequiresForeignKeysOffBeforeAnyMutation() {
        val fixture = prepared28("foreign-keys")
        withSource(fixture, foreignKeys = true) {
            val before = snapshot()
            val audit = MutationAudit(this)
            transaction {
                val failure = assertThrows(IllegalStateException::class.java) {
                    HistoricalNodeUpgradePreflight.requireSafeBeforeV2SecretWrite(audit)
                }
                assertTrue(failure.message.orEmpty().contains("foreign keys disabled"))
                assertTrue(audit.statements.isEmpty())
                assertEquals(before, snapshot())
            }
        }
    }

    @Test
    fun previewRequiresAnExistingTransactionBeforeAnyMutation() {
        val fixture = prepared28("no-transaction")
        withSource(fixture) {
            val before = snapshot()
            val audit = MutationAudit(this)
            val failure = assertThrows(IllegalStateException::class.java) {
                HistoricalNodeUpgradePreflight.requireSafeBeforeV2SecretWrite(audit)
            }
            assertTrue(failure.message.orEmpty().contains("transaction"))
            assertTrue(audit.statements.isEmpty())
            assertEquals(before, snapshot())
        }
    }

    @Test
    fun directTransferPreservesExistingParentAndMergesExactKeysWithoutLosingSelection() {
        val fixture = version28("merge")
        withSource(fixture, foreignKeys = true) {
            transaction {
                MigrateTablesToV2_29_30.migrate(this)
                execSQL(
                    "INSERT INTO chains(id, parentId, name, icon, prefix, isEthereumBased, isTestNet, hasCrowdloans, " +
                        "url, overridesCommon, staking_url, staking_type, history_url, history_type, crowdloans_url, crowdloans_type) " +
                        "VALUES (?, NULL, 'Preserve exact parent 一', 'existing-icon', 2, 0, 0, 1, 'existing-types', 1, " +
                        "'existing-staking', 'subquery', 'existing-history', 'subquery', 'existing-crowdloan', 'github')",
                    arrayOf(KUSAMA)
                )
                val parent = strings("SELECT * FROM chains")
                execSQL("INSERT INTO chain_nodes(chainId, url, name) VALUES (?, ?, 'Old registry label')", arrayOf(KUSAMA, CUSTOM_URL))
                execSQL("UPDATE nodes SET isActive = 0 WHERE isDefault = 0")
                execSQL("INSERT INTO nodes(name, link, networkType, isDefault, isActive) VALUES (?, ?, 0, 0, 0)", arrayOf(CUSTOM_NAME, CUSTOM_URL))
                execSQL("INSERT INTO nodes(name, link, networkType, isDefault, isActive) VALUES ('Selected catalog label', ?, 0, 1, 1)", arrayOf(CUSTOM_URL))
                execSQL("INSERT INTO nodes(name, link, networkType, isDefault, isActive) VALUES ('Same URL on another chain', ?, 1, 0, 0)", arrayOf(CUSTOM_URL))
                execSQL("INSERT INTO nodes(name, link, networkType, isDefault, isActive) VALUES ('Case-distinct URL', ?, 0, 0, 0)", arrayOf(CUSTOM_URL.replace("Yes", "yes")))
                val archiveBefore = nodeArchiveSnapshot()
                MigrateTablesToV2_30_31.migrate(this)
                assertEquals(parent, strings("SELECT * FROM chains WHERE id = '$KUSAMA'"))
                assertEquals(listOf(listOf(CUSTOM_NAME, "1", "0")), strings("SELECT name, isActive, isDefault FROM chain_nodes WHERE chainId = '$KUSAMA' AND url = '$CUSTOM_URL'"))
                assertEquals(listOf(listOf("Case-distinct URL", "0", "0")), strings("SELECT name, isActive, isDefault FROM chain_nodes WHERE chainId = '$KUSAMA' AND url = '${CUSTOM_URL.replace("Yes", "yes")}'"))
                query("SELECT name, isActive, isDefault FROM chain_nodes WHERE chainId = ? AND url = ?", arrayOf(POLKADOT, CUSTOM_URL)).use {
                    assertTrue(it.moveToFirst()); assertEquals("Same URL on another chain", it.getString(0))
                    assertEquals(0, it.getInt(1)); assertEquals(0, it.getInt(2)); assertFalse(it.moveToNext())
                }
                assertEquals(4L, scalar("SELECT COUNT(*) FROM chain_nodes"))
                assertEquals(archiveBefore, nodeArchiveSnapshot())
                query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
            }
        }
    }

    @Test
    fun directTransferFailureRollsBackItsOwnedTransactionAndCanRetry() {
        val fixture = version28("owned-rollback")
        withSource(fixture) {
            MigrateTablesToV2_29_30.migrate(this)
            val before = snapshot()
            val original = SQLiteException("owned legacy transfer failure")
            var injected = false
            val audit = MutationAudit(this) { sql ->
                if (sql.startsWith("INSERT INTO chain_nodes")) { injected = true; throw original }
            }
            val actual = assertThrows(SQLiteException::class.java) { MigrateTablesToV2_30_31.migrate(audit) }
            assertSame(original, actual)
            assertTrue(injected)
            assertFalse(inTransaction())
            assertEquals(before, snapshot())
            MigrateTablesToV2_30_31.migrate(this)
            assertEquals(2L, scalar("SELECT COUNT(*) FROM chain_nodes"))
            query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        }
    }

    @Test
    fun caseInsensitiveTemporaryNameCollisionRejectsBeforeAnySqlMutation() {
        val fixture = version28("temp-collision")
        withSource(fixture) {
            MigrateTablesToV2_29_30.migrate(this)
            execSQL("CREATE TEMP TABLE _MIGRATION_30_LEGACY_NODES(note TEXT)")
            execSQL("INSERT INTO temp._MIGRATION_30_LEGACY_NODES VALUES ('unowned value')")
            val before = snapshot()
            val audit = MutationAudit(this)
            assertThrows(IllegalStateException::class.java) { MigrateTablesToV2_30_31.migrate(audit) }
            assertTrue(audit.statements.isEmpty())
            assertEquals(before, snapshot())
        }
    }

    @Test
    fun mixedKnownAndUnknownOwnedNodesRemainWholeArchiveWithoutAnAutomaticBinding() {
        assertHealthyArchiveUpgrade("mixed-owned") {
            execSQL(
                "INSERT INTO nodes(name, link, networkType, isDefault, isActive) VALUES ('Unknown owned endpoint', ?, 15, 0, 1)",
                arrayOf("wss://unknown-owned.invalid/rpc")
            )
            assertEquals(1L, scalar("SELECT COUNT(*) FROM nodes WHERE networkType = 0 AND isDefault = 0"))
            assertEquals(1L, scalar("SELECT COUNT(*) FROM nodes WHERE networkType = 15 AND isDefault = 0"))
        }
    }

    @Test
    fun invalidStoredOwnershipFlagsRemainRawWithoutAnAutomaticBinding() {
        assertHealthyArchiveUpgrade("invalid-owned-flags") {
            execSQL("UPDATE nodes SET isActive = 'unknown-selection', isDefault = 2 WHERE networkType = 0")
            assertEquals("text", text("SELECT typeof(isActive) FROM nodes WHERE networkType = 0"))
            assertEquals(2L, scalar("SELECT isDefault FROM nodes WHERE networkType = 0"))
        }
    }

    @Test
    fun zeroEligibleArchiveStillRunsLatePreviewBeforeAnyV2PreferenceWrite() {
        val fixture = version28("zero-eligible-late")
        withSource(fixture) {
            execSQL("UPDATE nodes SET networkType = 15 WHERE isDefault = 0 OR isActive = 1")
            val before = snapshot()
            val preferencesBefore = fixture.preferences.snapshot()
            val tracking = TrackingPreferences(fixture.preferences)
            val original = SQLiteException("owned zero-eligible late preview failure")
            var injected = false
            var prepared: Map<String, List<List<String>>>? = null
            val audit = MutationAudit(this) { sql ->
                if (sql == "SAVEPOINT fearless_upgrade_preview" && prepared == null) prepared = snapshot()
                if (sql.contains("remoteAssetsSource")) { injected = true; throw original }
            }
            beginTransaction()
            try {
                val actual = assertThrows(SQLiteException::class.java) {
                    V2Migration(SecretStoreV1Impl(tracking), tracking).migrate(audit)
                }
                assertSame(original, actual)
                assertTrue(injected)
                assertTrue(actual.suppressed.isEmpty())
                assertTrue(tracking.reads > 0)
                assertEquals(0, tracking.writes)
                assertFalse(audit.statements.any {
                    it.startsWith("CREATE TEMP TABLE `_migration_30_legacy_nodes`") ||
                        it.startsWith("INSERT INTO chain_nodes ") && it.contains("temp.`_migration_30_legacy_nodes`")
                })
                assertEquals(checkNotNull(prepared), snapshot())
                assertEquals(preferencesBefore, fixture.preferences.snapshot())
            } finally { endTransaction() }
            assertEquals(before, snapshot())
        }
    }

    @Test
    fun downstreamStagingBudgetFallsBackWhileTonCachePayloadRemainsEligible() {
        for (limitStaging in listOf(true, false)) {
            val fixture = prepared28("candidate-aggregate-$limitStaging")
            withSource(fixture) {
                transaction {
                    MigrateTablesToV2_29_30.migrate(this)
                    execSQL(
                        "INSERT INTO chains(id, parentId, name, icon, prefix, isEthereumBased, isTestNet, hasCrowdloans, " +
                            "url, overridesCommon, staking_url, staking_type, history_url, history_type, crowdloans_url, crowdloans_type) " +
                            "VALUES (?, NULL, 'Existing parent', ?, 2, 0, 0, 0, NULL, 0, NULL, NULL, NULL, NULL, NULL, NULL)",
                        arrayOf(KUSAMA, "x".repeat(4096))
                    )
                    val archive = nodeArchiveSnapshot()
                    val parent = strings("SELECT * FROM chains")
                    val observations = mutableListOf<Long>()
                    val limits = TonUpgradePayloadLimits.PRODUCTION.copy(maximumTotalBytes = 1024)
                    LegacyNodeMigration.migrate(this) { candidate ->
                        observations += candidate.scalar("SELECT COUNT(*) FROM chain_nodes")
                        Db31UpgradeSqlPreflight.requireSafeBeforePreferenceAccess(candidate)
                        // Retained-parent staging is bounded; SQL-only registry cache bytes are not.
                        HistoricalNodeUpgradePreflight.requireSafeBeforeDb31SecretWrite(
                            candidate,
                            stagingPayloadLimits = if (limitStaging) limits else TonUpgradePayloadLimits.PRODUCTION,
                            tonPayloadLimits = if (limitStaging) TonUpgradePayloadLimits.PRODUCTION else limits
                        )
                    }
                    assertEquals(if (limitStaging) listOf(2L, 0L) else listOf(2L), observations)
                    assertEquals(archive, nodeArchiveSnapshot())
                    if (limitStaging) {
                        assertEquals(parent, strings("SELECT * FROM chains"))
                        assertEquals(0L, scalar("SELECT COUNT(*) FROM chain_nodes"))
                    } else {
                        assertEquals(parent, strings("SELECT * FROM chains WHERE id = '$KUSAMA'"))
                        assertEquals(2L, scalar("SELECT COUNT(*) FROM chains"))
                        assertEquals(
                            listOf(
                                listOf(POLKADOT, DEFAULT_URL, DEFAULT_NAME, "1", "1"),
                                listOf(KUSAMA, CUSTOM_URL, CUSTOM_NAME, "1", "0")
                            ),
                            strings("SELECT chainId, url, name, isActive, isDefault FROM chain_nodes ORDER BY chainId")
                        )
                    }
                    assertEquals(0L, scalar("SELECT COUNT(*) FROM sqlite_temp_master WHERE name LIKE '%migration%' OR name LIKE '%preview%'"))
                    query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
                    assertTrue(inTransaction())
                    execSQL("UPDATE temp.owned_legacy_marker SET note = 'committed after fallback'")
                }
                assertEquals("committed after fallback", text("SELECT note FROM temp.owned_legacy_marker"))
            }
        }
    }

    @Test
    fun exhaustedArchiveFallbackPropagatesBothFailuresAndCallerRollbackRemainsExact() {
        val fixture = prepared28("fallback-also-exhausted")
        withSource(fixture) {
            MigrateTablesToV2_29_30.migrate(this)
            val before = snapshot()
            val observations = mutableListOf<Long>()
            beginTransaction()
            try {
                val failure = assertThrows(MigrationPayloadLimitException::class.java) {
                    LegacyNodeMigration.migrate(this) { candidate ->
                        observations += candidate.scalar("SELECT COUNT(*) FROM chain_nodes")
                        Db31UpgradeSqlPreflight.requireSafeBeforePreferenceAccess(candidate)
                        HistoricalNodeUpgradePreflight.requireSafeBeforeDb31SecretWrite(
                            candidate, tonPayloadLimits = TonUpgradePayloadLimits.PRODUCTION.copy(maximumTotalBytes = 1)
                        )
                    }
                }
                assertEquals(listOf(2L, 0L), observations)
                assertEquals(1, failure.suppressed.size)
                assertTrue(failure.suppressed.single() is MigrationPayloadLimitException)
                assertTrue(failure !== failure.suppressed.single())
                assertEquals(0L, scalar("SELECT COUNT(*) FROM chain_nodes"))
                assertEquals(0L, scalar("SELECT COUNT(*) FROM sqlite_temp_master WHERE name LIKE '%migration%' OR name LIKE '%preview%'"))
                assertTrue(inTransaction())
            } finally { endTransaction() }
            assertEquals(before, snapshot())
        }
    }

    @Test
    fun future83FailurePrecedesActualV2PreferenceWriterAndAllowsRetry() {
        val fixture = version28("v2-future83-boundary")
        val writes = FutureV2PreferenceWriteAudit(fixture.preferences)
        withSource(fixture) {
            val before = futureV2TypedSqlImage()
            val preferencesBefore = fixture.preferences.snapshot()
            val legacySecret = fixture.preferences.getDecryptedString("security_source_$address")
            val expected = SQLiteException("OWNED_V2_FUTURE_83_DECISION_FAILURE")
            var injected = false
            val audit = MutationAudit(this) { sql ->
                if (sql.startsWith("CREATE TABLE `endpoint_recovery_decisions`")) {
                    assertTrue(CurrentEndpointArchive.requireSafe(this))
                    assertTrue(jp.co.soramitsu.coredb.recovery.requireOwnedEndpointSchema(this))
                    assertEquals(28, version)
                    assertTrue(inTransaction())
                    injected = true
                    throw expected
                }
            }
            beginTransaction()
            try {
                val actual = assertThrows(SQLiteException::class.java) {
                    V2Migration(SecretStoreV1Impl(writes), writes).migrate(audit)
                }
                assertSame(expected, actual)
                assertTrue(injected)
                assertTrue(actual.suppressed.isEmpty())
                assertEquals(0, writes.writeAttempts)
                assertEquals(preferencesBefore, fixture.preferences.snapshot())
            } finally { endTransaction() }
            assertFalse(inTransaction())
            assertEquals(before, futureV2TypedSqlImage())
            assertEquals(preferencesBefore, fixture.preferences.snapshot())
            assertEquals(0, writes.writeAttempts)
            assertEquals(28, version)
            assertEquals("original marker", text("SELECT note FROM temp.owned_legacy_marker"))

            // Reuse the same healthy source and preference store with only the injected failure
            // removed. This invokes the actual V2 writer, not the downstream writer at31/71/76.
            transaction { V2Migration(SecretStoreV1Impl(writes), writes).migrate(this) }
            assertTrue("Healthy V2 retry must reach its actual preference writer", writes.writeAttempts > 0)
            assertEquals(1L, scalar("SELECT COUNT(*) FROM meta_accounts"))
            assertEquals(USER_NAME, text("SELECT name FROM meta_accounts"))
            assertEquals(legacySecret, fixture.preferences.getDecryptedString("security_source_$address"))
            assertEquals(SENTINEL_VALUE, fixture.preferences.getDecryptedString(SENTINEL_KEY))
            assertFalse(CurrentEndpointArchive.requireSafe(this))
            assertEquals(0L, scalar("SELECT COUNT(*) FROM main.sqlite_master WHERE name = 'custom_endpoint_records'"))
            assertEquals("original marker", text("SELECT note FROM temp.owned_legacy_marker"))
        }
    }

    @Test
    fun future84FailurePrecedesActualV2PreferenceWriterAndAllowsRetry() {
        val fixture = version28("v2-future84-boundary")
        val writes = FutureV2PreferenceWriteAudit(fixture.preferences)
        withSource(fixture) {
            val before = futureV2TypedSqlImage()
            val preferencesBefore = fixture.preferences.snapshot()
            val legacySecret = fixture.preferences.getDecryptedString("security_source_$address")
            val expected = SQLiteException("OWNED_V2_FUTURE_84_CURRENCY_FAILURE")
            var injected = false
            val audit = MutationAudit(this) { sql ->
                if (sql == FiatCurrencyIdentityMigration.ADD_CURRENCY_ID) {
                    assertTrue(CurrentEndpointArchive.requireSafe(this))
                    assertTrue(jp.co.soramitsu.coredb.recovery.requireOwnedEndpointSchema(this))
                    assertEquals(28, version)
                    assertTrue(inTransaction())
                    injected = true
                    throw expected
                }
            }
            beginTransaction()
            try {
                val actual = assertThrows(SQLiteException::class.java) {
                    V2Migration(SecretStoreV1Impl(writes), writes).migrate(audit)
                }
                assertSame(expected, actual)
                assertTrue(injected)
                assertTrue(actual.suppressed.isEmpty())
                assertEquals(0, writes.writeAttempts)
                assertEquals(preferencesBefore, fixture.preferences.snapshot())
            } finally { endTransaction() }
            assertFalse(inTransaction())
            assertEquals(before, futureV2TypedSqlImage())
            assertEquals(preferencesBefore, fixture.preferences.snapshot())
            assertEquals(0, writes.writeAttempts)
            assertEquals(28, version)
            assertEquals("original marker", text("SELECT note FROM temp.owned_legacy_marker"))

            // Reuse the same healthy source and preference store with only the injected failure
            // removed. This invokes the actual V2 writer, not the downstream writer at31/71/76.
            transaction { V2Migration(SecretStoreV1Impl(writes), writes).migrate(this) }
            assertTrue("Healthy V2 retry must reach its actual preference writer", writes.writeAttempts > 0)
            assertEquals(1L, scalar("SELECT COUNT(*) FROM meta_accounts"))
            assertEquals(USER_NAME, text("SELECT name FROM meta_accounts"))
            assertEquals(legacySecret, fixture.preferences.getDecryptedString("security_source_$address"))
            assertEquals(SENTINEL_VALUE, fixture.preferences.getDecryptedString(SENTINEL_KEY))
            assertFalse(CurrentEndpointArchive.requireSafe(this))
            assertEquals(0L, scalar("SELECT COUNT(*) FROM main.sqlite_master WHERE name = 'custom_endpoint_records'"))
            assertEquals("original marker", text("SELECT note FROM temp.owned_legacy_marker"))
        }
    }

    @Test
    fun future82FailurePrecedesActualV2PreferenceWriterAndAllowsRetry() {
        val fixture = version28("v2-future82-boundary")
        val writes = FutureV2PreferenceWriteAudit(fixture.preferences)
        withSource(fixture) {
            val before = futureV2TypedSqlImage()
            val preferencesBefore = fixture.preferences.snapshot()
            val legacySecret = fixture.preferences.getDecryptedString("security_source_$address")
            val expected = SQLiteException("OWNED_V2_FUTURE_82_COPY_FAILURE")
            var injected = false
            val audit = MutationAudit(this) { sql ->
                if (sql.startsWith("CREATE TABLE `custom_endpoint_records`")) {
                    assertTrue(CurrentEndpointArchive.requireSafe(this))
                    assertEquals(28, version)
                    assertTrue(inTransaction())
                    injected = true
                    throw expected
                }
            }
            beginTransaction()
            try {
                val actual = assertThrows(SQLiteException::class.java) {
                    V2Migration(SecretStoreV1Impl(writes), writes).migrate(audit)
                }
                assertSame(expected, actual)
                assertTrue(injected)
                assertTrue(actual.suppressed.isEmpty())
                assertEquals(0, writes.writeAttempts)
                assertEquals(preferencesBefore, fixture.preferences.snapshot())
            } finally { endTransaction() }
            assertFalse(inTransaction())
            assertEquals(before, futureV2TypedSqlImage())
            assertEquals(preferencesBefore, fixture.preferences.snapshot())
            assertEquals(0, writes.writeAttempts)
            assertEquals(28, version)
            assertEquals("original marker", text("SELECT note FROM temp.owned_legacy_marker"))

            // Reuse the same healthy source and preference store with only the injected failure
            // removed. This invokes the actual V2 writer, not the downstream writer at31/71/76.
            transaction { V2Migration(SecretStoreV1Impl(writes), writes).migrate(this) }
            assertTrue("Healthy V2 retry must reach its actual preference writer", writes.writeAttempts > 0)
            assertEquals(1L, scalar("SELECT COUNT(*) FROM meta_accounts"))
            assertEquals(USER_NAME, text("SELECT name FROM meta_accounts"))
            assertEquals(legacySecret, fixture.preferences.getDecryptedString("security_source_$address"))
            assertEquals(SENTINEL_VALUE, fixture.preferences.getDecryptedString(SENTINEL_KEY))
            assertFalse(CurrentEndpointArchive.requireSafe(this))
            assertEquals(0L, scalar("SELECT COUNT(*) FROM main.sqlite_master WHERE name = 'custom_endpoint_records'"))
            assertEquals("original marker", text("SELECT note FROM temp.owned_legacy_marker"))
        }
    }

    /** Full rowid/schema image with explicit storage classes and exact TEXT/BLOB byte hex. */
    private fun SupportSQLiteDatabase.futureV2TypedSqlImage(): Map<String, List<List<String>>> {
        fun quote(value: String) = "`" + value.replace("`", "``") + "`"
        fun rows(table: String, columns: List<String>): List<List<String>> {
            val projection = columns.joinToString(", ") { name ->
                val column = quote(name)
                "typeof($column), CASE WHEN typeof($column) IN ('text','blob') THEN hex(CAST($column AS BLOB)) ELSE $column END"
            }
            return query("SELECT $projection FROM $table ORDER BY rowid").use { cursor ->
                buildList {
                    while (cursor.moveToNext()) add(buildList {
                        for (index in columns.indices) {
                            val type = cursor.getString(index * 2)
                            val valueIndex = index * 2 + 1
                            add(type)
                            add(when (type) {
                                "null" -> ""
                                "integer" -> cursor.getLong(valueIndex).toString()
                                "real" -> cursor.getDouble(valueIndex).toRawBits().toString()
                                "text", "blob" -> cursor.getString(valueIndex)
                                else -> error("Unexpected SQLite storage class")
                            })
                        }
                    })
                }
            }
        }
        return buildMap {
            put("version", listOf(listOf("integer", scalar("PRAGMA user_version").toString())))
            put("foreignKeys", listOf(listOf("integer", scalar("PRAGMA foreign_keys").toString())))
            for ((schema, master) in listOf("main" to "sqlite_master", "temp" to "sqlite_temp_master")) {
                put("$schema.schema", rows(master, listOf("rowid", "type", "name", "tbl_name", "rootpage", "sql")))
                val tables = query("SELECT name FROM $master WHERE type = 'table' ORDER BY name").use { cursor ->
                    buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
                }
                for (table in tables) {
                    val columns = query("PRAGMA ${quote(schema)}.table_info(${quote(table)})").use { cursor ->
                        val nameIndex = cursor.getColumnIndexOrThrow("name")
                        buildList { while (cursor.moveToNext()) add(cursor.getString(nameIndex)) }
                    }
                    put("$schema.$table", rows("${quote(schema)}.${quote(table)}", listOf("rowid") + columns))
                }
            }
        }
    }

    private class FutureV2PreferenceWriteAudit(private val delegate: EncryptedPreferences) : EncryptedPreferences by delegate {
        var writeAttempts = 0
            private set
        override fun putEncryptedString(field: String, value: String) {
            writeAttempts++; delegate.putEncryptedString(field, value)
        }
        override fun removeKey(field: String) {
            writeAttempts++; delegate.removeKey(field)
        }
        override fun savePinCodeDurably(pin: String) {
            writeAttempts++; delegate.savePinCodeDurably(pin)
        }
        override fun saveRecoveredPinCodeDurably(pin: String, expected: jp.co.soramitsu.common.data.storage.encrypt.PinResetSnapshot) {
            writeAttempts++; delegate.saveRecoveredPinCodeDurably(pin, expected)
        }
        override fun replaceEncryptedStringsDurably(valuesToPut: Map<String, String>, keysToRemove: Set<String>) {
            writeAttempts++; delegate.replaceEncryptedStringsDurably(valuesToPut, keysToRemove)
        }
        override fun replaceEncryptedStringsDurablyIfStatesMatch(
            expectedStates: Map<String, EncryptedPreferenceSnapshot?>,
            valuesToPut: Map<String, String>, keysToRemove: Set<String>, snapshotMoves: List<EncryptedPreferenceSnapshotMove>
        ): Boolean {
            writeAttempts++
            return delegate.replaceEncryptedStringsDurablyIfStatesMatch(expectedStates, valuesToPut, keysToRemove, snapshotMoves)
        }
        override fun quarantineEncryptedStringDurably(
            sourceKey: String, quarantineKey: String, expectedSnapshot: EncryptedPreferenceSnapshot
        ): Boolean {
            writeAttempts++; return delegate.quarantineEncryptedStringDurably(sourceKey, quarantineKey, expectedSnapshot)
        }
    }

    private fun assertHealthyArchiveUpgrade(suffix: String, alter: SupportSQLiteDatabase.() -> Unit) = runBlocking {
        val fixture = version28(suffix)
        var originalArchive: NodeArchiveSnapshot? = null
        withSource(fixture) {
            alter()
            originalArchive = nodeArchiveSnapshot()
        }
        val legacySecret = fixture.preferences.getDecryptedString("security_source_$address")
        var firstPreferences: Map<String, String>? = null
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
                assertEquals("ok", sql.text("PRAGMA integrity_check"))
                sql.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
                assertEquals(checkNotNull(originalArchive), sql.nodeArchiveSnapshot())
                // Whole-archive fallback must not silently bind even the remaining known row.
                assertEquals(0L, sql.scalar("SELECT COUNT(*) FROM chain_nodes"))
                assertEquals(0L, sql.scalar("SELECT COUNT(*) FROM chains"))
                assertEquals(1L, sql.scalar("SELECT COUNT(*) FROM meta_accounts"))
                val id = sql.scalar("SELECT id FROM meta_accounts")
                assertEquals(USER_NAME, sql.text("SELECT name FROM meta_accounts"))
                sql.query("SELECT substratePublicKey, substrateAccountId FROM meta_accounts").use {
                    assertTrue(it.moveToFirst())
                    assertArrayEquals(keypair.publicKey, it.getBlob(0))
                    assertArrayEquals(keypair.publicKey.substrateAccountId(), it.getBlob(1))
                    assertFalse(it.moveToNext())
                }
                val recovered = checkNotNull(SubstrateSecretStore(fixture.preferences).get(
                    id, keypair.publicKey, CryptoType.SR25519, keypair.publicKey.substrateAccountId()
                ))
                assertArrayEquals(keypair.publicKey, recovered[SubstrateSecrets.SubstrateKeypair][KeyPairSchema.PublicKey])
                assertArrayEquals(keypair.privateKey, recovered[SubstrateSecrets.SubstrateKeypair][KeyPairSchema.PrivateKey])
                assertEquals(legacySecret, fixture.preferences.getDecryptedString("security_source_$address"))
                assertEquals(SENTINEL_VALUE, fixture.preferences.getDecryptedString(SENTINEL_KEY))
                assertEquals(
                    listOf(listOf(address, USER_NAME, publicHex, CryptoType.SR25519.ordinal.toString(), "0", "PRESERVED_PENDING_REVIEW")),
                    sql.strings("SELECT address, username, publicKey, cryptoType, position, recoveryState FROM legacy_users_recovery")
                )
                val after = fixture.preferences.snapshot()
                firstPreferences?.let { previous -> assertEquals(previous, after) }
                firstPreferences = after
            } finally { database.close() }
        }
    }

    private data class NodeArchiveSnapshot(
        val schema: List<List<String>>,
        val sequence: List<List<String>>,
        val columns: List<String>,
        val rows: List<Pair<Long, List<CellFingerprint>>>
    )

    private data class CellFingerprint(val storageClass: String, val bytes: Long, val sha256: String)

    /** Each SQLite result is bounded to 16 KiB, including the oversized-name fixture. */
    private fun SupportSQLiteDatabase.nodeArchiveSnapshot(): NodeArchiveSnapshot {
        val schema = strings("SELECT type, name, tbl_name, sql FROM main.sqlite_master WHERE name = 'nodes' COLLATE BINARY")
        assertEquals(1, schema.size)
        val sequence = strings("SELECT rowid, typeof(seq), seq FROM main.sqlite_sequence WHERE name = 'nodes' COLLATE BINARY")
        val columns = query("PRAGMA main.table_info('nodes')").use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name"))) }
        }
        val rowids = query("SELECT rowid FROM main.nodes ORDER BY rowid").use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getLong(0)) }
        }
        val rows = rowids.map { rowid ->
            rowid to columns.map { name ->
                val column = "`" + name.replace("`", "``") + "`"
                val (type, bytes) = query(
                    "SELECT typeof($column), length(CAST($column AS BLOB)) FROM main.nodes WHERE rowid = ?", arrayOf(rowid)
                ).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    cursor.getString(0) to if (cursor.isNull(1)) -1L else cursor.getLong(1)
                }
                val digest = MessageDigest.getInstance("SHA-256")
                var offset = 0L
                while (offset < bytes) {
                    val chunk = query(
                        "SELECT substr(CAST($column AS BLOB), ?, ?) FROM main.nodes WHERE rowid = ?",
                        arrayOf(offset + 1, 16_384, rowid)
                    ).use { cursor -> assertTrue(cursor.moveToFirst()); cursor.getBlob(0) }
                    assertTrue(chunk.isNotEmpty() && chunk.size <= 16_384)
                    digest.update(chunk)
                    offset += chunk.size
                }
                assertEquals(if (bytes < 0) 0L else bytes, offset)
                CellFingerprint(type, bytes, digest.digest().toHexString(withPrefix = false))
            }
        }
        return NodeArchiveSnapshot(schema, sequence, columns, rows)
    }

    private data class Fixture(val name: String, val version: Int, val preferences: HashMapEncryptedPreferences)

    private fun install(release: String, suffix: String): Fixture {
        val schema = ReleasedSchemaFixtures.load(context, release)
        assertEquals(27, schema.databaseVersion)
        val name = "legacy-node-$release-$suffix"
        check(ownedDatabases.add(name))
        ReleasedSchemaFixtures.createDatabase(context, name, schema)
        val preferences = HashMapEncryptedPreferences()
        runBlocking {
            SecretStoreV1Impl(preferences).saveSecuritySource(address, SecuritySource.Unspecified(keypair))
        }
        preferences.putEncryptedString(SENTINEL_KEY, SENTINEL_VALUE)
        return Fixture(name, 27, preferences).also { fixture ->
            withSource(fixture) {
                assertEquals(0L, scalar("SELECT COUNT(*) FROM sqlite_master WHERE name IN ('chains', 'chain_nodes', 'meta_accounts')"))
                transaction {
                    execSQL(
                        "INSERT INTO users(address, username, publicKey, cryptoType, position, networkType) VALUES (?, ?, ?, ?, 0, 0)",
                        arrayOf(address, USER_NAME, publicHex, CryptoType.SR25519.ordinal)
                    )
                    execSQL("INSERT INTO nodes(name, link, networkType, isDefault, isActive) VALUES (?, ?, 0, 0, 1)", arrayOf(CUSTOM_NAME, CUSTOM_URL))
                    execSQL("INSERT INTO nodes(name, link, networkType, isDefault, isActive) VALUES (?, ?, 1, 1, 1)", arrayOf(DEFAULT_NAME, DEFAULT_URL))
                    // An unselected registry-only row needs no invented historical network binding.
                    execSQL("INSERT INTO nodes(name, link, networkType, isDefault, isActive) VALUES ('Old cache', 'wss://unused.invalid', 99, 1, 0)")
                }
            }
        }
    }

    private fun version28(suffix: String): Fixture {
        val fixture = install("1.9.15", suffix)
        withSource(fixture) {
            transaction { AddChainRegistryTables_27_28.migrate(this); version = 28 }
        }
        return fixture.copy(version = 28)
    }

    private fun prepared28(suffix: String): Fixture {
        val fixture = version28(suffix)
        withSource(fixture) {
            execSQL(
                "INSERT INTO meta_accounts(id, substratePublicKey, substrateCryptoType, substrateAccountId, " +
                    "ethereumPublicKey, ethereumAddress, name, isSelected, position) VALUES (1, ?, 'SR25519', ?, NULL, NULL, ?, 1, 0)",
                arrayOf(keypair.publicKey, keypair.publicKey.substrateAccountId(), USER_NAME)
            )
        }
        return fixture
    }

    private fun withSource(fixture: Fixture, foreignKeys: Boolean = false, action: SupportSQLiteDatabase.() -> Unit) {
        val callback = object : SupportSQLiteOpenHelper.Callback(fixture.version) {
            override fun onCreate(db: SupportSQLiteDatabase) = error("Fixture missing")
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = error("Unexpected setup upgrade")
        }
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(fixture.name).callback(callback).build()
        )
        try {
            val database = helper.writableDatabase
            database.setForeignKeyConstraintsEnabled(foreignKeys)
            database.execSQL("CREATE TEMP TABLE owned_legacy_marker(note TEXT)")
            database.execSQL("INSERT INTO temp.owned_legacy_marker VALUES ('original marker')")
            database.action()
        } finally { helper.close() }
    }

    private class MutationAudit(
        private val database: SupportSQLiteDatabase, private val before: (String) -> Unit = {}
    ) : SupportSQLiteDatabase by database {
        val statements = mutableListOf<String>()
        override fun execSQL(sql: String) { statements += sql; before(sql); database.execSQL(sql) }
        override fun execSQL(sql: String, bindArgs: Array<out Any?>) { statements += sql; before(sql); database.execSQL(sql, bindArgs) }
    }

    private class TrackingPreferences(private val delegate: HashMapEncryptedPreferences) : EncryptedPreferences by delegate {
        var reads = 0
        var writes = 0
        override fun requireDurableStorageHealthy() { reads++; delegate.requireDurableStorageHealthy() }
        override fun hasKey(field: String): Boolean { reads++; return delegate.hasKey(field) }
        override fun hasKeyWithPrefix(prefix: String): Boolean { reads++; return delegate.hasKeyWithPrefix(prefix) }
        override fun getDecryptedString(field: String): String? { reads++; return delegate.getDecryptedString(field) }
        override fun getDecryptedStringSnapshot(field: String): EncryptedPreferenceSnapshot? {
            reads++; return delegate.getDecryptedStringSnapshot(field)
        }
        override fun keysWithPrefixes(prefixes: Set<String>, maxResultCount: Int, maxKeyBytes: Int, maxTotalKeyBytes: Int, failOnOversizedMatch: Boolean): Set<String> {
            reads++; return delegate.keysWithPrefixes(prefixes, maxResultCount, maxKeyBytes, maxTotalKeyBytes, failOnOversizedMatch)
        }
        override fun putEncryptedString(field: String, value: String) { writes++; delegate.putEncryptedString(field, value) }
        override fun removeKey(field: String) { writes++; delegate.removeKey(field) }
        override fun replaceEncryptedStringsDurably(valuesToPut: Map<String, String>, keysToRemove: Set<String>) {
            writes++; delegate.replaceEncryptedStringsDurably(valuesToPut, keysToRemove)
        }
        override fun replaceEncryptedStringsDurablyIfStatesMatch(
            expectedStates: Map<String, EncryptedPreferenceSnapshot?>, valuesToPut: Map<String, String>, keysToRemove: Set<String>,
            snapshotMoves: List<EncryptedPreferenceSnapshotMove>
        ): Boolean {
            writes++; return delegate.replaceEncryptedStringsDurablyIfStatesMatch(expectedStates, valuesToPut, keysToRemove, snapshotMoves)
        }
        override fun quarantineEncryptedStringDurably(sourceKey: String, quarantineKey: String, expectedSnapshot: EncryptedPreferenceSnapshot): Boolean {
            writes++; return delegate.quarantineEncryptedStringDurably(sourceKey, quarantineKey, expectedSnapshot)
        }
    }

    private fun SupportSQLiteDatabase.transaction(action: SupportSQLiteDatabase.() -> Unit) {
        beginTransaction()
        try { action(); setTransactionSuccessful() } finally { endTransaction() }
    }

    private fun SupportSQLiteDatabase.snapshot(): Map<String, List<List<String>>> = buildMap {
        put("version", strings("PRAGMA user_version")); put("foreignKeys", strings("PRAGMA foreign_keys"))
        listOf("main" to "sqlite_master", "temp" to "sqlite_temp_master").forEach { (schema, master) ->
            put("$schema.schema", strings("SELECT type, name, tbl_name, rootpage, sql FROM $master ORDER BY rowid"))
            query("SELECT name FROM $master WHERE type = 'table' ORDER BY name").use { cursor ->
                while (cursor.moveToNext()) {
                    val name = cursor.getString(0).replace("`", "``")
                    put("$schema.$name", query("SELECT * FROM `$schema`.`$name`").use { rows ->
                        buildList {
                            while (rows.moveToNext()) add(List(rows.columnCount) { index ->
                                val type = rows.getType(index)
                                val value = when (type) {
                                    Cursor.FIELD_TYPE_NULL -> ""
                                    Cursor.FIELD_TYPE_INTEGER -> rows.getLong(index).toString()
                                    Cursor.FIELD_TYPE_FLOAT -> rows.getDouble(index).toRawBits().toString()
                                    Cursor.FIELD_TYPE_STRING -> rows.getString(index)
                                    Cursor.FIELD_TYPE_BLOB -> rows.getBlob(index).toHexString(withPrefix = false)
                                    else -> error("Unexpected storage class")
                                }
                                "$type:${value.length}:$value"
                            })
                        }.sortedBy { it.joinToString("|") }
                    })
                }
            }
        }
    }

    private fun SupportSQLiteDatabase.strings(sql: String): List<List<String>> = query(sql).use { cursor ->
        buildList { while (cursor.moveToNext()) add(List(cursor.columnCount) { cursor.getString(it) ?: "<NULL>" }) }
    }
    private fun SupportSQLiteDatabase.scalar(sql: String): Long = query(sql).use {
        assertTrue(it.moveToFirst()); val value = it.getLong(0); assertFalse(it.moveToNext()); value
    }
    private fun SupportSQLiteDatabase.text(sql: String): String = query(sql).use {
        assertTrue(it.moveToFirst()); val value = it.getString(0); assertFalse(it.moveToNext()); value
    }
    private fun HashMapEncryptedPreferences.snapshot(): Map<String, String> =
        keysWithPrefixes(setOf(""), 256, 1024, 65536, true).associateWith { checkNotNull(getDecryptedString(it)) }

    private companion object {
        const val KUSAMA = "b0a8d493285c2df73290dfb7e61f870f17b41801197a149ca93654499ea3dafe"
        const val POLKADOT = "91b171bb158e2d3848fa23a9f1c25182fb8e20313b2c1eb49219da7a70ce90c3"
        const val CUSTOM_URL = "wss://owned-legacy.invalid/rpc?exact=Yes"
        const val CUSTOM_NAME = "Owned node 一"
        const val DEFAULT_URL = "wss://selected-legacy.invalid/rpc"
        const val DEFAULT_NAME = "Selected old default"
        const val USER_NAME = "Legacy wallet 一"
        const val SENTINEL_KEY = "legacy-node-unrelated"
        const val SENTINEL_VALUE = "must remain exact"
    }
}
