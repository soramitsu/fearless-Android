package jp.co.soramitsu.staking.impl.domain.rewards

import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.staking.impl.data.network.blockhain.bindings.EraRewardPoints
import jp.co.soramitsu.staking.impl.scenarios.relaychain.StakingRelayChainScenarioRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class SoraRewardCalculatorFactoryAvailabilityTest {
    private class QuoteFailure(val marker: Any) : RuntimeException("quote unavailable")
    private class QuoteCancelled(val marker: Any) : CancellationException("quote cancelled")
    private val unit = BigInteger.TEN.pow(18)
    private val account = ByteArray(32) { 1 }
    private val id = "0x" + "01".repeat(32)
    private val config = mock<Asset> {
        on { precision } doReturn 18
        on { chainId } doReturn "sora"
    }
    private val target = RewardCalculationTarget(id, unit * BigInteger.valueOf(100), unit * BigInteger.valueOf(100), emptyList(), BigDecimal.ZERO)
    private val history = mapOf(BigInteger.ONE to EraRewardPoints(BigInteger.valueOf(100), listOf(EraRewardPoints.Individual(account, BigInteger.TEN))))
    private inner class Fixture(
        payouts: Map<BigInteger, BigInteger?> = mapOf(BigInteger.ONE to unit),
        quote: BigInteger = unit * BigInteger.valueOf(2)
    ) {
        val relay = mock<StakingRelayChainScenarioRepository> {
            onBlocking { getErasValidatorRewards("sora") } doReturn payouts
            onBlocking { retrieveEraPointsDistribution("sora") } doReturn history
        }
        val scenario = mock<SoraStakingRewardsScenario> {
            onBlocking { mainAssetToRewardAssetRate() } doReturn quote
        }
        val factory = RewardCalculatorFactory(relay, mock(), scenario, mock(), mock())
        suspend fun create() = factory.createSora(config, listOf(target))
    }
    private fun equalsNumber(expected: String, actual: BigDecimal) = assertEquals(0, BigDecimal(expected).compareTo(actual))

    @Test fun presentSamplesKeepTheExistingAverageAndIgnoreOnlyAbsentSamples() = runTest {
        val f = Fixture(mapOf(BigInteger.ONE to null, BigInteger.valueOf(2) to BigInteger.ZERO, BigInteger.valueOf(3) to unit * BigInteger.valueOf(2)))
        val calculator = f.create()
        val result = calculator.calculateReturns(BigDecimal.TEN, 365, false, "sora")
        equalsNumber("14.6", result.gainAmount); equalsNumber("73", result.gainPercentage)
        verify(f.relay).getErasValidatorRewards("sora")
        verify(f.scenario).mainAssetToRewardAssetRate()
        verify(f.relay).retrieveEraPointsDistribution("sora")
    }

    @Test fun explicitlyObservedZeroPayoutStillProducesKnownZeroEarnings() = runTest {
        val calculator = Fixture(mapOf(BigInteger.ONE to BigInteger.ZERO)).create()
        val result = calculator.calculateReturns(BigDecimal.TEN, 365, false, "sora")
        equalsNumber("0", result.gainAmount); equalsNumber("0", result.gainPercentage)
    }

    @Test fun absentPayoutSamplesFailBeforeQuoteOrHistoryReads() = runTest {
        for (payouts in listOf(emptyMap<BigInteger, BigInteger?>(), mapOf(BigInteger.ONE to null))) {
            val f = Fixture(payouts)
            assertTrue(runCatching { f.create() }.exceptionOrNull() is IllegalArgumentException)
            verifyNoInteractions(f.scenario)
            verify(f.relay, never()).retrieveEraPointsDistribution(any())
        }
    }

    @Test fun negativeNonfiniteAndOverflowingMeanPayoutsAreUnavailable() = runTest {
        val invalid = listOf(
            mapOf(BigInteger.ONE to BigInteger.valueOf(-1)),
            mapOf(BigInteger.ONE to BigInteger.TEN.pow(400)),
            mapOf(BigInteger.ONE to BigInteger.TEN.pow(326), BigInteger.valueOf(2) to BigInteger.TEN.pow(326))
        )
        for (payouts in invalid) {
            val f = Fixture(payouts)
            assertTrue(runCatching { f.create() }.exceptionOrNull() is IllegalArgumentException)
            verifyNoInteractions(f.scenario)
            verify(f.relay, never()).retrieveEraPointsDistribution(any())
        }
    }

    @Test fun nonpositiveOrNonfiniteConversionQuotesFailBeforeHistory() = runTest {
        for (quote in listOf(BigInteger.ZERO, BigInteger.valueOf(-1), BigInteger.TEN.pow(400))) {
            val f = Fixture(quote = quote)
            assertTrue(runCatching { f.create() }.exceptionOrNull() is IllegalArgumentException)
            verify(f.relay, never()).retrieveEraPointsDistribution(any())
        }
    }

    @Test fun quoteFailurePreservesIdentityWithoutCreatingAZeroCalculator() = runTest {
        val f = Fixture()
        val failure = QuoteFailure(Any())
        whenever(f.scenario.mainAssetToRewardAssetRate()).doSuspendableAnswer { throw failure }
        assertSame(failure, runCatching { f.create() }.exceptionOrNull())
        verify(f.relay, never()).retrieveEraPointsDistribution(any())
    }

    @Test fun quoteCancellationPreservesIdentityAndPreventsHistoryRead() = runTest {
        val f = Fixture()
        val reason = QuoteCancelled(Any())
        whenever(f.scenario.mainAssetToRewardAssetRate()).doSuspendableAnswer { throw reason }
        assertSame(reason, runCatching { f.create() }.exceptionOrNull())
        verify(f.relay, never()).retrieveEraPointsDistribution(any())
    }

    @Test fun callerCancellationTerminatesAHeldQuoteAndRunsCleanup() = runTest {
        val f = Fixture()
        val entered = CompletableDeferred<Unit>()
        val cleaned = CompletableDeferred<Unit>()
        whenever(f.scenario.mainAssetToRewardAssetRate()).doSuspendableAnswer {
            entered.complete(Unit)
            try { awaitCancellation() } finally { cleaned.complete(Unit) }
        }
        val job = launch { f.create() }
        var completion: Throwable? = null
        job.invokeOnCompletion { completion = it }
        runCurrent(); assertTrue(entered.isCompleted)
        val reason = QuoteCancelled(Any())
        job.cancel(reason); runCurrent(); job.join()
        assertSame(reason, completion); assertTrue(cleaned.isCompleted)
        verify(f.relay, never()).retrieveEraPointsDistribution(any())
    }
}
