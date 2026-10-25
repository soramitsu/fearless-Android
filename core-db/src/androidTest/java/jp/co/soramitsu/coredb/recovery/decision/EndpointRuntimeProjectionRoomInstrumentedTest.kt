package jp.co.soramitsu.coredb.recovery.decision

import android.os.CancellationSignal
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import jp.co.soramitsu.coredb.AppDatabase
import jp.co.soramitsu.coredb.dao.assetOf
import jp.co.soramitsu.coredb.model.AssetLocal
import jp.co.soramitsu.coredb.recovery.EndpointInventorySource
import jp.co.soramitsu.coredb.recovery.EndpointRecordRef
import jp.co.soramitsu.coredb.model.chain.ChainLocal
import jp.co.soramitsu.coredb.model.chain.ChainNodeLocal
import jp.co.soramitsu.coredb.model.chain.JoinedChainInfo
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Qualifies actual Room wrappers and same-count invalidation. Operational corruption follows ordinary seed. */
@RunWith(AndroidJUnit4::class)
class EndpointRuntimeProjectionRoomInstrumentedTest {
    @Test fun oversizedActiveRowCannotPoisonOtherChainOrChooseFallback() = runBlocking<Unit> {
        withDatabase { room ->
            seed(room)
            room.chainDao().update(emptyList(), listOf(catalog("b")))
            val huge = "x".repeat(3 * 1024 * 1024)
            val sql = room.openHelper.writableDatabase
            sql.execSQL("UPDATE chain_nodes SET name=? WHERE chainId='a' AND isActive=1", arrayOf(huge))
            val all = withTimeout(10_000) { room.chainDao().getJoinChainInfo() }.associateBy { it.chain.id }
            assertFalse(all.getValue("a").nodes.any { it.isActive })
            assertEquals("wss://b.invalid/", all.getValue("b").nodes.single { it.isActive }.url)
            sql.query("SELECT name = ?,isActive FROM chain_nodes WHERE chainId='a' AND url='wss://a.invalid/'", arrayOf(huge)).use {
                assertTrue(it.moveToFirst())
                assertEquals(1, it.getInt(0))
                assertEquals(1, it.getInt(1))
                assertFalse(it.moveToNext())
            }
        }
    }

    @Test fun oversizedInactiveAndInvalidUtf8ActiveRemainDistinct() = runBlocking<Unit> {
        withDatabase { room ->
            seed(room)
            val sql = room.openHelper.writableDatabase
            sql.execSQL("UPDATE chain_nodes SET name=? WHERE chainId='a' AND isActive=0", arrayOf("x".repeat(3 * 1024 * 1024)))
            assertEquals("wss://a.invalid/", room.chainDao().getJoinChainInfo("a")!!.nodes.single { it.isActive }.url)
            sql.execSQL("UPDATE chain_nodes SET name=CAST(X'80' AS TEXT) WHERE isActive=1")
            assertFalse(room.chainDao().getJoinChainInfo("a")!!.nodes.any { it.isActive })
            assertEquals(1L, scalar(room, "SELECT count(*) FROM chain_nodes WHERE typeof(name)='text' AND hex(name)='80' AND isActive=1"))
        }
    }

    @Test fun maximumAdmittedTextSurvivesGeneratedBlobChunks() = runBlocking<Unit> {
        withDatabase { room ->
            val first = catalog("a")
            val original = first.nodes.single()
            val name = "x".repeat(1048576 - 128 - original.chainId.length - original.url.length)
            room.chainDao().update(emptyList(), listOf(first.copy(nodes = listOf(original.copy(name = name)))))
            val observed = withTimeout(10_000) { room.chainDao().getJoinChainInfo("a")!! }
            assertEquals(name, observed.nodes.single().name)
            assertEquals(original.url, observed.nodes.single().url)
            assertTrue(observed.nodes.single().isActive)
        }
    }

    @Test fun malformedFlagsMultipleSelectionAndMissingSelectionNeverFabricateActiveRow() = runBlocking<Unit> {
        withDatabase { room ->
            seed(room)
            val sql = room.openHelper.writableDatabase
            sql.execSQL("UPDATE chain_nodes SET isActive=1")
            assertFalse(room.chainDao().getJoinChainInfo("a")!!.nodes.any { it.isActive })
            sql.execSQL("UPDATE chain_nodes SET isActive=0")
            assertFalse(room.chainDao().getJoinChainInfo("a")!!.nodes.any { it.isActive })
            sql.execSQL("UPDATE chain_nodes SET isActive=1 WHERE url='wss://a.invalid/'")
            sql.execSQL("UPDATE chain_nodes SET isDefault='bad' WHERE isActive=0")
            val readable = room.chainDao().getJoinChainInfo("a")!!.nodes
            assertEquals("wss://a.invalid/", readable.single().url)
            assertTrue(readable.single().isActive)
            assertEquals(1L, scalar(room, "SELECT count(*) FROM chain_nodes WHERE typeof(isDefault)='text' AND isDefault='bad'"))
            sql.execSQL("UPDATE chain_nodes SET isDefault='bad' WHERE isActive=1")
            assertFalse(room.chainDao().getJoinChainInfo("a")!!.nodes.any { it.isActive })
            sql.execSQL("UPDATE chain_nodes SET isDefault=1")
            sql.execSQL("UPDATE chain_nodes SET isActive='bad' WHERE isActive=0")
            assertFalse(room.chainDao().getJoinChainInfo("a")!!.nodes.any { it.isActive })
            assertEquals(1L, scalar(room, "SELECT count(*) FROM chain_nodes WHERE typeof(isActive)='text' AND isActive='bad'"))
        }
    }

    @Test fun signedRowidPagesIncludeMinimumAndMultipleFullPages() = runBlocking<Unit> {
        withDatabase { room ->
            room.chainDao().updateChains(listOf(catalog("a").chain), emptyList())
            val sql = room.openHelper.writableDatabase
            sql.beginTransactionNonExclusive()
            try {
                for (i in 0..129) sql.execSQL(
                    "INSERT INTO chain_nodes(rowid,chainId,url,name,isActive,isDefault) VALUES(?,'a',?,'Node',?,1)",
                    arrayOf(if (i == 0)Long.MIN_VALUE else i.toLong() - 65, "wss://a.invalid/$i", if (i == 129)1 else 0)
                )
                sql.setTransactionSuccessful()
            } finally {
                sql.endTransaction()
            }
            val nodes = withTimeout(10_000) { room.chainDao().getJoinChainInfo("a")!!.nodes }
            assertEquals(130, nodes.size)
            assertEquals("wss://a.invalid/0", nodes.first().url)
            assertEquals("wss://a.invalid/129", nodes.single { it.isActive }.url)
        }
    }

    @Test fun actualNodesFlowPublishesSameCountSelectionAndActiveEdit() = runBlocking<Unit> {
        withDatabase { room ->
            coroutineScope {
            seed(room, customSecond = true)
            val events = Channel<List<ChainNodeLocal>>(Channel.UNLIMITED)
            val observer = launch { room.chainDao().nodesFlow("a").collect { events.send(it) } }
            try {
                assertEquals("wss://a.invalid/", withTimeout(10_000) { events.receive() }.single { it.isActive }.url)
                room.chainDao().selectNode("a", "wss://a.invalid/second")
                withTimeout(10_000) { while (events.receive().single { it.isActive }.url != "wss://a.invalid/second") Unit }
                assertEquals(1, room.chainDao().updateNode("a", "wss://a.invalid/second", "Edited", "wss://a.invalid/second"))
                withTimeout(10_000) { while (events.receive().single { it.isActive }.name != "Edited") Unit }
                assertEquals(2L, scalar(room, "SELECT count(*) FROM chain_nodes"))
            } finally {
                observer.cancel()
                events.close()
            }
        }
        }
    }

    @Test fun actualJoinedFlowPublishesNodeOnlyChangesAndParentMetadata() = runBlocking<Unit> {
        withDatabase { room ->
            coroutineScope {
            seed(room)
            val events = Channel<List<JoinedChainInfo>>(Channel.UNLIMITED)
            val observer = launch { room.chainDao().joinChainInfoFlow().collect { events.send(it) } }
            try {
                withTimeout(10_000) { events.receive() }
                room.chainDao().selectNode("a", "wss://a.invalid/second")
                withTimeout(10_000) {
                    while (events.receive().single().nodes.single { it.isActive }.url != "wss://a.invalid/second") Unit
                }
                room.chainDao().updateChains(listOf(catalog("a").chain.copy(name = "Fresh")))
                withTimeout(10_000) { while (events.receive().single().chain.name != "Fresh") Unit }
            } finally {
                observer.cancel()
                events.close()
            }
        }
        }
    }

    @Test fun runtimeReadIsFreshTransactionalAndRetiredByWriterThenClose() = runBlocking<Unit> {
        withDatabase { room ->
            seed(room)
            val dao = room.chainDao()
            val before = dao.readEndpointRuntime("a")
            assertTrue(dao.endpointMutationLane.isCurrent(before.version))
            dao.selectNode("a", "wss://a.invalid/second")
            assertFalse(dao.endpointMutationLane.isCurrent(before.version))
            val after = dao.readEndpointRuntime("a")
            assertEquals("wss://a.invalid/second", after.value!!.nodes.single { it.isActive }.url)
            assertTrue(dao.endpointMutationLane.isCurrent(after.version))
            assertFalse(room.openHelper.writableDatabase.inTransaction())
            room.close()
            assertFalse(dao.endpointMutationLane.isCurrent(after.version))
        }
    }

    @Test fun rawSelectionCommitPublishesToNodesCatalogAndFundedNetworkFlows() = runBlocking<Unit> {
        withDatabase { room ->
            coroutineScope {
                val id = "12".repeat(32)
                val chain = catalog(id)
                val dao = room.chainDao()
                dao.update(emptyList(), listOf(chain.copy(assets = listOf(chain.chain.assetOf("0", "TEST")))))
                room.assetDao().insertAsset(AssetLocal.createEmpty(byteArrayOf(1), "0", id, 7L, null, true))
                val customUrl = "wss://selection.invalid/"
                dao.insertChainNode(ChainNodeLocal(id, customUrl, "Selection", false, false))
                val sql = room.openHelper.writableDatabase
                val ownerId = scalar(room, "SELECT id FROM custom_endpoint_records")
                val signal = CancellationSignal()
                val decisions = EndpointRecoveryDecisionStore(sql)
                val decision = decisions.saveOffline(
                    signal,
                    decisions.prepareSource(signal, EndpointRecordRef(EndpointInventorySource.OWNED, ownerId, 1)),
                    EndpointDecisionCandidate("Selection", customUrl),
                    checkNotNull(decisions.prepareTarget(signal, id)), null, EndpointDecisionAuthority()
                )
                val nodeEvents = Channel<List<ChainNodeLocal>>(Channel.UNLIMITED)
                val catalogEvents = Channel<List<ChainNodeLocal>>(Channel.UNLIMITED)
                val fundedEvents = Channel<List<ChainNodeLocal>>(Channel.UNLIMITED)
                val observers = listOf(
                    launch { dao.nodesFlow(id).collect { nodeEvents.send(it) } },
                    launch { dao.joinChainInfoFlow().collect { catalogEvents.send(it.single().nodes) } },
                    launch { dao.observeChainsWithBalance(7L, "0").collect { fundedEvents.send(it.keys.single().nodes) } }
                )
                try {
                    for (events in listOf(nodeEvents, catalogEvents, fundedEvents)) {
                        assertEquals(chain.nodes.single().url, withTimeout(10_000) { events.receive() }.single { it.isActive }.url)
                    }
                    // Let seed invalidations finish before the raw store writes. The JVM regression
                    // additionally holds the Room wake-up stream completely idle across the same lane event.
                    delay(250)
                    for (events in listOf(nodeEvents, catalogEvents, fundedEvents)) {
                        while (events.tryReceive().isSuccess) Unit
                    }
                    val store = EndpointSelectionStore(sql, dao.endpointMutationLane)
                    val observed = store.observeAttached(signal, decision)
                    val operation = checkNotNull(dao.endpointMutationLane.admitObserved(observed.version))
                    val capture = store.prepare(signal, checkNotNull(observed.value), operation)
                    val authority = EndpointSelectionAuthority(dao.endpointMutationLane)
                    assertEquals(EndpointSelectionResult.Kind.SELECTED, store.commit(signal, capture, authority, authority.newAttempt(capture)).kind)
                    assertFalse(sql.inTransaction())
                    for (events in listOf(nodeEvents, catalogEvents, fundedEvents)) {
                        withTimeout(10_000) {
                            while (events.receive().single { it.isActive }.url != customUrl) Unit
                        }
                    }
                    assertEquals(2L, scalar(room, "SELECT count(*) FROM chain_nodes"))
                    assertEquals(1L, scalar(room, "SELECT count(*) FROM chain_nodes WHERE isActive=1 AND isDefault=0"))
                    assertEquals(1L, scalar(room, "SELECT count(*) FROM custom_endpoint_records WHERE revision=1 AND attached=1 AND deleted=0"))
                } finally {
                    observers.forEach { it.cancel() }
                    listOf(nodeEvents, catalogEvents, fundedEvents).forEach { it.close() }
                }
            }
        }
    }

    @Test fun fundedNetworksUseBoundedNodesAndPreserveHealthyPeer() = runBlocking<Unit> {
        withDatabase { room ->
            val chains = listOf(catalog("a"), catalog("b"))
            room.chainDao().update(emptyList(), chains.map { it.copy(assets = listOf(it.chain.assetOf("0", "TEST"))) })
            room.assetDao().insertAssets(chains.map { AssetLocal.createEmpty(byteArrayOf(1), "0", it.chain.id, 7L, null, true) })
            val huge = "x".repeat(3 * 1024 * 1024)
            room.openHelper.writableDatabase.execSQL("UPDATE chain_nodes SET name=? WHERE chainId='a'", arrayOf(huge))
            val funded = withTimeout(10_000) { room.chainDao().observeChainsWithBalance(7L, "0").first() }
            val byId = funded.keys.associateBy { it.chain.id }
            assertEquals(setOf("a", "b"), byId.keys)
            assertFalse(byId.getValue("a").nodes.any { it.isActive })
            assertEquals("wss://b.invalid/", byId.getValue("b").nodes.single { it.isActive }.url)
            assertEquals(3145728L, scalar(room, "SELECT length(CAST(name AS BLOB)) FROM chain_nodes WHERE chainId='a'"))
            assertEquals(2L, scalar(room, "SELECT count(*) FROM assets WHERE enabled=1"))
        }
    }

    private suspend fun seed(room: AppDatabase, customSecond: Boolean = false) {
        room.chainDao().update(emptyList(), listOf(catalog("a")))
        room.chainDao().insertChainNode(ChainNodeLocal("a", "wss://a.invalid/second", "Second", false, !customSecond))
    }
    private suspend fun <T> withDatabase(block: suspend(AppDatabase) -> T): T {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "jp.co.soramitsu.core_db.test")
        val name = "endpoint-runtime-projection-${UUID.randomUUID()}.db"
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
        ChainLocal(id, null, null, null, id, null, "", null, 42, false, false, false, false, false, false, false, false, null, "Substrate", null, null, null, null),
        listOf(ChainNodeLocal(id, "wss://$id.invalid/", "Default", true, true)), emptyList(), emptyList()
    )
}
