package jp.co.soramitsu.coredb.recovery.decision

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import jp.co.soramitsu.coredb.AppDatabase
import jp.co.soramitsu.coredb.model.chain.ChainLocal
import jp.co.soramitsu.coredb.model.chain.ChainNodeLocal
import jp.co.soramitsu.coredb.model.chain.JoinedChainInfo
import jp.co.soramitsu.coredb.recovery.selection.EndpointCatalogSnapshot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Generated Room transaction and DAO-lifetime controls in uniquely named core-test databases only. */
@RunWith(AndroidJUnit4::class)
class EndpointWriterRoomInstrumentedTest {
    @Test fun cachedDaoSharesLaneAndCloseRetiresOldAndQueuedOperations() = runBlocking<Unit> {
        val room = database()
        val dao = room.chainDao()
        val lane = dao.endpointMutationLane
        assertSame(dao, room.chainDao())
        assertSame(lane, room.chainDao().endpointMutationLane)
        val held = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val holder = launch(Dispatchers.IO) {
            lane.withOperation(lane.admit(listOf("held"))) {
                held.complete(Unit)
            release.await()
            }
        }
        try {
            withTimeout(5_000) { held.await() }
            val old = lane.admit(listOf("a"))
            val queued = async(Dispatchers.IO) { runCatching { dao.updateChains(old, listOf(chain()), emptyList()) } }
            room.close()
            assertFalse(lane.isLatest(old))
            release.complete(Unit)
            assertTrue(withTimeout(5_000) { queued.await() }.isFailure)
            assertThrows(IllegalStateException::class.java) { lane.admit(listOf("a")) }
            withDatabase { replacement ->
                assertNotSame(lane, replacement.chainDao().endpointMutationLane)
                assertTrue(runCatching { replacement.chainDao().updateChains(old, listOf(chain()), emptyList()) }.isFailure)
            }
        } finally {
            release.complete(Unit)
            holder.join()
            room.close()
        }
    }

    @Test fun generatedAddTransactionRollsBackOwnerWhenProjectionInsertFails() = runBlocking<Unit> {
        withDatabase { room ->
            val dao = room.chainDao()
            dao.updateChains(listOf(chain()), emptyList())
            val db = room.openHelper.writableDatabase
            db.execSQL("CREATE TEMP TRIGGER reject_public_fixture BEFORE INSERT ON main.chain_nodes BEGIN SELECT RAISE(ABORT,'synthetic projection failure'); END")
            assertTrue(
                runCatching {
                dao.insertChainNode(ChainNodeLocal("a", "wss://owned.invalid/", "Owned", false, false))
            }.isFailure
            )
            assertEquals(0L, count(db, "custom_endpoint_records"))
            assertEquals(0L, count(db, "chain_nodes"))
            assertFalse(db.inTransaction())
        }
    }

    @Test fun generatedCatalogTransactionReturnsAcknowledgedFreshParentsWithoutRecursiveLaneAcquisition() =
        runBlocking<Unit> {
        withDatabase { room ->
            val dao = room.chainDao()
            dao.updateChains(listOf(chain()), emptyList())
            val next = chain().copy(name = "Updated")
            val result = withTimeout(5_000) {
                dao.replaceCatalog(dao.endpointMutationLane.admitCatalogReplacement()) { local ->
                    assertEquals(listOf("a"), local.map { it.chain.id })
                    EndpointCatalogSnapshot(listOf(JoinedChainInfo(next, listOf(ChainNodeLocal("a", "wss://default.invalid/", "Default", true, true)), emptyList(), emptyList())), emptySet())
                }
            }
            assertTrue(result.isEmpty())
            assertEquals("Updated", dao.getJoinChainInfo("a")!!.chain.name)
            assertTrue(dao.getNode("a", "wss://default.invalid/").isActive)
            assertFalse(room.openHelper.writableDatabase.inTransaction())
        }
    }

    private suspend fun <T> withDatabase(block: suspend (AppDatabase) -> T): T {
        val room = database()
        return try {
            block(room)
        } finally {
            room.close()
        }
    }

    private fun database(): AppDatabase {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "jp.co.soramitsu.core_db.test")
        val name = "endpoint-writer-room-${UUID.randomUUID()}.db"
        check(!context.getDatabasePath(name).exists())
        return Room.databaseBuilder(context, AppDatabase::class.java, name).build()
    }

    private fun count(db: SupportSQLiteDatabase, table: String): Long = db.query("SELECT COUNT(*) FROM $table").use {
        check(it.moveToFirst())
        it.getLong(0)
    }

    private fun chain() = ChainLocal(
        "a", null, null, null, "A", null, "", null, 42, false, false, false, false, false,
        false, false, false, null, "Substrate", null, null, null, null
    )
}
