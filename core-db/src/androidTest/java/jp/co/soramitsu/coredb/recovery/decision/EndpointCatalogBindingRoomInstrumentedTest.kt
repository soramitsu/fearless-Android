package jp.co.soramitsu.coredb.recovery.decision

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import jp.co.soramitsu.coredb.AppDatabase
import jp.co.soramitsu.coredb.model.chain.ChainLocal
import jp.co.soramitsu.coredb.model.chain.ChainNodeLocal
import jp.co.soramitsu.coredb.model.chain.JoinedChainInfo
import jp.co.soramitsu.coredb.recovery.selection.EndpointCatalogSnapshot
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** API 26 must qualify large endpoint/parent lists without relying on a newer bind ceiling. */
@RunWith(AndroidJUnit4::class)
class EndpointCatalogBindingRoomInstrumentedTest {
    @Test fun moreThan998IncomingDefaultsPreserveSelectedCustom() = runBlocking<Unit> {
        withDatabase { room ->
            val dao = room.chainDao()
            dao.update(emptyList(), listOf(catalog("a")))
            val owned = ChainNodeLocal("a", "wss://owned.invalid/", "Owned", false, false)
            dao.insertChainNode(owned)
            dao.selectNode("a", owned.url)
            val nodes = (0..1_000).map { ChainNodeLocal("a", "wss://node-$it.invalid/", "Default $it", it == 0, true) }
            withTimeout(30_000) {
                dao.replaceCatalog(dao.endpointMutationLane.admitCatalogReplacement()) {
                    EndpointCatalogSnapshot(listOf(catalog("a").copy(nodes = nodes)), emptySet())
                }
            }
            assertEquals(1_002L, scalar(room, "SELECT count(*) FROM chain_nodes"))
            assertEquals(1L, scalar(room, "SELECT count(*) FROM chain_nodes WHERE url = 'wss://owned.invalid/' AND isActive = 1 AND isDefault = 0"))
            assertEquals(1L, scalar(room, "SELECT revision FROM custom_endpoint_records"))
            assertFalse(room.openHelper.writableDatabase.inTransaction())
        }
    }

    @Test fun moreThan999ParentsUpdateAndDetachAtomically() = runBlocking<Unit> {
        withDatabase { room ->
            val dao = room.chainDao()
            val items = (0..1_000).map { catalog("c$it") }
            withTimeout(30_000) { dao.update(emptyList(), items) }
            assertEquals(1_001L, scalar(room, "SELECT count(*) FROM chain_nodes WHERE isActive = 1"))
            dao.insertChainNode(ChainNodeLocal("c1000", "wss://owned.invalid/", "Owned", false, false))
            withTimeout(30_000) {
                dao.replaceCatalog(dao.endpointMutationLane.admitCatalogReplacement()) {
                    EndpointCatalogSnapshot(emptyList(), emptySet())
                }
            }
            assertEquals(0L, scalar(room, "SELECT count(*) FROM chains"))
            assertEquals(0L, scalar(room, "SELECT count(*) FROM chain_nodes"))
            assertEquals(1L, scalar(room, "SELECT count(*) FROM custom_endpoint_records WHERE attached = 0 AND revision = 2"))
            assertFalse(room.openHelper.writableDatabase.inTransaction())
        }
    }

    @Test fun defaultPageIncludesMinimumSignedRowId() = runBlocking<Unit> {
        withDatabase { room ->
            val dao = room.chainDao()
            dao.update(emptyList(), listOf(catalog("a")))
            room.openHelper.writableDatabase.execSQL("INSERT INTO chain_nodes(rowid,chainId,url,name,isActive,isDefault) VALUES(-9223372036854775808,'a','wss://old.invalid/','Old',0,1)")
            withTimeout(10_000) {
                dao.replaceCatalog(dao.endpointMutationLane.admitCatalogReplacement()) {
                    EndpointCatalogSnapshot(listOf(catalog("a")), emptySet())
                }
            }
            assertEquals(1L, scalar(room, "SELECT count(*) FROM chain_nodes"))
            assertEquals(0L, scalar(room, "SELECT count(*) FROM chain_nodes WHERE rowid = -9223372036854775808"))
        }
    }

    private suspend fun <T> withDatabase(block: suspend (AppDatabase) -> T): T {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "jp.co.soramitsu.core_db.test")
        val name = "endpoint-catalog-bind-room-${UUID.randomUUID()}.db"
        check(!context.getDatabasePath(name).exists())
        val room = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        return try {
            block(room)
        } finally {
            room.close()
        }
    }

    private fun scalar(room: AppDatabase, sql: String) = room.openHelper.writableDatabase.query(sql).use {
        check(it.moveToFirst())

        it.getLong(0)
    }

    private fun catalog(id: String) = JoinedChainInfo(
        ChainLocal(
            id, null, null, null, id, null, "", null, 42, false, false, false, false, false,
            false, false, false, null, "Substrate", null, null, null, null
        ),
        listOf(ChainNodeLocal(id, "wss://default.invalid/", "Default", true, true)), emptyList(), emptyList()
    )
}
