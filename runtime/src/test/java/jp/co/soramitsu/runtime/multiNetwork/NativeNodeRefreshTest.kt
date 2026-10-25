package jp.co.soramitsu.runtime.multiNetwork

import jp.co.soramitsu.core.models.ChainNode
import jp.co.soramitsu.coredb.dao.ChainDao
import jp.co.soramitsu.coredb.model.chain.ChainNodeLocal
import jp.co.soramitsu.runtime.multiNetwork.chain.mapChainToChainLocal
import jp.co.soramitsu.runtime.multiNetwork.chain.nativeWalletChains
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.withSettings

class NativeNodeRefreshTest {
    @Test
    fun `selecting a row deleted before acknowledgement preserves the old active node`() = runBlocking {
        var cleared = false
        val dao = mock(ChainDao::class.java, withSettings().useConstructor().defaultAnswer { invocation ->
            when (invocation.method.name) {
                "selectNode", "selectNodeTransaction" -> invocation.callRealMethod()
                "nodeRowId" -> null
                "clearNodeSelection" -> { cleared = true; Unit }
                else -> error("Unexpected DAO call ${invocation.method.name}")
            }
        })
        val failure = runCatching { dao.selectNode("solana-mainnet", "https://gone.example.invalid") }.exceptionOrNull()
        assertEquals(IllegalStateException::class.java, failure?.javaClass)
        assertEquals("Node is no longer available", failure?.message)
        org.junit.Assert.assertFalse(cleared)
    }

    @Test
    fun `registry write keeps the latest selected custom RPC instead of reactivating its default`() = runBlocking {
        val chain = nativeWalletChains().single { it.id == "solana-mainnet" }
        val info = mapChainToChainLocal(chain)
        val custom = ChainNodeLocal(chain.id, "https://custom.example.invalid", "Custom", true, false)
        var nodes = info.nodes.map { it.copy(isActive = false) } + custom
        val dao = mockCatalogNodeDao(read = { nodes }, write = { nodes = it })
        // This catalogue was prepared before the user's custom-node selection.
        dao.updateNodes(emptyList(), info.nodes.map { it.copy(name = "Updated default") }, emptyList())
        assertEquals(listOf(custom.url), nodes.filter { it.isActive }.map { it.url })
        assertEquals("Updated default", nodes.single { it.isDefault }.name)
        // Repeating the catalogue refresh must also retain the durable choice.
        dao.updateNodes(emptyList(), info.nodes, emptyList())
        assertEquals(listOf(custom.url), nodes.filter { it.isActive }.map { it.url })
    }

    @Test
    fun `custom RPC selection changes equality without erasing historical alias identity`() {
        val chain = nativeWalletChains(setOf("solana:mainnet")).single { it.id == "solana:mainnet" }
        val custom = chain.copy(nodes = chain.nodes + ChainNode("https://custom.example.invalid", "Custom", false, false))
        org.junit.Assert.assertNotEquals(chain, custom)
        assertEquals(chain.id, custom.id)
    }
}
