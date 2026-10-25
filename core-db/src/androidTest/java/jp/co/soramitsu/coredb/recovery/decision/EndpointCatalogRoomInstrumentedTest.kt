package jp.co.soramitsu.coredb.recovery.decision

import androidx.room.Room
import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import jp.co.soramitsu.coredb.AppDatabase
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.coredb.model.AssetLocal
import jp.co.soramitsu.coredb.model.ChainAccountLocal
import jp.co.soramitsu.coredb.model.MetaAccountLocal
import jp.co.soramitsu.coredb.model.chain.ChainAssetLocal
import jp.co.soramitsu.coredb.model.chain.ChainExplorerLocal
import jp.co.soramitsu.coredb.model.chain.FavoriteChainLocal
import jp.co.soramitsu.coredb.model.chain.ChainLocal
import jp.co.soramitsu.coredb.model.chain.ChainNodeLocal
import jp.co.soramitsu.coredb.model.chain.JoinedChainInfo
import jp.co.soramitsu.coredb.recovery.selection.EndpointCatalogSnapshot
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.math.BigInteger

/** Actual Room must avoid projecting oversized node text; corruption is injected after ordinary seed. */
@RunWith(AndroidJUnit4::class)
class EndpointCatalogRoomInstrumentedTest {
    private val huge = "wss://public-fixture.invalid/" + "x".repeat(3 * 1024 * 1024)
    private val owned = ChainNodeLocal("a", "wss://owned.invalid/", "Owned", false, false)

    @Test fun onlineCatalogPreservesOversizedSelectedCustomBytes() = runBlocking<Unit> {
        withDatabase { room ->
            val dao = room.chainDao()
            dao.update(emptyList(), listOf(catalog()))
            dao.insertChainNode(owned)
            dao.selectNode("a", owned.url)
            val db = room.openHelper.writableDatabase
            val selected = scalar(db, "SELECT rowid FROM chain_nodes WHERE isActive = 1")
            db.execSQL("UPDATE chain_nodes SET url = ?, name = X'80' WHERE isDefault = 0", arrayOf(huge))
            val replacement = catalog().let { it.copy(chain = it.chain.copy(name = "Updated")) }
            withTimeout(10_000) {
                dao.replaceCatalog(dao.endpointMutationLane.admitCatalogReplacement()) { fresh ->
                    assertTrue(fresh.all { it.nodes.isEmpty() })
                    EndpointCatalogSnapshot(listOf(replacement), emptySet())
                }
            }
            assertEquals(selected, scalar(db, "SELECT rowid FROM chain_nodes WHERE isActive = 1"))
            assertPreserved(db, selected, default = false)
            assertEquals(1L, scalar(db, "SELECT count(*) FROM chains WHERE name = 'Updated'"))
            assertEquals(1L, scalar(db, "SELECT count(*) FROM custom_endpoint_records WHERE revision = 1 AND attached = 1 AND deleted = 0 AND url = 'wss://owned.invalid/' AND name = 'Owned'"))
            assertFalse(db.inTransaction())
        }
    }

    @Test fun offlineCatalogPreservesOversizedCachedDefaultBytes() = runBlocking<Unit> {
        withDatabase { room ->
            val dao = room.chainDao()
            dao.update(emptyList(), listOf(catalog()))
            val db = room.openHelper.writableDatabase
            val selected = scalar(db, "SELECT rowid FROM chain_nodes")
            db.execSQL("UPDATE chain_nodes SET url = ?, name = X'80'", arrayOf(huge))
            withTimeout(10_000) {
                dao.replaceCatalog(dao.endpointMutationLane.admitCatalogReplacement()) { fresh ->
                    assertTrue(fresh.all { it.nodes.isEmpty() })
                    EndpointCatalogSnapshot(fresh, emptySet(), fresh.map { it.chain.id }.toSet())
                }
            }
            assertEquals(1L, scalar(db, "SELECT count(*) FROM chain_nodes"))
            assertPreserved(db, selected, default = true)
            assertFalse(db.inTransaction())
        }
    }

    @Test fun parentRemovalDetachesOwnerWithoutProjectingMalformedNodes() = runBlocking<Unit> {
        withDatabase { room ->
            val dao = room.chainDao()
            dao.update(emptyList(), listOf(catalog()))
            dao.insertChainNode(owned)
            val db = room.openHelper.writableDatabase
            db.execSQL("UPDATE chain_nodes SET url = ?, name = X'80' WHERE isDefault = 0", arrayOf(huge))
            withTimeout(10_000) {
                dao.replaceCatalog(dao.endpointMutationLane.admitCatalogReplacement()) {
                    EndpointCatalogSnapshot(emptyList(), emptySet())
                }
            }
            assertEquals(0L, scalar(db, "SELECT count(*) FROM chains"))
            assertEquals(0L, scalar(db, "SELECT count(*) FROM chain_nodes"))
            assertEquals(1L, scalar(db, "SELECT count(*) FROM custom_endpoint_records WHERE revision = 2 AND attached = 0 AND deleted = 0 AND url = 'wss://owned.invalid/' AND name = 'Owned'"))
            assertFalse(db.inTransaction())
        }
    }

    @Test fun unavailableReferencedParentsPreserveWalletRowsAndReenableOnCompleteCatalog() = runBlocking<Unit> {
        // Each FK must independently retain the parent, not just a wallet having all three.
        for (reference in listOf("account", "favorite", "balance", "all")) {
            withDatabase { room ->
                val dao = room.chainDao()
                val retained = catalogWithChildren("retained")
                val healthy = catalogWithChildren("healthy")
                dao.update(emptyList(), listOf(retained, healthy, catalogWithChildren("unbound")))
                dao.insertChainNode(owned.copy(chainId = "retained"))
                val publicKey = ByteArray(32) { (it + 1).toByte() }
                val meta = MetaAccountLocal(publicKey, CryptoType.SR25519, publicKey, null, null, null,
                    "Public fixture", true, 0, false, null, true)
                val metaId = room.metaAccountDao().insertMetaAccount(meta)
                if (reference == "account" || reference == "all") {
                    room.metaAccountDao().insertWatchChainAccounts(listOf(
                        ChainAccountLocal(metaId, "retained", publicKey, publicKey, CryptoType.SR25519, "Owned account", true)
                    ))
                }
                if (reference == "favorite" || reference == "all") {
                    room.metaAccountDao().insertOrReplaceFavoriteChain(FavoriteChainLocal(metaId, "retained", true))
                }
                if (reference == "balance" || reference == "all") {
                    room.assetDao().insertAssets(listOf(AssetLocal("0", "retained", publicKey, metaId, null,
                        freeInPlanks = BigInteger("123456789"), enabled = false)))
                }
                val db = room.openHelper.writableDatabase
                val userRows = walletRows(db)
                val childRows = retainedCatalogRows(db)

                repeat(2) { round ->
                    val refreshed = healthy.copy(chain = healthy.chain.copy(name = "Healthy refresh $round"))
                    withTimeout(10_000) {
                        dao.replaceCatalog(dao.endpointMutationLane.admitCatalogReplacement()) {
                            EndpointCatalogSnapshot(listOf(refreshed), emptySet())
                        }
                    }
                    assertEquals(userRows, walletRows(db))
                    assertEquals(childRows, retainedCatalogRows(db))
                    assertEquals(1L, scalar(db, "SELECT count(*) FROM chains WHERE id = 'retained' AND ecosystem = 'CatalogUnavailable:Substrate'"))
                    assertEquals(1L, scalar(db, "SELECT count(*) FROM chains WHERE id = 'healthy' AND name = 'Healthy refresh $round'"))
                    assertEquals(0L, scalar(db, "SELECT count(*) FROM chains WHERE id = 'unbound'"))
                    db.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
                }

                val restored = retained.copy(chain = retained.chain.copy(name = "Re-enabled chain"))
                withTimeout(10_000) {
                    dao.replaceCatalog(dao.endpointMutationLane.admitCatalogReplacement()) {
                        EndpointCatalogSnapshot(listOf(restored, healthy), emptySet())
                    }
                }
                assertEquals(userRows, walletRows(db))
                assertEquals(1L, scalar(db, "SELECT count(*) FROM chains WHERE id = 'retained' AND ecosystem = 'Substrate' AND name = 'Re-enabled chain'"))
                assertEquals(1L, scalar(db, "SELECT count(*) FROM chain_nodes WHERE chainId = 'retained' AND isDefault = 0"))
                db.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
                assertFalse(db.inTransaction())
            }
        }
    }

    private fun walletRows(db: SupportSQLiteDatabase) = listOf(
        "meta_accounts", "chain_accounts", "favorite_chains", "assets"
    ).associateWith { rows(db, "SELECT * FROM $it ORDER BY rowid") }

    private fun retainedCatalogRows(db: SupportSQLiteDatabase) = listOf(
        "chain_nodes", "chain_assets", "chain_explorers", "custom_endpoint_records"
    ).associateWith { rows(db, "SELECT * FROM $it WHERE chainId = 'retained' ORDER BY rowid") }

    private fun rows(db: SupportSQLiteDatabase, sql: String): List<List<Any?>> = db.query(sql).use { cursor ->
        buildList {
            while (cursor.moveToNext()) add((0 until cursor.columnCount).map { column ->
                when (cursor.getType(column)) {
                    Cursor.FIELD_TYPE_NULL -> null
                    Cursor.FIELD_TYPE_BLOB -> cursor.getBlob(column).toList()
                    Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(column)
                    Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(column)
                    else -> cursor.getString(column)
                }
            })
        }
    }

    private fun catalogWithChildren(id: String) = catalog().let {
        it.copy(chain = it.chain.copy(id = id), nodes = it.nodes.map { node -> node.copy(chainId = id) },
            assets = listOf(ChainAssetLocal("0", "Retained token", "TOK", id, "", null, "RELAYCHAIN", 12,
                null, true, null, null, null, null, true, null, null)),
            explorers = listOf(ChainExplorerLocal(id, "SUBSCAN", "[]", "https://explorer.invalid/")))
    }

    private fun assertPreserved(
        db: SupportSQLiteDatabase,
        row: Long,
        default: Boolean
    ) {
        db.query("SELECT url = ?, typeof(name) = 'blob' AND hex(name) = '80', isActive, isDefault FROM chain_nodes WHERE rowid = ?", arrayOf(huge, row)).use {
            assertTrue(it.moveToFirst())
            assertEquals(1, it.getInt(0))
            assertEquals(1, it.getInt(1))
            assertEquals(1, it.getInt(2))
            assertEquals(if (default) 1 else 0, it.getInt(3))
            assertFalse(it.moveToNext())
        }
    }

    private suspend fun <T> withDatabase(block: suspend (AppDatabase) -> T): T {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "jp.co.soramitsu.core_db.test")
        val name = "endpoint-catalog-room-${UUID.randomUUID()}.db"
        check(!context.getDatabasePath(name).exists())
        val room = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        return try {
            block(room)
        } finally {
            room.close()
        }
    }

    private fun scalar(db: SupportSQLiteDatabase, sql: String) = db.query(sql).use {
        check(it.moveToFirst())
        it.getLong(0)
    }

    private fun catalog() = JoinedChainInfo(
        ChainLocal(
            "a", null, null, null, "A", null, "", null, 42, false, false, false, false, false,
            false, false, false, null, "Substrate", null, null, null, null
        ),
        listOf(ChainNodeLocal("a", "wss://default.invalid/", "Default", true, true)), emptyList(), emptyList()
    )
}
