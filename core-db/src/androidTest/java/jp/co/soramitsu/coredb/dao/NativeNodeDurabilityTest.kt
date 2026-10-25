package jp.co.soramitsu.coredb.dao

import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.co.soramitsu.coredb.AppDatabase
import jp.co.soramitsu.coredb.model.chain.ChainNodeLocal
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Runs the generated Room DAO and SQLite transactions, rather than a mock SQL implementation. */
@RunWith(AndroidJUnit4::class)
class NativeNodeDurabilityTest : DaoTest<ChainDao>(AppDatabase::chainDao) {
    private val chainId = "solana-mainnet"
    private val default = ChainNodeLocal(chainId, "https://default.invalid", "Default", true, true)
    private val custom = ChainNodeLocal(chainId, "https://custom.invalid", "Custom", false, false)

    @Test fun delayedRegistryRefreshPreservesTheCurrentManualSelection() = runBlocking {
        seed()
        dao.selectNode(chainId, custom.url)
        dao.updateNodes(emptyList(), listOf(default.copy(name = "Refreshed", isActive = true)), emptyList())
        assertEquals(listOf(custom.url), dao.getJoinChainInfo(chainId)!!.nodes.filter { it.isActive }.map { it.url })
        assertEquals("Refreshed", dao.getNode(chainId, default.url).name)
    }

    @Test fun registryRefreshRepairsDuplicateActiveRowsWithoutDiscardingTheCustomNode() = runBlocking {
        seed()
        db.openHelper.writableDatabase.execSQL("UPDATE chain_nodes SET isActive=1 WHERE chainId=?", arrayOf(chainId))
        dao.updateNodes(emptyList(), listOf(default.copy(name = "Refreshed")), emptyList())
        val rows = dao.getJoinChainInfo(chainId)!!.nodes
        assertEquals(2, rows.size)
        assertEquals(listOf(custom.url), rows.filter { it.isActive }.map { it.url })
    }

    @Test fun editingCannotReplaceAnActiveEndpointOrMutateAMissingOrDefaultRow() = runBlocking {
        seed()
        dao.selectNode(chainId, custom.url)
        assertEquals(0, dao.updateNode(chainId, custom.url, "Replacement", "https://replacement.invalid"))
        assertEquals(custom.url, dao.getJoinChainInfo(chainId)!!.nodes.single { it.isActive }.url)
        assertEquals(1, dao.updateNode(chainId, custom.url, "Renamed", custom.url))
        assertEquals("Renamed", dao.getNode(chainId, custom.url).name)
        assertEquals(0, dao.updateNode(chainId, default.url, "Changed", default.url))
        assertEquals(0, dao.updateNode(chainId, "https://missing.invalid", "Missing", "https://new.invalid"))
        assertEquals(2, dao.getJoinChainInfo(chainId)!!.nodes.size)
    }

    @Test fun inactiveCustomEndpointCanBeEditedAndMissingSelectionDoesNotClearTheCurrentOne() = runBlocking {
        seed()
        val newUrl = "https://replacement.invalid"
        assertEquals(1, dao.updateNode(chainId, custom.url, "Replacement", newUrl))
        assertEquals(newUrl, dao.getNode(chainId, newUrl).url)
        assertTrue(runCatching { dao.selectNode(chainId, "https://missing.invalid") }.isFailure)
        assertEquals(listOf(default.url), dao.getJoinChainInfo(chainId)!!.nodes.filter { it.isActive }.map { it.url })
    }

    private suspend fun seed() {
        dao.addChain(createTestChain(chainId).copy(nodes = listOf(default, custom)))
    }
}
