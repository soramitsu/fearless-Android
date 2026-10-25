package jp.co.soramitsu.app.root.domain

import android.database.Cursor
import android.database.sqlite.SQLiteConstraintException
import android.os.Build
import android.os.Process
import android.os.SystemClock
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import jp.co.soramitsu.common.resources.ContextManager
import jp.co.soramitsu.common.resources.LanguagesHolder
import jp.co.soramitsu.coredb.AppDatabase
import jp.co.soramitsu.coredb.model.chain.ChainNodeLocal
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainSyncService
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.mapChainLocalToChain
import jp.co.soramitsu.runtime.multiNetwork.chain.mapChainToChainLocal
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.ChainFetcher
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.model.ChainRemote
import jp.co.soramitsu.runtime.multiNetwork.chain.toChain
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic current-schema public fixtures; actual generated Room DAO and startup catalog service.
 * Stored unknown family bytes are never discarded. The external SQL oracle permits only the named cells.
 */
@RunWith(AndroidJUnit4::class)
class WalletUnknownEcosystemInstrumentedTest {
    @Test
    fun cachedUnknownFamilyCannotPoisonHealthyRuntimePeers() = runBlocking {
        for (bytes in listOf(16, 64, 65, WIDE_BYTES)) {
            for (reverse in listOf(false, true)) fixture("cached", reverse, bytes) { f ->
                assertRuntime(f, admitted = false, healthyName = "Public ${f.healthy}")
            }
        }
    }

    @Test
    fun fetchedUnknownFamilyChangesOnlyRawFamilyWhileHealthyPeerRefreshes() = runBlocking {
        for (bytes in listOf(16, 65, WIDE_BYTES)) {
            for (reverse in listOf(false, true)) fixture("quarantine", reverse, bytes) { f ->
                measure(f, "actual-sync-quarantine") {
                    val service = sync(f.room, ordered(f.reverse, remote(f.damaged, f.raw), remote(f.healthy, name = UPDATED_HEALTHY)))
                    assertEquals(listOf(f.healthy), service.getCurrentProcessXcmDiscoveryChains().map { it.id })
                }
                assertRaw(f, f.raw)
                assertRuntime(f, admitted = false, healthyName = UPDATED_HEALTHY)
            }
        }
    }

    @Test
    fun supportedFetchRestoresPreviouslyQuarantinedFamily() = runBlocking {
        for (bytes in listOf(65, WIDE_BYTES)) {
            for (reverse in listOf(false, true)) fixture("restore", reverse, bytes) { f ->
                assertTrue(ChainsRepository(f.room.chainDao()).getChains().none { it.id == f.damaged })
                measure(f, "actual-sync-restore") {
                    val service = sync(f.room, ordered(f.reverse, remote(f.damaged), remote(f.healthy)))
                    assertEquals(setOf("a", "b"), service.getCurrentProcessXcmDiscoveryChains().map { it.id }.toSet())
                }
                assertRaw(f, "Substrate")
                assertRuntime(f, admitted = true, healthyName = "Public ${f.healthy}")
            }
        }
    }

    @Test
    fun lateExplorerFailureRollsBackQuarantineAndHealthyWritesTogether() = runBlocking {
        for (bytes in listOf(65, WIDE_BYTES)) {
            for (reverse in listOf(false, true)) fixture("late-abort", reverse, bytes) { f ->
                measure(f, "actual-sync-late-abort") {
                    try {
                        sync(
                            f.room,
                            ordered(
                                f.reverse,
                                remote(f.damaged, f.raw),
                                remote(f.healthy, name = UPDATED_HEALTHY, assetName = UPDATED_ASSET)
                            )
                        )
                        fail("The persistent late trigger must reject the write")
                    } catch (failure: SQLiteConstraintException) {
                        assertTrue(failure.message.orEmpty().contains("FW_ECOSYSTEM_LATE_ABORT"))
                        assertTrue(!failure.message.orEmpty().contains("FW_ECOSYSTEM_EARLY_ABORT"))
                    }
                    assertTrue(!f.room.openHelper.writableDatabase.inTransaction())
                }
                assertRaw(f, "Substrate")
                assertRuntime(f, admitted = true, healthyName = "Public ${f.healthy}")
            }
        }
    }

    @Test
    fun duplicateRemoteIdentityNeverChoosesKnownOrUnknownWinner() = runBlocking {
        for (reverse in listOf(false, true)) fixture("duplicate", reverse, 65) { f ->
            measure(f, "actual-sync-duplicate-rejection") {
                try {
                    sync(f.room, ordered(f.reverse, remote(f.damaged), remote(f.damaged, f.raw)))
                    fail("Duplicate identity must fail before partition")
                } catch (failure: IllegalArgumentException) {
                    assertEquals("Remote catalog parent identity is ambiguous", failure.message)
                }
            }
            assertRaw(f, "Substrate")
            assertRuntime(f, admitted = true, healthyName = "Public ${f.healthy}")
        }
    }

    private data class Fixture(
        val room: AppDatabase,
        val directory: File,
        val damaged: String,
        val healthy: String,
        val reverse: Boolean,
        val raw: String
    )

    private fun ordered(reverse: Boolean, first: ChainRemote, second: ChainRemote): List<ChainRemote> =
        listOf(first, second).let { if (reverse) it.reversed() else it }

    private suspend fun sync(room: AppDatabase, rows: List<ChainRemote>?): ChainSyncService {
        var calls = 0
        val offline = IOException("Synthetic no-network registry")
        val fetcher = object : ChainFetcher {
            override suspend fun getChains(): List<ChainRemote> {
                calls += 1
                return rows ?: throw offline
            }
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val service = ChainSyncService(
            room.chainDao(),
            fetcher,
            room.metaAccountDao(),
            room.assetDao(),
            ContextManager.getInstanceOrInit(context, LanguagesHolder())
        )
        if (rows == null) {
            try {
                service.syncUp()
                fail("Expected exact offline failure")
            } catch (failure: IOException) {
                assertSame(offline, failure)
            }
        } else {
            service.syncUp()
        }
        assertEquals(1, calls)
        return service
    }

    private suspend fun assertRuntime(f: Fixture, admitted: Boolean, healthyName: String) {
        val dao = f.room.chainDao()
        val repository = ChainsRepository(dao)
        val expected = linkedMapOf<String, Chain>()
        for (id in listOf("a", "b")) {
            if (id == f.damaged && !admitted) continue
            val value = mapChainToChainLocal(remote(id, name = if (id == f.healthy) healthyName else "Public $id").toChain())
            val stored = if (id == f.damaged) {
                value.copy(
                    nodes = listOf(
                        value.nodes.single().copy(isActive = false),
                        ChainNodeLocal(id, CUSTOM_URL, "Saved custom 雪", true, false)
                    )
                )
            } else {
                value
            }
            expected[id] = mapChainLocalToChain(stored)
        }
        for (id in listOf("bitcoin-mainnet", "solana-mainnet", "bitcoin-testnet")) {
            expected[id] = mapChainLocalToChain(checkNotNull(dao.getJoinChainInfo(id)))
        }
        measure(f, "generated-dao-projection") {
            val projected = checkNotNull(dao.getJoinChainInfo(f.damaged))
            val stored = if (admitted) "Substrate" else f.raw
            assertEquals(if (stored.toByteArray(Charsets.UTF_8).size <= 64) stored else "", projected.chain.ecosystem)
            assertEquals("Public ${f.damaged}", projected.chain.name)
            assertRaw(f, stored)
        }
        measure(f, "repository-list") { assertDomain(expected, repository.getChains()) }
        measure(f, "repository-map") { assertDomain(expected, repository.getChainsById().values.toList()) }
        measure(f, "repository-list-flow") { assertDomain(expected, repository.chainsFlow().first()) }
        measure(f, "repository-map-flow") { assertDomain(expected, repository.chainsByIdFlow().first().values.toList()) }
    }

    private fun assertDomain(expected: Map<String, Chain>, actual: List<Chain>) {
        assertEquals(expected.keys, actual.map { it.id }.toSet())
        assertEquals(expected.size, actual.size)
        val retainedTestnet = actual.single { it.id == "bitcoin-testnet" }
        assertTrue(retainedTestnet.isTestNet)
        assertTrue(retainedTestnet.assets.isEmpty() && retainedTestnet.nodes.isEmpty())
        for (chain in actual) {
            val prior = expected.getValue(chain.id)
            assertEquals(Gson().toJson(prior), Gson().toJson(chain))
            assertEquals(
                prior.nodes.map { listOf(it.url, it.name, it.isActive, it.isDefault) },
                chain.nodes.map { listOf(it.url, it.name, it.isActive, it.isDefault) }
            )
        }
    }

    private fun assertRaw(f: Fixture, value: String) {
        assertEquals(
            1L,
            scalar(
                f.room.openHelper.writableDatabase,
                "SELECT count(*) FROM chains WHERE id=? AND typeof(ecosystem)='text' AND ecosystem=? AND length(CAST(ecosystem AS BLOB))=?",
                arrayOf<Any>(f.damaged, value, value.toByteArray(Charsets.UTF_8).size)
            )
        )
    }

    private suspend fun measure(f: Fixture, label: String, action: suspend () -> Unit) {
        val started = SystemClock.elapsedRealtime()
        val runtime = Runtime.getRuntime()
        File(f.directory, "reads.jsonl").appendText(
            JSONObject().put("path", label).put("outcome", "started-runtime-read")
                .put("elapsedRealtimeStart", started).put("wallTimeStart", System.currentTimeMillis()).put("heapMaximum", runtime.maxMemory()).toString() + "\n"
        )
        withTimeout(120_000) { action() }
        File(f.directory, "reads.jsonl").appendText(
            JSONObject().put("path", label).put("outcome", "complete-runtime-assertions")
                .put("elapsedMillis", SystemClock.elapsedRealtime() - started).put("heapMaximum", runtime.maxMemory()).toString() + "\n"
        )
    }

    private suspend fun fixture(label: String, reverse: Boolean, bytes: Int, action: suspend (Fixture) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val args = InstrumentationRegistry.getArguments()
        check(context.packageName == "jp.co.soramitsu.fearless.debug")
        check(context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_LARGE_HEAP != 0)
        check(Process.myUid() == context.applicationInfo.uid)
        check(Build.VERSION.SDK_INT in setOf(26, 34, 36) && Build.HARDWARE in setOf("ranchu", "goldfish"))
        check(args.getString("fearlessRuntimeCacheOwnedUid") == "exclusively-owned-disposable-app-uid")
        check(args.getString("fearlessRuntimeCacheOwnedSerial")?.matches(Regex("emulator-[0-9]+")) == true)
        val nonce = checkNotNull(args.getString("fearlessRuntimeCacheRunNonce"))
        check(nonce.matches(Regex("[0-9a-f]{32}")))
        val directory = File(context.cacheDir, "runtime-unknown-ecosystem-$nonce-${UUID.randomUUID()}")
        check(directory.mkdir())
        val file = File(directory, "app.db")
        val damaged = if (reverse) "b" else "a"
        val healthy = if (reverse) "a" else "b"
        val raw = "FUTURE_" + "u".repeat(bytes - 7)
        val changes = JSONArray()
        fun change(id: String, column: String, value: String) {
            changes.put(
                JSONObject().put("table", "chains").put("keyColumn", "id").put("key", id)
                    .put("column", column).put("utf8Bytes", value.toByteArray(Charsets.UTF_8).size).put("sha256", textDigest(value))
            )
        }
        if (label == "quarantine") {
            change(damaged, "ecosystem", raw)
            change(healthy, "name", UPDATED_HEALTHY)
        }
        if (label == "restore") change(damaged, "ecosystem", "Substrate")
        File(directory, "case.json").writeText(
            JSONObject().put("label", label).put("reverse", reverse)
                .put("damaged", damaged).put("healthy", healthy).put("api", Build.VERSION.SDK_INT).put("uid", Process.myUid())
                .put("rawBytes", bytes).put("rawSHA256", textDigest(raw)).put("expectedChanges", changes)
                .put("currentSchemaSynthetic", true).put("actualStartupCaller", true).put("productionLargeHeap", true).toString() + "\n"
        )
        fun open() = Room.databaseBuilder(context, AppDatabase::class.java, file.absolutePath)
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE).build()
        val seed = open()
        val protected: String
        try {
            seed.chainDao().update(emptyList(), ordered(reverse, remote("a"), remote("b")).map { mapChainToChainLocal(it.toChain()) })
            val sql = seed.openHelper.writableDatabase
            seedPublicUserData(sql)
            sync(seed, null) // Establish app-owned native definitions before the captured fixture.
            sql.execSQL("UPDATE chain_nodes SET isActive=0 WHERE chainId=?", arrayOf(damaged))
            sql.execSQL(
                "INSERT INTO chain_nodes(chainId,url,name,isActive,isDefault) VALUES(?,?,'Saved custom 雪',1,0)",
                arrayOf(damaged, CUSTOM_URL)
            )
            sql.execSQL("INSERT INTO custom_endpoint_records VALUES(17,3,?,?,'Saved custom 雪',1,0,17)", arrayOf(damaged, CUSTOM_URL))
            sql.execSQL(
                "INSERT INTO custom_endpoint_records VALUES(18,4,?,'wss://public.invalid/deleted','Deleted custom 雪',0,1,18)",
                arrayOf(damaged)
            )
            sql.execSQL(
                "INSERT INTO custom_endpoint_records VALUES(19,2,?,'wss://public.invalid/archived','Archived custom 雪',0,0,19)",
                arrayOf(damaged)
            )
            sql.execSQL(
                "INSERT INTO endpoint_recovery_decisions VALUES(23,2,'OWNED',19,2,?,'Pending custom 雪','wss://public.invalid/archived',?,?,'SUBSTRATE',NULL,'PENDING')",
                arrayOf("1".repeat(64), damaged, "2".repeat(64))
            )
            if (label in setOf("cached", "restore")) sql.execSQL("UPDATE chains SET ecosystem=? WHERE id=?", arrayOf(raw, damaged))
            if (label == "late-abort") {
                sql.execSQL(
                    "CREATE TRIGGER reject_unknown_ecosystem BEFORE UPDATE ON chain_explorers WHEN NEW.chainId='$healthy' BEGIN SELECT CASE WHEN " +
                        "(SELECT name FROM chains WHERE id='$healthy')='$UPDATED_HEALTHY' AND " +
                        "(SELECT name FROM chain_assets WHERE chainId='$healthy' AND id='0')='$UPDATED_ASSET' AND " +
                        "(SELECT length(CAST(ecosystem AS BLOB)) FROM chains WHERE id='$damaged')=$bytes AND " +
                        "(SELECT substr(ecosystem,1,7) FROM chains WHERE id='$damaged')='FUTURE_' " +
                        "THEN RAISE(ABORT,'FW_ECOSYSTEM_LATE_ABORT') ELSE RAISE(ABORT,'FW_ECOSYSTEM_EARLY_ABORT') END; END"
                )
            }
            assertEquals(84, sql.version)
            assertEquals(0, sql.query("PRAGMA foreign_key_check").use { it.count })
            protected = protectedRows(sql)
        } finally {
            seed.close()
        }
        requireClosedSidecars(file)
        file.copyTo(File(directory, "before.db"))
        val reader = open()
        try {
            assertEquals(file.canonicalPath, File(checkNotNull(reader.openHelper.writableDatabase.path)).canonicalPath)
            action(Fixture(reader, directory, damaged, healthy, reverse, raw))
            val sql = reader.openHelper.writableDatabase
            assertEquals(protected, protectedRows(sql))
            assertEquals(4L, scalar(sql, "SELECT count(*) FROM chain_assets"))
            assertEquals(0, sql.query("PRAGMA foreign_key_check").use { it.count })
            File(directory, "assertions-complete.txt").writeText("unknown ecosystem native assertions complete\n")
        } finally {
            reader.close()
            requireClosedSidecars(file)
            file.copyTo(File(directory, "after.db"))
            File(directory, "closed-images.json").writeText(
                JSONObject().put("before", digest(File(directory, "before.db")))
                    .put("after", digest(File(directory, "after.db"))).put("requiresExternalNamedCellEquality", true).toString() + "\n"
            )
        }
    }

    private fun remote(
        id: String,
        ecosystem: String = "Substrate",
        name: String = "Public $id",
        assetName: String = "Public token"
    ): ChainRemote {
        val json = JSONObject().put("chainId", id).put("rank", 1).put("name", name).put("icon", "public-icon")
            .put("addressPrefix", 42).put("ecosystem", ecosystem).put("options", JSONArray())
            .put("nodes", JSONArray().put(JSONObject().put("url", "wss://public.invalid/$id").put("name", "Public endpoint")))
            .put(
                "assets",
                JSONArray().put(
                    JSONObject().put("id", "0").put("name", assetName).put("symbol", "PUB")
                        .put("icon", "public-icon").put("staking", "UNSUPPORTED").put("precision", 12)
                        .put(
                            "purchaseProviders",
                            JSONArray().put("public")
                        ).put("isUtility", true).put("isNative", true).put("existentialDeposit", "1")
                )
            )
            .put(
                "externalApi",
                JSONObject().put(
                    "explorers",
                    JSONArray().put(
                        JSONObject().put("type", "SUBSCAN")
                            .put("types", JSONArray().put("account")).put("url", "https://public.invalid/explorer")
                    )
                )
            )
        return Gson().fromJson(json.toString(), ChainRemote::class.java)
    }

    private fun textDigest(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun seedPublicUserData(db: SupportSQLiteDatabase) {
        db.execSQL(
            "INSERT INTO meta_accounts(id,substratePublicKey,substrateCryptoType,substrateAccountId,name,isSelected,position,isBackedUp,initialized) VALUES(42,X'$PUBLIC_KEY','ED25519',X'$PUBLIC_KEY','Public runtime wallet 雪',1,3,1,1)"
        )
        for (id in listOf("a", "b")) {
            db.execSQL(
                "INSERT INTO chain_accounts(metaId,chainId,publicKey,accountId,cryptoType,name,initialized) VALUES(42,?,X'$PUBLIC_KEY',X'$PUBLIC_KEY','ED25519','Public chain account 雪',1)",
                arrayOf(id)
            )
            db.execSQL(
                "INSERT INTO assets(id,chainId,accountId,metaId,sortIndex,enabled,markedNotNeed,freeInPlanks) VALUES('0',?,X'$PUBLIC_KEY',42,19,1,0,'7')",
                arrayOf(id)
            )
        }
        db.execSQL(
            "INSERT INTO assets(id,chainId,accountId,metaId,sortIndex,enabled,markedNotNeed,freeInPlanks) VALUES('hidden','a',X'$PUBLIC_KEY',42,23,0,0,'11')"
        )
        db.execSQL("INSERT INTO favorite_chains VALUES(42,'a',1)")
        db.execSQL("INSERT INTO address_book(address,name,chainId,created,id) VALUES('public-address','Contact 雪','a',123,7)")
    }

    private fun protectedRows(db: SupportSQLiteDatabase): String = JSONArray().apply {
        for (table in listOf(
            "meta_accounts",
            "chain_accounts",
            "assets",
            "favorite_chains",
            "address_book",
            "chain_nodes",
            "custom_endpoint_records",
            "endpoint_recovery_decisions"
        )) {
            val rows = JSONArray()
            db.query("SELECT * FROM `$table` ORDER BY rowid").use { cursor ->
                while (cursor.moveToNext()) rows.put(
                    JSONArray().apply {
                        for (index in 0 until cursor.columnCount) put(
                            when (cursor.getType(index)) {
                                Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
                                Cursor.FIELD_TYPE_BLOB -> "blob:" + cursor.getBlob(index).joinToString("") { "%02x".format(it.toInt() and 255) }
                                else -> "${cursor.getType(index)}:${cursor.getString(index)}"
                            }
                        )
                    }
                )
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
        file.inputStream().use { stream ->
            val bytes = ByteArray(32768)
            while (true) {
                val n = stream.read(bytes)
                if (n < 0) break
                hash.update(bytes, 0, n)
            }
        }
        return hash.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }
    private fun scalar(db: SupportSQLiteDatabase, query: String, args: Array<out Any?> = emptyArray()): Long = db.query(query, args).use {
        check(it.moveToFirst())
        val result = it.getLong(0)
        check(!it.moveToNext())
        result
    }
    private companion object {
        const val CUSTOM_URL = "wss://public.invalid/custom"
        const val WIDE_BYTES = 3 * 1024 * 1024
        const val UPDATED_HEALTHY = "Updated healthy"
        const val UPDATED_ASSET = "Updated asset"
        const val PUBLIC_KEY = "d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a"
    }
}
