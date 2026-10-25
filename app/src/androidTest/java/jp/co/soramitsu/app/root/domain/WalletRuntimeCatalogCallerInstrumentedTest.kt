package jp.co.soramitsu.app.root.domain

import android.database.Cursor
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import jp.co.soramitsu.common.resources.ContextManager
import jp.co.soramitsu.common.resources.LanguagesHolder
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainSyncService
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.mapChainLocalToChain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.ChainFetcher
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.model.ChainRemote
import java.io.IOException
import org.junit.Test
import org.junit.runner.RunWith

/** Actual startup sync and all four repository paths in the wallet process.
 * Synthetic current-schema public fixtures; full closed SQL images must remain exact after offline sync.
 * App-owned native catalog rows are established before the retained before image, through the same service.
 */
@RunWith(AndroidJUnit4::class)
class WalletRuntimeCatalogCallerInstrumentedTest {
    @Test fun offlineStartupPreservesHealthyCatalogAndSavedEndpoints() = runBlocking {
        for (reverse in listOf(false, true)) {
            fixture("caller-healthy", reverse, null, customEndpoints = true) { room, directory, damaged ->
                qualifyCaller(room, directory, damaged, null)
            }
        }
    }

    @Test fun offlineStartupPreservesWideCatalogAndHealthyPeer() = runBlocking {
        for (field in Field.entries) for (reverse in listOf(false, true)) {
            fixture("caller-wide-${field.name}", reverse, field to "x".repeat(PROJECTED_BYTES + 1),
                customEndpoints = true) { room, directory, damaged ->
                qualifyCaller(room, directory, damaged, field)
            }
        }
    }

    private suspend fun offlineSync(room: AppDatabase) {
        val expected = IOException("Synthetic offline registry; no network request")
        var fetches = 0
        val fetcher = object : ChainFetcher {
            override suspend fun getChains(): List<ChainRemote> {
                fetches += 1
                throw expected
            }
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val service = ChainSyncService(room.chainDao(), fetcher, room.metaAccountDao(), room.assetDao(),
            ContextManager.getInstanceOrInit(context, LanguagesHolder()))
        try {
            service.syncUp()
            fail("The original offline fetch failure must reach the caller")
        } catch (failure: IOException) {
            assertSame(expected, failure)
        }
        assertEquals(1, fetches)
        assertTrue(service.getCurrentProcessXcmDiscoveryChains().isEmpty())
    }

    private suspend fun qualifyCaller(room: AppDatabase, directory: File, damaged: String, omitted: Field?) {
        val nativeBefore = listOf("bitcoin-mainnet", "solana-mainnet").map { id ->
            mapChainLocalToChain(checkNotNull(room.chainDao().getJoinChainInfo(id)))
        }
        val expected = (listOf("a", "b").map { id ->
            val base = catalog(id)
            val selected = if (id != damaged) base else base.copy(nodes = listOf(
                base.nodes.single().copy(isActive = false),
                ChainNodeLocal(id, CUSTOM_URL, "Saved custom 雪", true, false)
            ))
            val projected = if (id != damaged) selected else when (omitted) {
                Field.PARENT -> selected.copy(chain = selected.chain.copy(xcm = null))
                Field.PURCHASE -> selected.copy(assets = selected.assets.map { it.copy(purchaseProviders = null) })
                Field.PRICE -> selected.copy(assets = selected.assets.map { it.copy(priceProvider = null) })
                Field.EXPLORER -> selected.copy(explorers = selected.explorers.map { it.copy(types = "[]") })
                null -> selected
            }
            mapChainLocalToChain(projected)
        } + nativeBefore).associateBy { it.id }
        val repository = ChainsRepository(room.chainDao())
        measure(directory, "actual-offline-sync") { offlineSync(room) }
        measure(directory, "repository-list") { assertDomain(expected, repository.getChains()) }
        measure(directory, "repository-map") { assertDomain(expected, repository.getChainsById().values.toList()) }
        measure(directory, "repository-list-flow") { assertDomain(expected, repository.chainsFlow().first()) }
        measure(directory, "repository-map-flow") { assertDomain(expected, repository.chainsByIdFlow().first().values.toList()) }
    }

    private fun assertDomain(expected: Map<String, Chain>, actual: List<Chain>) {
        assertEquals(expected.keys, actual.map { it.id }.toSet())
        assertEquals(expected.size, actual.size)
        for (chain in actual) {
            val original = expected.getValue(chain.id)
            assertEquals(original, chain)
            assertEquals(original.nodes.map { listOf(it.url, it.name, it.isActive, it.isDefault) },
                chain.nodes.map { listOf(it.url, it.name, it.isActive, it.isDefault) })
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
        val directory = File(context.cacheDir, "runtime-catalog-caller-$nonce-${UUID.randomUUID()}")
        check(directory.mkdir())
        val file = File(directory, "app.db")
        val damaged = if (reverse) "b" else "a"
        File(directory, "case.json").writeText(JSONObject().put("label", label).put("reverse", reverse).put("damaged", damaged)
            .put("actualStartupCaller", true).put("nativeCatalogPreseeded", true).put("currentSchemaSynthetic", true).put("api", Build.VERSION.SDK_INT).put("uid", Process.myUid())
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
            // Establish native catalog rows before injecting the tested wide metadata or custom selection.
            offlineSync(seed)
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
            assertEquals(83, sql.version)
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
            assertEquals(if (large) LARGE_ROWS + 3L else 4L, scalar(sql, "SELECT count(*) FROM chain_assets"))
            (afterWide ?: wide)?.let { (field, value) -> assertEquals(1L, scalar(sql,
                "SELECT count(*) FROM ${field.table} WHERE ${field.key}=? AND ${field.column} IS ?", arrayOf(damaged, value))) }
            assertEquals(0, sql.query("PRAGMA foreign_key_check").use { it.count })
            File(directory, "assertions-complete.txt").writeText("actual startup caller preservation assertions complete\n")
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
