package jp.co.soramitsu.staking.impl.domain.rewards

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.staking.api.data.StakingSharedState
import jp.co.soramitsu.staking.impl.data.network.blockhain.bindings.EraRewardPoints
import jp.co.soramitsu.staking.impl.presentation.common.rewardDestination.RewardDestinationProvider
import jp.co.soramitsu.wallet.impl.domain.model.AssetAmountState
import jp.co.soramitsu.wallet.impl.domain.model.FiatValuationContext
import jp.co.soramitsu.wallet.impl.domain.model.Token
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.*

class SoraRewardCalculatorAvailabilityTest {
    @get:Rule val instant = InstantTaskExecutorRule()
    private val account = ByteArray(32) { 1 }
    private val id = "0x" + "01".repeat(32)
    private val config = mock<Asset> {
        on { precision } doReturn 0
        on { chainId } doReturn "sora"
        on { id } doReturn "val"
        on { symbol } doReturn "VAL"
        on { priceId } doReturn "val"
        on { priceProvider } doReturn null
    }
    private fun target(stake: BigInteger = BigInteger.valueOf(100), commission: BigDecimal = BigDecimal.ZERO) =
        RewardCalculationTarget(id, stake, stake, emptyList(), commission)
    private fun history(total: BigInteger = BigInteger.valueOf(100), points: BigInteger = BigInteger.TEN) =
        mapOf(BigInteger.ONE to EraRewardPoints(total, listOf(EraRewardPoints.Individual(account, points))))
    private fun calculator(
        rate: Double = 2.0,
        payout: Double = 1.0,
        targets: List<RewardCalculationTarget> = listOf(target()),
        selected: List<String> = listOf(id),
        history: Map<BigInteger, EraRewardPoints> = history()
    ) = SoraRewardCalculator(targets, rate, payout, config, selected, history)
    private fun equalsNumber(expected: String, actual: BigDecimal) = assertEquals(0, BigDecimal(expected).compareTo(actual))

    @Test fun validFormulaAndExistingCompoundFlagBehaviorRemainUnchanged() = runBlocking {
        val calculator = calculator()
        for (compound in listOf(false, true)) {
            val result = calculator.calculateReturns(BigDecimal.TEN, 365, compound, "sora")
            equalsNumber("14.6", result.gainAmount); equalsNumber("73", result.gainPercentage)
            val validator = calculator.calculateReturns(10.0, 365, compound, id)
            equalsNumber("14.6", validator.gainAmount); equalsNumber("73", validator.gainPercentage)
        }
        equalsNumber("73", calculator.calculateMaxAPY("sora"))
        equalsNumber("73", calculator.calculateAvgAPY())
        equalsNumber("0.73", calculator.getApyFor(account))
    }

    @Test fun knownZeroAmountAndZeroDaysProduceKnownZeroReturns() = runBlocking {
        val calculator = calculator()
        for (result in listOf(
            calculator.calculateReturns(BigDecimal.ZERO, 365, false, "sora"),
            calculator.calculateReturns(BigDecimal.TEN, 0, false, "sora"),
            calculator.calculateReturns(0.0, 365, false, id)
        )) {
            equalsNumber("0", result.gainAmount); equalsNumber("0", result.gainPercentage)
        }
    }

    @Test fun explicitlyObservedZeroPayoutIsLegitimateZeroEarnings() = runBlocking {
        val result = calculator(payout = 0.0).calculateReturns(BigDecimal.TEN, 365, false, "sora")
        equalsNumber("0", result.gainAmount); equalsNumber("0", result.gainPercentage)
    }

    @Test fun noIndividualPointsInPresentHistoryKeepsTheReleasedZeroContributionPolicy() = runBlocking {
        val observed = mapOf(BigInteger.ONE to EraRewardPoints(BigInteger.valueOf(100), emptyList()))
        val result = calculator(history = observed).calculateReturns(BigDecimal.TEN, 365, false, "sora")
        equalsNumber("0", result.gainAmount); equalsNumber("0", result.gainPercentage)
    }

    @Test fun existingMatchedTargetSubsetPolicyIsPreserved() = runBlocking {
        val result = calculator(selected = listOf(id, "0x02")).calculateReturns(BigDecimal.TEN, 365, false, "sora")
        equalsNumber("14.6", result.gainAmount); equalsNumber("73", result.gainPercentage)
    }

    @Test fun absentStatisticsCannotConstructAZeroReturnCalculator() {
        val invalid = listOf<() -> SoraRewardCalculator>(
            { calculator(targets = emptyList()) }, { calculator(selected = emptyList()) },
            { calculator(selected = listOf("0x02")) }, { calculator(history = emptyMap()) },
            { calculator(history = history(total = BigInteger.ZERO, points = BigInteger.ZERO)) }
        )
        invalid.forEach { assertTrue(runCatching { it() }.exceptionOrNull() is IllegalArgumentException) }
    }

    @Test fun invalidConversionRatesAndPayoutsNeverBecomeZeroEarnings() {
        for (rate in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertTrue(runCatching { calculator(rate = rate) }.exceptionOrNull() is IllegalArgumentException)
        }
        for (payout in listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertTrue(runCatching { calculator(payout = payout) }.exceptionOrNull() is IllegalArgumentException)
        }
    }

    @Test fun invalidStakeCommissionAndHistoricalPointsAreRejected() {
        val invalid = listOf<() -> SoraRewardCalculator>(
            { calculator(targets = listOf(target(stake = BigInteger.ZERO))) },
            { calculator(targets = listOf(target(stake = BigInteger.valueOf(-1)))) },
            { calculator(targets = listOf(target(stake = BigInteger.TEN.pow(400)))) },
            { calculator(targets = listOf(target(commission = BigDecimal("-0.1")))) },
            { calculator(targets = listOf(target(commission = BigDecimal("1.1")))) },
            { calculator(history = history(points = BigInteger.valueOf(-1))) },
            { calculator(history = history(total = BigInteger.TEN.pow(400))) }
        )
        invalid.forEach { assertTrue(runCatching { it() }.exceptionOrNull() is IllegalArgumentException) }
    }

    @Test fun convertedStakeOrAnnualReturnOverflowIsUnavailable() {
        assertTrue(runCatching { calculator(rate = Double.MAX_VALUE) }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { calculator(payout = Double.MAX_VALUE, rate = 1.0) }.exceptionOrNull() is IllegalArgumentException)
    }

    @Test fun invalidRequestedAmountsAndPeriodsAreRejected() = runBlocking {
        val calculator = calculator()
        for (amount in listOf(Double.NaN, Double.POSITIVE_INFINITY, -1.0)) {
            assertTrue(runCatching { calculator.calculateReturns(amount, 365, false, id) }.exceptionOrNull() is IllegalArgumentException)
        }
        for (amount in listOf(BigDecimal("1E400"), BigDecimal("1E-400"), BigDecimal("-1E-400"))) {
            assertTrue(runCatching { calculator.calculateReturns(amount, 365, false, "sora") }.exceptionOrNull() is IllegalArgumentException)
        }
        assertTrue(runCatching { calculator.calculateReturns(BigDecimal.ONE, -1, false, "sora") }.exceptionOrNull() is IllegalArgumentException)
    }

    @Test fun actualProviderRevokesItsOldEstimatesWhenActualCalculatorRejectsAnAmount() = runBlocking {
        val resources = mock<ResourceManager> {
            on { getString(any()) } doReturn "Unavailable"
            on { getString(any(), any()) } doReturn "gain"
        }
        val token = Token(config, AssetAmountState.Known(BigDecimal("2")), AssetAmountState.Missing,
            FiatValuationContext("usd"), "usd", "val")
        val rewards = mock<SoraStakingRewardsScenario> { onBlocking { getRewardAsset() } doReturn token }
        val shared = mock<StakingSharedState> { on { assetWithChain } doReturn emptyFlow() }
        val provider = RewardDestinationProvider(resources, mock(), mock(), mock(), mock(), shared, mock(), rewards)
        val asset = jp.co.soramitsu.wallet.impl.domain.model.Asset.createEmpty(config, 7, account, minSupportedVersion = null)
            .copy(token = token)
        val calculator = calculator()
        provider.updateReturns(calculator, asset, BigDecimal.TEN)
        assertNotNull(provider.rewardReturnsLiveData.value?.restake?.fiatAmount)
        assertTrue(runCatching { provider.updateReturns(calculator, asset, BigDecimal("1E400")) }.exceptionOrNull() is IllegalArgumentException)
        val value = requireNotNull(provider.rewardReturnsLiveData.value)
        listOf(value.restake, value.payout).forEach {
            assertEquals("Unavailable", it.amount); assertEquals("Unavailable", it.gain); assertNull(it.fiatAmount)
        }
    }
}
