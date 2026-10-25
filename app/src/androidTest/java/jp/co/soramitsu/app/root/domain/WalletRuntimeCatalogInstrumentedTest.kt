package jp.co.soramitsu.app.root.domain

import android.database.Cursor
import android.database.sqlite.SQLiteException
import android.os.Build
import android.os.Process
import android.os.SystemClock
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.MessageDigest
import java.util.BitSet
import java.util.UUID
import jp.co.soramitsu.coredb.AppDatabase
import jp.co.soramitsu.coredb.model.chain.ChainAssetLocal
import jp.co.soramitsu.coredb.model.chain.ChainExplorerLocal
import jp.co.soramitsu.coredb.model.chain.ChainLocal
import jp.co.soramitsu.coredb.model.chain.ChainNodeLocal
import jp.co.soramitsu.coredb.model.chain.JoinedChainInfo
import jp.co.soramitsu.coredb.recovery.selection.EndpointCatalogSnapshot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Runtime catalog reads in the actual wallet process and production heap policy.
 * Wide optional fields retain exact in-bound text and omit only the operational over-bound value.
 * All original stored rows, user choices, healthy peers and full large relation rosters are asserted.
 * Writer controls additionally require exact offline preservation, explicit online clears,
 * pre-DML rejection, late rollback, saved custom endpoint/recovery row preservation,
 * and full large-relation preservation. Only sixteen named cases permit
 * one independently reconstructable catalog-cell mutation; every other stored value stays exact.
 * Synthetic current-schema public fixtures only; no migration, secret, Activity or process-death claim.
 */
@RunWith(AndroidJUnit4::class)
class WalletRuntimeCatalogInstrumentedTest {
    @Test fun healthyCurrentSchemaReadsPreserveBothPeersAndUserChoices() = runBlocking {
        for (reverse in listOf(false, true)) fixture("healthy", reverse, null) { room, _, _ ->
            val expected = listOf(catalog("a"), catalog("b")).associateBy { it.chain.id }
            val dao = room.chainDao()
            assertCatalog(expected, dao.getJoinChainInfo())
            expected.forEach { (id, value) -> assertJoined(value, checkNotNull(dao.getJoinChainInfo(id))) }
            assertCatalog(expected, withTimeout(30_000) { dao.joinChainInfoFlow().first() })
            assertCatalog(expected, withTimeout(30_000) { dao.observeChainsWithBalance(42, "0").first() }.keys.toList())
            assertEquals(expected.values.flatMap { it.assets }.toSet(), dao.getAssetsConfigs().toSet())
        }
    }


    @Test fun wideParentOptionalJsonRemainsReadableWithoutStoredMutation() = characterize(Field.PARENT)
    @Test fun widePurchaseProvidersRemainReadableWithoutStoredMutation() = characterize(Field.PURCHASE)
    @Test fun widePriceProviderRemainsReadableWithoutStoredMutation() = characterize(Field.PRICE)
    @Test fun wideExplorerTypesRemainReadableWithoutStoredMutation() = characterize(Field.EXPLORER)

    @Test fun largeAssetRelationMaterializesEveryRowWithoutSilentTruncation() = runBlocking {
        for (reverse in listOf(false, true)) fixture("large-relation", reverse, null, large = true) { room, directory, damaged ->
            val dao = room.chainDao()
            val expectedCount = LARGE_ROWS + 1
            measure(directory, "assets-all") {
                val rows = withTimeout(120_000) { dao.getAssetsConfigs() }
                assertEquals(expectedCount, rows.size)
                assertAssetRoster(rows, damaged)
                assertTrue(rows.all { it.purchaseProviders == "[\"public\"]" && it.staking == "UNSUPPORTED" })
            }
            measure(directory, "catalog-all") { assertLargeCatalog(dao.getJoinChainInfo(), damaged) }
            measure(directory, "catalog-single") {
                val row = checkNotNull(dao.getJoinChainInfo(damaged))
                assertEquals(LARGE_ROWS, row.assets.size)
                assertAssetRoster(row.assets, damaged, includeHealthy = false)
            }
            measure(directory, "catalog-flow") { assertLargeCatalog(withTimeout(120_000) { dao.joinChainInfoFlow().first() }, damaged) }
            measure(directory, "funded-flow") {
                assertLargeCatalog(withTimeout(120_000) { dao.observeChainsWithBalance(42, "0").first() }.keys.toList(), damaged)
            }
        }
    }

    @Test fun preservedWideCatalogSurvivesIndependentHealthyRefresh() = runBlocking {
        for (field in Field.entries) for (reverse in listOf(false, true)) {
            val value = "x".repeat(PROJECTED_BYTES + 1)
            fixture("writer-preserve-${field.name}", reverse, field to value, mutationKind = "healthy-name") { room, directory, damaged ->
                val dao = room.chainDao()
                val healthy = if (damaged == "a") "b" else "a"
                measure(directory, "writer-preserve") {
                    val added = dao.replaceCatalog(dao.endpointMutationLane.admitCatalogReplacement()) { local ->
                        assertEquals(setOf("a", "b"), local.map { it.chain.id }.toSet())
                        assertTrue(local.all { it.nodes.isEmpty() })
                        val incoming = local.map {
                            if (it.chain.id == damaged) it else it.copy(
                                chain = it.chain.copy(name = "Refreshed public $healthy"), nodes = catalog(healthy).nodes
                            )
                        }
                        EndpointCatalogSnapshot(incoming, emptySet(), preservedCatalogChains = setOf(damaged))
                    }
                    assertTrue(added.isEmpty())
                    assertEquals("Refreshed public $healthy", checkNotNull(dao.getJoinChainInfo(healthy)).chain.name)
                    assertEquals(catalog(damaged).chain.name, checkNotNull(dao.getJoinChainInfo(damaged)).chain.name)
                }
            }
        }
    }

    @Test fun fetchedEmptyOptionalValueReplacesPreviouslyOmittedStoredText() = runBlocking {
        for (field in Field.entries) for (reverse in listOf(false, true)) {
            val value = "x".repeat(PROJECTED_BYTES + 1)
            val afterValue = if (field == Field.EXPLORER) "[]" else null
            fixture("writer-clear-${field.name}", reverse, field to value, mutationKind = "wide-clear",
                afterWide = field to afterValue) { room, directory, damaged ->
                val dao = room.chainDao()
                val expected = listOf(catalog("a"), catalog("b")).map { row ->
                    if (row.chain.id != damaged) row else when (field) {
                        Field.PARENT -> row.copy(chain = row.chain.copy(xcm = null))
                        Field.PURCHASE -> row.copy(assets = row.assets.map { it.copy(purchaseProviders = null) })
                        Field.PRICE -> row.copy(assets = row.assets.map { it.copy(priceProvider = null) })
                        Field.EXPLORER -> row.copy(explorers = row.explorers.map { it.copy(types = "[]") })
                    }
                }
                measure(directory, "writer-clear") {
                    val added = dao.replaceCatalog(dao.endpointMutationLane.admitCatalogReplacement()) {
                        EndpointCatalogSnapshot(expected, emptySet())
                    }
                    assertTrue(added.isEmpty())
                    assertCatalog(expected.associateBy { it.chain.id }, dao.getJoinChainInfo())
                }
            }
        }
    }

    @Test fun absentPreservedCatalogParentRejectsBeforeAnyStoredMutation() = runBlocking {
        for (reverse in listOf(false, true)) fixture("writer-absent-parent", reverse, null) { room, directory, _ ->
            val dao = room.chainDao()
            measure(directory, "writer-rejection") {
                var rejected = false
                try {
                    dao.replaceCatalog(dao.endpointMutationLane.admitCatalogReplacement()) { local ->
                        EndpointCatalogSnapshot(local + catalog("missing").copy(nodes = emptyList()), emptySet(),
                            preservedCatalogChains = setOf("missing"))
                    }
                } catch (failure: IllegalArgumentException) {
                    assertEquals("Catalog preservation requires an existing parent", failure.message)
                    rejected = true
                }
                assertTrue(rejected)
                assertCatalog(listOf(catalog("a"), catalog("b")).associateBy { it.chain.id }, dao.getJoinChainInfo())
            }
        }
    }

    @Test fun preservedLargeCatalogRetainsEveryStoredRelationRow() = runBlocking {
        for (reverse in listOf(false, true)) fixture("writer-large-relation", reverse, null, large = true) { room, directory, damaged ->
            val dao = room.chainDao()
            measure(directory, "writer-large-preserve") {
                val added = dao.replaceCatalog(dao.endpointMutationLane.admitCatalogReplacement()) { local ->
                    assertEquals(setOf("a", "b"), local.map { it.chain.id }.toSet())
                    assertTrue(local.all { it.nodes.isEmpty() })
                    assertAssetRoster(local.single { it.chain.id == damaged }.assets, damaged, includeHealthy = false)
                    EndpointCatalogSnapshot(local, emptySet(), preservedCatalogChains = setOf("a", "b"))
                }
                assertTrue(added.isEmpty())
                assertLargeCatalog(dao.getJoinChainInfo(), damaged)
            }
        }
    }

    @Test fun lateCatalogTriggerFailureRollsBackEarlierParentAndAssetUpdates() = runBlocking {
        for (reverse in listOf(false, true)) fixture("writer-late-rollback", reverse,
            Field.PARENT to "x".repeat(PROJECTED_BYTES + 1), lateAbort = true) { room, directory, damaged ->
            val dao = room.chainDao()
            val healthy = if (damaged == "a") "b" else "a"
            measure(directory, "writer-late-rollback") {
                var rejected = false
                try {
                    dao.replaceCatalog(dao.endpointMutationLane.admitCatalogReplacement()) { local ->
                        val incoming = local.map { row ->
                            if (row.chain.id == damaged) row else row.copy(
                                chain = row.chain.copy(name = "Uncommitted parent"), nodes = catalog(healthy).nodes,
                                assets = row.assets.map { it.copy(name = "Uncommitted asset") },
                                explorers = row.explorers.map { it.copy(url = "https://public.invalid/uncommitted") }
                            )
                        }
                        EndpointCatalogSnapshot(incoming, emptySet(), preservedCatalogChains = setOf(damaged))
                    }
                } catch (failure: SQLiteException) {
                    assertTrue(failure.message.orEmpty().contains("FW_RUNTIME_LATE_ABORT"))
                    assertTrue(!failure.message.orEmpty().contains("FW_RUNTIME_EARLY_ABORT"))
                    rejected = true
                }
                assertTrue(rejected)
                assertJoined(catalog(healthy), checkNotNull(dao.getJoinChainInfo(healthy)))
                assertEquals(false, room.openHelper.writableDatabase.inTransaction())
            }
        }
    }

    @Test fun catalogPreservationRetainsSelectedCustomEndpointAndStoredRecoveryRows() = runBlocking {
        for (reverse in listOf(false, true)) fixture("writer-owned-endpoints", reverse,
            Field.PURCHASE to "x".repeat(PROJECTED_BYTES + 1), customEndpoints = true) { room, directory, damaged ->
            val dao = room.chainDao()
            val healthy = if (damaged == "a") "b" else "a"
            val expectedNodes = catalog(damaged).nodes.map { it.copy(isActive = false) } +
                ChainNodeLocal(damaged, CUSTOM_URL, "Saved custom 雪", true, false)
            assertEquals(nodeImage(expectedNodes), nodeImage(checkNotNull(dao.getJoinChainInfo(damaged)).nodes))
            measure(directory, "writer-owned-preserve") {
                val added = dao.replaceCatalog(dao.endpointMutationLane.admitCatalogReplacement()) { local ->
                    EndpointCatalogSnapshot(local, emptySet(), preservedCatalogChains = setOf("a", "b"))
                }
                assertTrue(added.isEmpty())
                assertEquals(nodeImage(expectedNodes), nodeImage(checkNotNull(dao.getJoinChainInfo(damaged)).nodes))
                assertJoined(catalog(healthy), checkNotNull(dao.getJoinChainInfo(healthy)))
                assertEquals(3L, scalar(room.openHelper.writableDatabase, "SELECT count(*) FROM custom_endpoint_records"))
                assertEquals(1L, scalar(room.openHelper.writableDatabase, "SELECT count(*) FROM endpoint_recovery_decisions"))
            }
        }
    }

    private fun characterize(field: Field) = runBlocking {
        val variants = listOf(
            Triple("one-mib", 1024 * 1024, false),
            Triple("three-mib", 3 * 1024 * 1024, false),
            Triple("malformed", 3 * 1024 * 1024, true),
            Triple("at-bound", PROJECTED_BYTES, false),
            Triple("over-bound", PROJECTED_BYTES + 1, false)
        )
        for ((variant, bytes, malformed) in variants) for (reverse in listOf(false, true)) {
            val (prefix, suffix) = when (field) {
                Field.PARENT -> "{\"public\":\"" to "\"}"
                Field.PRICE -> "{\"id\":\"" to "\",\"type\":\"Chainlink\",\"precision\":8}"
                else -> "[\"" to "\"]"
            }
            val complete = prefix + "x".repeat(bytes - prefix.length - suffix.length) + suffix
            val value = if (malformed) complete.dropLast(1) else complete
            assertEquals(if (malformed) bytes - 1 else bytes, value.toByteArray().size)
            fixture("wide-${field.name}-$variant", reverse, field to value) { room, directory, damaged ->
                val projected = value.takeIf { it.toByteArray().size <= PROJECTED_BYTES }
                val expected = listOf(catalog("a"), catalog("b")).associateBy { it.chain.id }.toMutableMap()
                val before = expected.getValue(damaged)
                expected[damaged] = when (field) {
                    Field.PARENT -> before.copy(chain = before.chain.copy(xcm = projected))
                    Field.PURCHASE -> before.copy(assets = before.assets.map { it.copy(purchaseProviders = projected) })
                    Field.PRICE -> before.copy(assets = before.assets.map { it.copy(priceProvider = projected) })
                    Field.EXPLORER -> before.copy(explorers = before.explorers.map { it.copy(types = projected ?: "[]") })
                }
                val healthy = if (damaged == "a") "b" else "a"
                val dao = room.chainDao()
                assertJoined(catalog(healthy), checkNotNull(dao.getJoinChainInfo(healthy)))
                measure(directory, "catalog-all") { assertCatalog(expected, dao.getJoinChainInfo()) }
                measure(directory, "catalog-single") { assertJoined(expected.getValue(damaged), checkNotNull(dao.getJoinChainInfo(damaged))) }
                measure(directory, "catalog-flow") { assertCatalog(expected, withTimeout(30_000) { dao.joinChainInfoFlow().first() }) }
                measure(directory, "funded-flow") {
                    assertCatalog(expected, withTimeout(30_000) { dao.observeChainsWithBalance(42, "0").first() }.keys.toList())
                }
                measure(directory, "assets-all") { assertEquals(expected.values.flatMap { it.assets }.toSet(), dao.getAssetsConfigs().toSet()) }
                assertJoined(catalog(healthy), checkNotNull(dao.getJoinChainInfo(healthy)))
                assertEquals(false, room.openHelper.writableDatabase.inTransaction())
            }
        }
    }

    private suspend fun measure(directory: File, label: String, action: suspend () -> Unit) {
        val started = SystemClock.elapsedRealtime()
        val runtime = Runtime.getRuntime()
        val before = runtime.totalMemory() - runtime.freeMemory()
        File(directory, "reads.jsonl").appendText(JSONObject().put("path", label).put("outcome", "started-runtime-read")
            .put("elapsedRealtimeStart", started).put("wallTimeStart", System.currentTimeMillis())
            .put("heapUsedBefore", before).put("heapMaximum", runtime.maxMemory()).toString() + "\n")
        withTimeout(120_000) { action() }
        File(directory, "reads.jsonl").appendText(JSONObject().put("path", label).put("outcome", "complete-runtime-assertions")
            .put("elapsedMillis", SystemClock.elapsedRealtime() - started).put("heapUsedBefore", before)
            .put("heapUsedAfter", runtime.totalMemory() - runtime.freeMemory()).put("heapMaximum", runtime.maxMemory()).toString() + "\n")
    }

    private suspend fun fixture(label: String, reverse: Boolean, wide: Pair<Field, String>?, large: Boolean = false,
        mutationKind: String? = null, afterWide: Pair<Field, String?>? = null,
        lateAbort: Boolean = false, customEndpoints: Boolean = false,
        action: suspend (AppDatabase, File, String) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val args = InstrumentationRegistry.getArguments()
        check(context.packageName == "jp.co.soramitsu.fearless.debug")
        check(context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_LARGE_HEAP != 0)
        check(Process.myUid() == context.applicationInfo.uid)
        check(Build.VERSION.SDK_INT in setOf(26, 34, 36) && Build.HARDWARE in setOf("ranchu", "goldfish"))
        check(args.getString("fearlessRuntimeCacheOwnedUid") == "exclusively-owned-disposable-app-uid")
        check(args.getString("fearlessRuntimeCacheOwnedSerial")?.matches(Regex("emulator-[0-9]+")) == true)
        val nonce = checkNotNull(args.getString("fearlessRuntimeCacheRunNonce"))
        check(nonce.matches(Regex("[0-9a-f]{32}")))
        val directory = File(context.cacheDir, "runtime-catalog-read-$nonce-${UUID.randomUUID()}")
        check(directory.mkdir())
        val file = File(directory, "app.db")
        val damaged = if (reverse) "b" else "a"
        File(directory, "case.json").writeText(JSONObject().put("label", label).put("reverse", reverse).put("damaged", damaged)
            .put("currentSchemaSynthetic", true).put("api", Build.VERSION.SDK_INT).put("uid", Process.myUid())
            .put("productionLargeHeap", true).put("heapMaximum", Runtime.getRuntime().maxMemory())
            .put("wideField", wide?.first?.name).put("wideBytes", wide?.second?.toByteArray()?.size ?: 0)
            .put("mutationKind", mutationKind).put("lateAbortFixture", lateAbort).put("customEndpointFixture", customEndpoints)
            .put("boundaryRowIds", label == "healthy").put("largeRows", if (large) LARGE_ROWS else 1).toString() + "\n")
        fun open() = Room.databaseBuilder(context, AppDatabase::class.java, file.absolutePath)
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE).build()
        val seed = open()
        val before: String
        try {
            val ordered = if (reverse) listOf(catalog("b"), catalog("a")) else listOf(catalog("a"), catalog("b"))
            seed.chainDao().update(emptyList(), ordered)
            val sql = seed.openHelper.writableDatabase
            seedPublicUserData(sql)
            if (label == "healthy") {
                // Declared IDs remain unchanged; exercise the entire signed SQLite rowid range.
                // The first page must include MIN_VALUE and the page after MAX_VALUE must terminate.
                for ((table, key) in listOf("chains" to "id", "chain_assets" to "chainId", "chain_explorers" to "chainId")) {
                    sql.execSQL("UPDATE `$table` SET rowid=? WHERE `$key`=?", arrayOf<Any>(Long.MIN_VALUE, damaged))
                    sql.execSQL("UPDATE `$table` SET rowid=? WHERE `$key`<>?", arrayOf<Any>(Long.MAX_VALUE, damaged))
                    assertEquals(Long.MIN_VALUE, scalar(sql, "SELECT MIN(rowid) FROM `$table`"))
                    assertEquals(Long.MAX_VALUE, scalar(sql, "SELECT MAX(rowid) FROM `$table`"))
                }
            }
            if (large) sql.execSQL("WITH RECURSIVE n(i) AS (VALUES(1) UNION ALL SELECT i+1 FROM n WHERE i < ?) " +
                "INSERT INTO chain_assets(id,name,symbol,chainId,icon,priceId,staking,precision,purchaseProviders,isUtility,type,currencyId,existentialDeposit,color,isNative,priceProvider,coinbaseUrl) SELECT CAST(i AS TEXT),name,symbol,chainId,icon,priceId,staking,precision,purchaseProviders,isUtility,type,currencyId,existentialDeposit,color,isNative,priceProvider,coinbaseUrl FROM n,chain_assets WHERE chainId=? AND id='0'",
                arrayOf<Any>(LARGE_ROWS - 1, damaged))
            wide?.let { (field, value) ->
                sql.execSQL("UPDATE ${field.table} SET ${field.column}=? WHERE ${field.key}=?", arrayOf(value, damaged))
                assertEquals(1L, scalar(sql, "SELECT count(*) FROM ${field.table} WHERE ${field.key}=? AND ${field.column}=?", arrayOf(damaged, value)))
            }
            if (lateAbort) {
                val healthy = if (damaged == "a") "b" else "a"
                // Abort only after verifying both earlier writes actually reached this transaction.
                // Keep the persistent trigger in both closed images; the external oracle includes its exact SQL.
                sql.execSQL("CREATE TRIGGER reject_runtime_catalog BEFORE UPDATE ON chain_explorers " +
                    "WHEN NEW.chainId='$healthy' BEGIN SELECT CASE WHEN " +
                    "(SELECT name FROM chains WHERE id='$healthy')='Uncommitted parent' AND " +
                    "(SELECT name FROM chain_assets WHERE chainId='$healthy' AND id='0')='Uncommitted asset' " +
                    "THEN RAISE(ABORT,'FW_RUNTIME_LATE_ABORT') ELSE RAISE(ABORT,'FW_RUNTIME_EARLY_ABORT') END; END")
            }
            if (customEndpoints) {
                sql.execSQL("UPDATE chain_nodes SET isActive=0 WHERE chainId=?", arrayOf(damaged))
                sql.execSQL("INSERT INTO chain_nodes(chainId,url,name,isActive,isDefault) VALUES(?,?,'Saved custom 雪',1,0)", arrayOf(damaged, CUSTOM_URL))
                sql.execSQL("INSERT INTO custom_endpoint_records VALUES(17,3,?,?,'Saved custom 雪',1,0,17)", arrayOf(damaged, CUSTOM_URL))
                sql.execSQL("INSERT INTO custom_endpoint_records VALUES(18,4,?,'wss://public.invalid/deleted','Deleted custom 雪',0,1,18)", arrayOf(damaged))
                sql.execSQL("INSERT INTO custom_endpoint_records VALUES(19,2,?,'wss://public.invalid/archived','Archived custom 雪',0,0,19)", arrayOf(damaged))
                // A stored public pending-intent row is preserved verbatim. This fixture does not
                // claim cryptographic source/target admission or an actual authenticated attachment.
                sql.execSQL("INSERT INTO endpoint_recovery_decisions VALUES(23,2,'OWNED',19,2,?,'Pending custom 雪'," +
                    "'wss://public.invalid/archived',?,?,'SUBSTRATE',NULL,'PENDING')",
                    arrayOf("1".repeat(64), damaged, "2".repeat(64)))
            }
            assertEquals(84, sql.version)
            assertEquals(0, sql.query("PRAGMA foreign_key_check").use { it.count })
            before = protectedRows(sql)
        } finally { seed.close() }
        requireClosedSidecars(file)
        file.copyTo(File(directory, "before.db"))
        val reader = open()
        try {
            assertEquals(file.canonicalPath, File(checkNotNull(reader.openHelper.writableDatabase.path)).canonicalPath)
            action(reader, directory, damaged)
            val sql = reader.openHelper.writableDatabase
            assertEquals(before, protectedRows(sql))
            assertEquals(if (large) LARGE_ROWS + 1L else 2L, scalar(sql, "SELECT count(*) FROM chain_assets"))
            (afterWide ?: wide)?.let { (field, value) -> assertEquals(1L, scalar(sql,
                "SELECT count(*) FROM ${field.table} WHERE ${field.key}=? AND ${field.column} IS ?", arrayOf(damaged, value))) }
            assertEquals(0, sql.query("PRAGMA foreign_key_check").use { it.count })
            File(directory, "assertions-complete.txt").writeText("runtime catalog preservation assertions complete\n")
        } finally {
            reader.close()
            requireClosedSidecars(file)
            file.copyTo(File(directory, "after.db"))
            File(directory, "closed-images.json").writeText(JSONObject().put("before", digest(File(directory, "before.db")))
                .put("after", digest(File(directory, "after.db"))).put("requiresExternalAllRowEquality", mutationKind == null)
                .put("expectedMutation", mutationKind).toString() + "\n")
        }
    }

    private fun seedPublicUserData(db: SupportSQLiteDatabase) {
        db.execSQL("INSERT INTO meta_accounts(id,substratePublicKey,substrateCryptoType,substrateAccountId,name,isSelected,position,isBackedUp,initialized) VALUES(42,X'$PUBLIC_KEY','ED25519',X'$PUBLIC_KEY','Public runtime wallet 雪',1,3,1,1)")
        for (id in listOf("a", "b")) {
            db.execSQL("INSERT INTO chain_accounts(metaId,chainId,publicKey,accountId,cryptoType,name,initialized) VALUES(42,?,X'$PUBLIC_KEY',X'$PUBLIC_KEY','ED25519','Public chain account 雪',1)", arrayOf(id))
            db.execSQL("INSERT INTO assets(id,chainId,accountId,metaId,sortIndex,enabled,markedNotNeed,freeInPlanks) VALUES('0',?,X'$PUBLIC_KEY',42,19,1,0,'7')", arrayOf(id))
        }
        db.execSQL("INSERT INTO assets(id,chainId,accountId,metaId,sortIndex,enabled,markedNotNeed,freeInPlanks) VALUES('hidden','a',X'$PUBLIC_KEY',42,23,0,0,'11')")
        db.execSQL("INSERT INTO favorite_chains VALUES(42,'a',1)")
        db.execSQL("INSERT INTO address_book(address,name,chainId,created,id) VALUES('public-address','Contact 雪','a',123,7)")
    }

    private fun protectedRows(db: SupportSQLiteDatabase): String = JSONArray().apply {
        for (table in listOf("meta_accounts", "chain_accounts", "assets", "favorite_chains", "address_book", "chain_nodes", "custom_endpoint_records", "endpoint_recovery_decisions")) {
            val rows = JSONArray()
            db.query("SELECT * FROM `$table` ORDER BY rowid").use { cursor ->
                while (cursor.moveToNext()) rows.put(JSONArray().apply {
                    for (index in 0 until cursor.columnCount) put(when (cursor.getType(index)) {
                        Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
                        Cursor.FIELD_TYPE_BLOB -> "blob:" + cursor.getBlob(index).joinToString("") { "%02x".format(it.toInt() and 255) }
                        else -> "${cursor.getType(index)}:${cursor.getString(index)}"
                    })
                })
            }
            put(JSONObject().put("table", table).put("rows", rows))
        }
    }.toString()

    private fun requireClosedSidecars(file: File) {
        for (suffix in listOf("-wal", "-journal")) {
            val sidecar = File(file.path + suffix)
            check(!sidecar.exists() || sidecar.isFile && sidecar.length() == 0L)
        }
    }
    private fun digest(file: File): String {
        val hash = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream -> val bytes = ByteArray(32768); while (true) { val n = stream.read(bytes); if (n < 0) break; hash.update(bytes, 0, n) } }
        return hash.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }
    private fun scalar(db: SupportSQLiteDatabase, query: String, args: Array<out Any?> = emptyArray()): Long = db.query(query, args).use {
        check(it.moveToFirst()); val result = it.getLong(0); check(!it.moveToNext()); result
    }
    private fun assertCatalog(expected: Map<String, JoinedChainInfo>, actual: List<JoinedChainInfo>) {
        assertEquals(expected.size, actual.size)
        assertEquals(expected.keys, actual.map { it.chain.id }.toSet())
        actual.forEach { assertJoined(expected.getValue(it.chain.id), it) }
    }
    private fun assertJoined(expected: JoinedChainInfo, actual: JoinedChainInfo) {
        assertEquals(expected, actual)
        // ChainNodeLocal.equals intentionally omits isActive; qualify every node field explicitly.
        assertEquals(nodeImage(expected.nodes), nodeImage(actual.nodes))
    }
    private fun nodeImage(nodes: List<ChainNodeLocal>): List<List<Any>> = nodes.map {
        listOf(it.chainId, it.url, it.name, it.isActive, it.isDefault)
    }
    private fun assertLargeCatalog(rows: List<JoinedChainInfo>, damaged: String) {
        val healthy = if (damaged == "a") "b" else "a"
        assertEquals(2, rows.size)
        assertJoined(catalog(healthy), rows.single { it.chain.id == healthy })
        val large = rows.single { it.chain.id == damaged }
        assertEquals(LARGE_ROWS, large.assets.size)
        assertAssetRoster(large.assets, damaged, includeHealthy = false)
        assertEquals(catalog(damaged).chain, large.chain)
        assertEquals(nodeImage(catalog(damaged).nodes), nodeImage(large.nodes))
        assertEquals(catalog(damaged).explorers, large.explorers)
    }
    private fun assertAssetRoster(rows: List<ChainAssetLocal>, damaged: String, includeHealthy: Boolean = true) {
        val seen = BitSet(LARGE_ROWS)
        var healthy = 0
        for (row in rows) {
            if (row.chainId == damaged) {
                val id = row.id.toInt()
                assertTrue(id in 0 until LARGE_ROWS && row.id == id.toString() && !seen[id])
                seen.set(id)
            } else {
                assertEquals(catalog(if (damaged == "a") "b" else "a").assets.single(), row)
                healthy++
            }
        }
        assertEquals(LARGE_ROWS, seen.cardinality())
        assertEquals(if (includeHealthy) 1 else 0, healthy)
    }
    private fun catalog(id: String) = JoinedChainInfo(
        ChainLocal(id, null, null, 1, "Public $id", null, "public-icon", null, 42, false, false, false, false, false, false, false, false, null, "Substrate", null, null, null, null),
        listOf(ChainNodeLocal(id, "wss://public.invalid/$id", "Public endpoint", true, true)),
        listOf(ChainAssetLocal("0", "Public token", "PUB", id, "public-icon", null, "UNSUPPORTED", 12, "[\"public\"]", true, null, null, "1", null, true, null, null)),
        listOf(ChainExplorerLocal(id, "SUBSCAN", "[\"account\"]", "https://public.invalid/explorer"))
    )
    private enum class Field(val table: String, val column: String, val key: String) {
        PARENT("chains", "xcm", "id"), PURCHASE("chain_assets", "purchaseProviders", "chainId"),
        PRICE("chain_assets", "priceProvider", "chainId"), EXPLORER("chain_explorers", "types", "chainId")
    }
    private companion object {
        const val CUSTOM_URL = "wss://public.invalid/custom"
        const val PROJECTED_BYTES = 4 * 1024 * 1024
        const val LARGE_ROWS = 262_145
        const val PUBLIC_KEY = "d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a"
    }
}
