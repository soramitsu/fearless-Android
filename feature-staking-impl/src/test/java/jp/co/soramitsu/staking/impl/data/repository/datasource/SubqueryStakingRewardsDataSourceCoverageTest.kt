package jp.co.soramitsu.staking.impl.data.repository.datasource

import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import java.math.BigInteger
import jp.co.soramitsu.common.base.errors.RewardsNotSupportedWarning
import jp.co.soramitsu.common.data.network.subquery.GiantsquidRewardAmountResponse
import jp.co.soramitsu.common.data.network.subquery.ReefAddress
import jp.co.soramitsu.common.data.network.subquery.ReefRewardsConnection
import jp.co.soramitsu.common.data.network.subquery.ReefRewardsEdge
import jp.co.soramitsu.common.data.network.subquery.ReefRewardsNode
import jp.co.soramitsu.common.data.network.subquery.ReefStakingRewardsResponse
import jp.co.soramitsu.common.data.network.subquery.SubQueryNodes
import jp.co.soramitsu.common.data.network.subquery.SubQueryResponse
import jp.co.soramitsu.common.data.network.subquery.SubsquidEthRewardAmountResponse
import jp.co.soramitsu.common.data.network.subquery.SubsquidPageInfo
import jp.co.soramitsu.common.data.network.subquery.SubsquidRelayRewardAmountResponse
import jp.co.soramitsu.common.data.network.subquery.SubsquidResponse
import jp.co.soramitsu.common.data.network.subquery.TransactionHistoryRemote
import jp.co.soramitsu.coredb.dao.StakingTotalRewardDao
import jp.co.soramitsu.coredb.model.TotalRewardLocal
import jp.co.soramitsu.runtime.ext.addressOf
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraMainChainId
import jp.co.soramitsu.staking.impl.StakingLaneFixtures
import jp.co.soramitsu.staking.impl.data.network.subquery.StakingApi
import jp.co.soramitsu.staking.impl.data.network.subquery.request.GiantsquidRewardAmountRequest
import jp.co.soramitsu.staking.impl.data.network.subquery.request.SubsquidRelayRewardAmountRequest
import jp.co.soramitsu.xnetworking.lib.datasources.blockexplorer.api.BlockExplorerRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Total staking rewards are fetched from each indexer kind and stored per address. */
class SubqueryStakingRewardsDataSourceCoverageTest {

    private val api = mockk<StakingApi>()
    private val dao = mockk<StakingTotalRewardDao>()
    private val registry = mockk<ChainRegistry>()
    private val explorer = mockk<BlockExplorerRepository>()
    private val source = SubqueryStakingRewardsDataSource(api, dao, registry, explorer)
    private val inserted = mutableListOf<TotalRewardLocal>()

    init {
        coEvery { dao.insert(capture(inserted)) } just Runs
    }

    private fun chain(type: Chain.ExternalApi.Section.Type?, chainId: String = "rewards-chain", ethereum: Boolean = false): Chain {
        val external = type?.let { Chain.ExternalApi(Chain.ExternalApi.Section(it, URL), null, null) }
        val chain = StakingLaneFixtures.chain(StakingLaneFixtures.chainAsset(chainId = chainId), externalApi = external, ethereum = ethereum)
        coEvery { registry.getChain(chainId) } returns chain
        return chain
    }

    private suspend fun syncedTotal(chain: Chain, address: String = ADDRESS): BigInteger {
        source.sync(chain.id, address)
        return inserted.last().totalReward
    }

    @Test
    fun `subquery rewards are summed`() = runTest {
        val history = TransactionHistoryRemote(
            SubQueryNodes(listOf(BigInteger.valueOf(5), BigInteger.valueOf(7)).map { TransactionHistoryRemote.HistoryElement(TransactionHistoryRemote.HistoryElement.Reward(it)) })
        )
        coEvery { api.getSumReward(URL, any()) } returns SubQueryResponse(history)

        assertEquals(BigInteger.valueOf(12), syncedTotal(chain(Chain.ExternalApi.Section.Type.SUBQUERY)))
        assertEquals(ADDRESS, inserted.single().accountAddress)
    }

    @Test
    fun `subsquid rewards are summed for ethereum and relay chains`() = runTest {
        val eth = SubsquidEthRewardAmountResponse(listOf(SubsquidEthRewardAmountResponse.Reward(BigInteger.valueOf(3)), SubsquidEthRewardAmountResponse.Reward(null)))
        val relay = SubsquidRelayRewardAmountResponse(
            listOf(
                SubsquidRelayRewardAmountResponse.HistoryElement(SubsquidRelayRewardAmountResponse.Reward(BigInteger.valueOf(4))),
                SubsquidRelayRewardAmountResponse.HistoryElement(null)
            )
        )
        coEvery { api.getEthRewardAmounts(URL, any()) } returns SubsquidResponse(eth)
        coEvery { api.getRelayRewardAmounts(URL, any<SubsquidRelayRewardAmountRequest>()) } returns SubsquidResponse(relay)

        assertEquals(BigInteger.valueOf(3), syncedTotal(chain(Chain.ExternalApi.Section.Type.SUBSQUID, "eth-chain", ethereum = true)))
        assertEquals(BigInteger.valueOf(4), syncedTotal(chain(Chain.ExternalApi.Section.Type.SUBSQUID)))
    }

    @Test
    fun `giantsquid rewards are queried by account id`() = runTest {
        val giant = chain(Chain.ExternalApi.Section.Type.GIANTSQUID)
        val rewards = GiantsquidRewardAmountResponse(listOf(GiantsquidRewardAmountResponse.StakingReward(BigInteger.valueOf(6)), GiantsquidRewardAmountResponse.StakingReward(BigInteger.ONE)))
        coEvery { api.getRelayRewardAmounts(URL, any<GiantsquidRewardAmountRequest>()) } returns SubsquidResponse(rewards)

        assertEquals(BigInteger.valueOf(7), syncedTotal(giant, giant.addressOf(ByteArray(32) { 9 })))
    }

    @Test
    fun `reef rewards are paged and summed`() = runTest {
        val node = ReefRewardsNode("reward-1", "Reward", BigInteger.valueOf(9), "2026-01-01T00:00:00Z", ReefAddress("signer"))
        val page = ReefStakingRewardsResponse(ReefRewardsConnection(listOf(ReefRewardsEdge(node)), SubsquidPageInfo(false, "")))
        coEvery { api.getReefRewards(URL, any()) } returns SubsquidResponse(page)

        assertEquals(BigInteger.valueOf(9), syncedTotal(chain(Chain.ExternalApi.Section.Type.REEF)))
    }

    @Test
    fun `sora rewards come from the block explorer`() = runTest {
        val sora = chain(Chain.ExternalApi.Section.Type.SUBQUERY, soraMainChainId)
        coEvery { explorer.getStakingRewarded(soraMainChainId, ADDRESS) } returns listOf("1.5", "2")

        assertEquals(BigInteger.valueOf(35_000_000_000), syncedTotal(sora))

        coEvery { explorer.getStakingRewarded(soraMainChainId, ADDRESS) } throws IllegalStateException("explorer down")
        assertEquals(BigInteger.ZERO, syncedTotal(sora))
        coVerify(exactly = 0) { api.getSumReward(any(), any()) }
    }

    @Test
    fun `chains without a supported indexer are refused`() = runTest {
        val missing = runCatching { source.sync(chain(null).id, ADDRESS) }.exceptionOrNull()
        val unknown = runCatching { source.sync(chain(Chain.ExternalApi.Section.Type.UNKNOWN, "unknown-chain").id, ADDRESS) }.exceptionOrNull()

        assertTrue(missing is RewardsNotSupportedWarning)
        assertTrue(unknown is RewardsNotSupportedWarning)
        assertTrue(inserted.isEmpty())
    }

    @Test
    fun `total rewards flow skips absent rows`() = runTest {
        every { dao.observeTotalRewards(ADDRESS) } returns flowOf(null, TotalRewardLocal(ADDRESS, BigInteger.valueOf(8)))

        assertEquals(BigInteger.valueOf(8), source.totalRewardsFlow(ADDRESS).first())
    }

    private companion object {
        const val URL = "https://rewards.example/graphql"
        const val ADDRESS = "reward-address"
    }
}
