package jp.co.soramitsu.staking.impl.data.network.subquery

import io.mockk.coEvery
import io.mockk.mockk
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.common.data.network.subquery.StakingHistoryRemote
import jp.co.soramitsu.common.data.network.subquery.SubQueryNodes
import jp.co.soramitsu.common.data.network.subquery.SubQueryResponse
import jp.co.soramitsu.common.data.network.subquery.SubsquidResponse
import jp.co.soramitsu.common.data.network.subquery.SubsquidRewardResponse
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.staking.api.domain.model.DelegationAction
import jp.co.soramitsu.staking.impl.StakingLaneFixtures
import jp.co.soramitsu.staking.impl.data.network.subquery.request.StakingDelegatorHistoryRequest
import jp.co.soramitsu.staking.impl.data.network.subquery.request.SubsquidDelegatorHistoryRequest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Delegator history toward one collator from SubQuery or SubSquid, mapped to unbonding entries. */
class SubQueryDelegationHistoryFetcherCoverageTest {

    private val api = mockk<StakingApi>()
    private val registry = mockk<ChainRegistry>()
    private val fetcher = SubQueryDelegationHistoryFetcher(api, registry)

    private fun chain(type: Chain.ExternalApi.Section.Type?, id: String = "history-chain"): String {
        val external = type?.let { Chain.ExternalApi(Chain.ExternalApi.Section(it, URL), null, null) }
        val chain = StakingLaneFixtures.chain(StakingLaneFixtures.chainAsset(chainId = id), externalApi = external)
        coEvery { registry.getChain(id) } returns chain
        return id
    }

    @Test
    fun `subquery history elements become unbondings`() = runTest {
        val element = StakingHistoryRemote.HistoryElement(
            id = "1",
            blockNumber = BigInteger.ONE,
            delegatorId = DELEGATOR,
            collatorId = COLLATOR,
            timestamp = null,
            type = BigInteger.ONE,
            roundId = "5",
            amount = BigDecimal("25")
        )
        coEvery { api.getDelegatorHistory(URL, any<StakingDelegatorHistoryRequest>()) } returns
            SubQueryResponse(StakingHistoryRemote(SubQueryNodes(listOf(element))))

        val unbonding = fetcher.fetchDelegationHistory(chain(Chain.ExternalApi.Section.Type.SUBQUERY), DELEGATOR, COLLATOR).single()

        assertEquals(BigInteger.valueOf(25), unbonding.amount)
        assertEquals(DelegationAction.UNSTAKE, unbonding.type)
        assertEquals(0L, unbonding.timeLeft)
    }

    @Test
    fun `subquery failures give no history`() = runTest {
        coEvery { api.getDelegatorHistory(URL, any<StakingDelegatorHistoryRequest>()) } throws IllegalStateException("indexer down")

        assertTrue(fetcher.fetchDelegationHistory(chain(Chain.ExternalApi.Section.Type.SUBQUERY), DELEGATOR, COLLATOR).isEmpty())
    }

    @Test
    fun `subsquid rewards become reward entries`() = runTest {
        val reward = SubsquidRewardResponse.Reward("1", BigInteger.TEN, 100, 5, null, null)
        coEvery { api.getDelegatorHistory(URL, any<SubsquidDelegatorHistoryRequest>()) } returns
            SubsquidResponse(SubsquidRewardResponse(listOf(reward)))

        val entry = fetcher.fetchDelegationHistory(chain(Chain.ExternalApi.Section.Type.SUBSQUID), DELEGATOR, COLLATOR).single()

        assertEquals(BigInteger.TEN, entry.amount)
        assertEquals(DelegationAction.REWARD, entry.type)

        coEvery { api.getDelegatorHistory(URL, any<SubsquidDelegatorHistoryRequest>()) } throws IllegalStateException("indexer down")
        assertTrue(fetcher.fetchDelegationHistory(chain(Chain.ExternalApi.Section.Type.SUBSQUID), DELEGATOR, COLLATOR).isEmpty())
    }

    @Test
    fun `chains without a supported indexer are refused`() = runTest {
        val missing = runCatching { fetcher.fetchDelegationHistory(chain(null), DELEGATOR, COLLATOR) }.exceptionOrNull()
        val unknown = runCatching {
            fetcher.fetchDelegationHistory(chain(Chain.ExternalApi.Section.Type.SORA, "sora-history"), DELEGATOR, COLLATOR)
        }.exceptionOrNull()

        assertEquals("Staking for this network is not supported yet", missing?.message)
        assertEquals("Staking for this network is not supported yet", unknown?.message)
    }

    private companion object {
        const val URL = "https://history.example/graphql"
        const val DELEGATOR = "0xdelegator"
        const val COLLATOR = "0xcollator"
    }
}
