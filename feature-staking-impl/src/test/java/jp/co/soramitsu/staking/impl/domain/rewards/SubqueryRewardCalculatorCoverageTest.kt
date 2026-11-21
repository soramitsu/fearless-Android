package jp.co.soramitsu.staking.impl.domain.rewards

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.common.data.network.subquery.StakingCollatorsApyResponse
import jp.co.soramitsu.common.data.network.subquery.StakingLastRoundId
import jp.co.soramitsu.common.data.network.subquery.SubQueryNodes
import jp.co.soramitsu.common.data.network.subquery.SubQueryResponse
import jp.co.soramitsu.common.data.network.subquery.SubsquidCollatorsApyResponse
import jp.co.soramitsu.common.data.network.subquery.SubsquidResponse
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.staking.api.domain.api.StakingRepository
import jp.co.soramitsu.staking.api.domain.model.StakingState
import jp.co.soramitsu.staking.impl.StakingLaneFixtures
import jp.co.soramitsu.staking.impl.data.network.subquery.StakingApi
import jp.co.soramitsu.staking.impl.data.network.subquery.request.StakingAllCollatorsApyRequest
import jp.co.soramitsu.staking.impl.data.network.subquery.request.StakingCollatorsApyRequest
import jp.co.soramitsu.staking.impl.data.network.subquery.request.StakingLastRoundIdRequest
import jp.co.soramitsu.staking.impl.data.network.subquery.request.SubsquidCollatorsApyRequest
import jp.co.soramitsu.staking.impl.scenarios.parachain.StakingParachainScenarioInteractor
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Robolectric: failed indexer reads are logged with android.util.Log before falling back to empty results. */
@RunWith(RobolectricTestRunner::class)
class SubqueryRewardCalculatorCoverageTest {

    private val url = "https://indexer.example/graphql"
    private val first = ByteArray(32) { 1 }
    private val second = ByteArray(32) { 2 }
    private val repository = mockk<StakingRepository>()
    private val parachain = mockk<StakingParachainScenarioInteractor>()
    private val api = mockk<StakingApi>()

    private fun calculator(type: Chain.ExternalApi.Section.Type?, chainId: String = "moonbeam"): SubqueryRewardCalculator {
        val externalApi = type?.let { Chain.ExternalApi(Chain.ExternalApi.Section(it, url), null, null) }
        val chain = StakingLaneFixtures.chain(StakingLaneFixtures.chainAsset(chainId = chainId), externalApi = externalApi)
        every { parachain.stakingStateFlow() } returns flowOf(StakingState.Parachain.None(chain, first))
        return SubqueryRewardCalculator(repository, parachain, api)
    }

    private fun lastRound(id: String?) =
        SubQueryResponse(StakingLastRoundId(SubQueryNodes(listOf(StakingLastRoundId.RoundIdElement(id)))))

    private fun subqueryApy(vararg rows: Pair<String?, BigDecimal?>) = SubQueryResponse(
        StakingCollatorsApyResponse(SubQueryNodes(rows.map { StakingCollatorsApyResponse.CollatorApyElement(it.first, it.second) }))
    )

    private fun subsquidApy(vararg rows: Pair<String?, BigDecimal?>) = SubsquidResponse(
        SubsquidCollatorsApyResponse(rows.map { SubsquidCollatorsApyResponse.CollatorApyElement(it.first, it.second) })
    )

    @Test
    fun `subquery maximum APY is the best collator rate of the previous round`() = runTest {
        val calculator = calculator(Chain.ExternalApi.Section.Type.SUBQUERY)
        coEvery { api.getLastRoundId(url, any<StakingLastRoundIdRequest>()) } returns lastRound("12")
        coEvery { api.getAllCollatorsApy(url, any<StakingAllCollatorsApyRequest>()) } returns subqueryApy(
            first.toHexString(true) to BigDecimal("0.1"),
            null to BigDecimal("0.9"),
            second.toHexString(true) to null
        )

        assertEquals(0, BigDecimal("10").compareTo(calculator.calculateMaxAPY("moonbeam")))
    }

    @Test
    fun `subquery maximum APY is zero when the indexer fails`() = runTest {
        val calculator = calculator(Chain.ExternalApi.Section.Type.SUBQUERY)
        coEvery { api.getLastRoundId(url, any<StakingLastRoundIdRequest>()) } throws IllegalStateException("no rounds")
        coEvery { api.getAllCollatorsApy(url, any<StakingAllCollatorsApyRequest>()) } throws IllegalStateException("indexer down")

        assertEquals(BigDecimal.ZERO, calculator.calculateMaxAPY("moonbeam"))
    }

    @Test
    fun `subsquid maximum APY is zero when the indexer fails`() = runTest {
        val calculator = calculator(Chain.ExternalApi.Section.Type.SUBSQUID)
        coEvery { api.getCollatorsApy(url, any<SubsquidCollatorsApyRequest>()) } throws IllegalStateException("indexer down")

        assertEquals(0, calculator.calculateMaxAPY("moonbeam").signum())
    }

    @Test
    fun `unsupported indexers fail the maximum APY and the per-collator APY`() = runTest {
        val unsupported = calculator(Chain.ExternalApi.Section.Type.SORA)
        assertTrue(runCatching { unsupported.calculateMaxAPY("moonbeam") }.exceptionOrNull()?.message!!.contains("not supported"))
        assertTrue(runCatching { unsupported.getApy(listOf(first)) }.exceptionOrNull()?.message!!.contains("not supported"))

        val missing = calculator(null)
        assertTrue(runCatching { missing.getApy(listOf(first)) }.exceptionOrNull()?.message!!.contains("not supported"))
    }

    @Test
    fun `subsquid collator APY keeps only the selected candidates`() = runTest {
        val calculator = calculator(Chain.ExternalApi.Section.Type.SUBSQUID)
        coEvery { api.getCollatorsApy(url, any<SubsquidCollatorsApyRequest>()) } returns subsquidApy(
            first.toHexString(true) to BigDecimal("0.07"),
            second.toHexString(true) to BigDecimal("0.03"),
            null to BigDecimal("0.5")
        )

        assertEquals(mapOf(first.toHexString() to BigDecimal("0.07")), calculator.getApy(listOf(first)))
        assertEquals(BigDecimal("0.03"), calculator.getApyFor(second))
    }

    @Test
    fun `subsquid collator APY is empty and zero when the indexer fails`() = runTest {
        val calculator = calculator(Chain.ExternalApi.Section.Type.SUBSQUID)
        coEvery { api.getCollatorsApy(url, any<SubsquidCollatorsApyRequest>()) } throws IllegalStateException("indexer down")

        assertTrue(calculator.getApy(listOf(first)).isEmpty())
        assertEquals(BigDecimal.ZERO, calculator.getApyFor(first))
    }

    @Test
    fun `subquery collator APY reads the previous round for the candidates`() = runTest {
        val calculator = calculator(Chain.ExternalApi.Section.Type.SUBQUERY)
        coEvery { api.getLastRoundId(url, any<StakingLastRoundIdRequest>()) } returns lastRound("not-a-number")
        coEvery { api.getCollatorsApy(url, any<StakingCollatorsApyRequest>()) } returns subqueryApy(
            first.toHexString(true) to BigDecimal("0.2"),
            null to BigDecimal("0.4")
        )

        assertEquals(mapOf(first.toHexString() to BigDecimal("0.2")), calculator.getApy(listOf(first)))
    }

    @Test
    fun `subquery collator APY is empty when the indexer fails`() = runTest {
        val calculator = calculator(Chain.ExternalApi.Section.Type.SUBQUERY)
        coEvery { api.getLastRoundId(url, any<StakingLastRoundIdRequest>()) } returns lastRound("5")
        coEvery { api.getCollatorsApy(url, any<StakingCollatorsApyRequest>()) } throws IllegalStateException("indexer down")

        assertTrue(calculator.getApy(listOf(first)).isEmpty())
        assertNull(calculator.getApy(listOf(first))[first.toHexString()])
    }

    @Test
    fun `average APY remembers the last calculated returns`() = runTest {
        val calculator = calculator(Chain.ExternalApi.Section.Type.SUBSQUID)
        coEvery { repository.getTotalIssuance("moonbeam") } returns BigInteger.valueOf(1_000)
        coEvery { parachain.total("moonbeam") } returns Result.success(BigInteger.valueOf(250))
        assertEquals(0, calculator.calculateAvgAPY().signum())

        calculator.calculateReturns(BigDecimal.TEN, DAYS_IN_YEAR, true, "moonbeam")

        // 2.5% of the issuance over a quarter of it staked: 10% a year.
        assertEquals(10.0, calculator.calculateAvgAPY().toDouble(), 1e-9)
        assertEquals(10.0, calculator.calculateAvgAPY("moonbeam").toDouble(), 1e-9)
    }

    @Test
    fun `nothing staked gives zero returns and per-collator returns are always zero`() = runTest {
        val calculator = calculator(Chain.ExternalApi.Section.Type.SUBSQUID)
        coEvery { repository.getTotalIssuance("moonbeam") } returns BigInteger.valueOf(1_000)
        coEvery { parachain.total("moonbeam") } returns Result.success(BigInteger.ZERO)

        val returns = calculator.calculateReturns(BigDecimal.TEN, 30, false, "moonbeam")
        assertEquals(0, returns.gainAmount.signum())
        assertEquals(0, calculator.calculateAvgAPY().signum())

        val perCollator = calculator.calculateReturns(10.0, 30, true, first.toHexString())
        assertEquals(BigDecimal.ZERO, perCollator.gainAmount)
        assertEquals(BigDecimal.ZERO, perCollator.gainPercentage)
    }
}
