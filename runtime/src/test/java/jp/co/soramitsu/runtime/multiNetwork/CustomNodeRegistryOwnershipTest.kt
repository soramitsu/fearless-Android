package jp.co.soramitsu.runtime.multiNetwork

import jp.co.soramitsu.coredb.dao.ChainDao
import jp.co.soramitsu.coredb.model.chain.ChainNodeLocal
import jp.co.soramitsu.runtime.multiNetwork.chain.mapChainToChainLocal
import jp.co.soramitsu.runtime.multiNetwork.chain.nativeWalletChains
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** Registry metadata cannot acquire a custom endpoint entered while a fetch was in flight. */
class CustomNodeRegistryOwnershipTest {
    @Test
    fun `same key registry update retains current custom label ownership and selection`() = runBlocking {
        for (active in listOf(false, true)) {
            val fixture = fixture(active)
            fixture.dao.updateNodes(emptyList(), listOf(fixture.remote), emptyList())
            fixture.assertCustomExact()
        }
    }

    @Test
    fun `stale registry insert cannot replace a custom endpoint added during fetch`() = runBlocking {
        for (active in listOf(false, true)) {
            val fixture = fixture(active)
            fixture.dao.updateNodes(listOf(fixture.remote), emptyList(), emptyList())
            fixture.assertCustomExact()
        }
    }

    @Test
    fun `stale registry removal cannot delete a newly owned endpoint at the same key`() = runBlocking {
        for (active in listOf(false, true)) {
            val fixture = fixture(active)
            fixture.dao.updateNodes(emptyList(), emptyList(), listOf(fixture.remote))
            fixture.assertCustomExact()
        }
    }

    @Test
    fun `ordinary registry metadata still refreshes without changing current custom selection`() = runBlocking {
        val fixture = fixture(true)
        val updated = fixture.nodes.single { it.isDefault }.copy(name = "Updated catalog label", isActive = true)
        fixture.dao.updateNodes(emptyList(), listOf(updated), emptyList())
        assertEquals("Updated catalog label", fixture.nodes.single { it.isDefault }.name)
        fixture.assertCustomExact()
    }

    private fun fixture(active: Boolean): Fixture {
        val chain = nativeWalletChains().single { it.id == "solana-mainnet" }
        val info = mapChainToChainLocal(chain)
        val custom = ChainNodeLocal(chain.id, "https://custom.invalid/rpc?token=PUBLIC_ONLY", "Owner label '雪'", active, false)
        val fixture = Fixture(custom, info.nodes.map { it.copy(isActive = !active) } + custom)
        fixture.dao = mockCatalogNodeDao(read = { fixture.nodes }, write = { fixture.nodes = it })
        return fixture
    }

    private class Fixture(val custom: ChainNodeLocal, var nodes: List<ChainNodeLocal>) {
        lateinit var dao: ChainDao
        val remote = custom.copy(name = "Catalog label", isDefault = true, isActive = true)

        fun assertCustomExact() {
            // ChainNodeLocal.equals intentionally omits isActive; assert every column explicitly.
            assertEquals(listOf(listOf(custom.chainId, custom.url, custom.name, custom.isActive, custom.isDefault)),
                nodes.filter { it.chainId == custom.chainId && it.url == custom.url }
                    .map { listOf(it.chainId, it.url, it.name, it.isActive, it.isDefault) })
            assertEquals(1, nodes.count { it.isActive })
            if (custom.isActive) assertEquals(listOf(custom.url), nodes.filter { it.isActive }.map { it.url })
        }
    }
}
