package jp.co.soramitsu.liquiditypools.blockexplorer

import jp.co.soramitsu.common.BuildConfig
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraMainChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraTestChainId
import jp.co.soramitsu.xnetworking.lib.datasources.blockexplorer.api.BlockExplorerRepository
import jp.co.soramitsu.xnetworking.lib.datasources.blockexplorer.api.BlockExplorerValue
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BlockExplorerManagerTest {

    private class FakeExplorer(private val values: List<BlockExplorerValue>) : BlockExplorerRepository {
        val requestedChains = mutableListOf<String>()

        override suspend fun getApy(chainId: String): List<BlockExplorerValue> {
            requestedChains += chainId
            return values
        }

        override suspend fun getStakingRewarded(chainId: String, accountAddress: String) = error("not used")

        override suspend fun getValidatorsList(
            chainId: String,
            stashAccountAddress: String,
            historicalRange: List<String>
        ) = error("not used")
    }

    @Test
    fun `strategic bonus yields are read once and returned as percentages`() = runBlocking {
        val explorer = FakeExplorer(
            listOf(
                BlockExplorerValue("pool-a", "0.125"),
                BlockExplorerValue("pool-nan", "NaN"),
                BlockExplorerValue("pool-text", "n/a")
            )
        )
        val manager = BlockExplorerManager(explorer)

        manager.syncSbApy()

        assertEquals(12.5, manager.getApy("pool-a")!!, 1e-9)
        assertNull(manager.getApy("pool-nan"))
        assertNull(manager.getApy("pool-text"))
        assertNull(manager.getApy("unknown"))
        // Debug builds read the SORA test network, release builds mainnet; one request serves every pool.
        assertEquals(listOf(if (BuildConfig.DEBUG) soraTestChainId else soraMainChainId), explorer.requestedChains)
    }
}
