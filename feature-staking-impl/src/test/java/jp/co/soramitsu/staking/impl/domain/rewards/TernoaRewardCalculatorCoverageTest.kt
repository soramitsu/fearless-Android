package jp.co.soramitsu.staking.impl.domain.rewards

import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ternoaChainId
import jp.co.soramitsu.staking.impl.StakingLaneFixtures
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TernoaRewardCalculatorCoverageTest {

    private val asset = StakingLaneFixtures.chainAsset(chainId = ternoaChainId, symbol = "CAPS", precision = 0)
    private val first = ByteArray(32) { 1 }
    private val second = ByteArray(32) { 2 }

    // Average payout 20 over two validators: each earns 10 per era (one era per day).
    private val validators = listOf(
        RewardCalculationTarget(first.toHexString(), BigInteger.valueOf(1_000), BigInteger.valueOf(1_000), emptyList(), BigDecimal.ZERO),
        RewardCalculationTarget(second.toHexString(), BigInteger.valueOf(2_000), BigInteger.valueOf(2_000), emptyList(), BigDecimal("0.5"))
    )

    private fun calculator(targets: List<RewardCalculationTarget> = validators) =
        TernoaRewardCalculator(validators = targets, averageValidatorPayout = 20.0, asset = asset)

    private fun compound(daily: Double) = Math.pow(1 + daily, 365.0) - 1

    @Test
    fun `max APY is the best compounded validator rate as a percentage`() = runTest {
        // First validator: 10 / 1000 daily without commission.
        assertEquals(compound(0.01) * 100, calculator().calculateMaxAPY(ternoaChainId).toDouble(), 1e-6)
    }

    @Test
    fun `average APY uses the mean stake and mean commission`() = runTest {
        // Mean stake 1500, mean commission 0.25: 10 / 1500 * 0.75 = 0.005 daily.
        assertEquals(compound(0.005) * 100, calculator().calculateAvgAPY().toDouble(), 1e-6)
    }

    @Test
    fun `APY for a known validator is its compounded fraction, unknown targets fall back to the average`() = runTest {
        val calculator = calculator()
        assertEquals(compound(0.0025), calculator.getApyFor(second).toDouble(), 1e-9)
        assertEquals(compound(0.005), calculator.getApyFor(ByteArray(32) { 9 }).toDouble(), 1e-9)
    }

    @Test
    fun `compound returns follow the maximum compounded APY`() = runTest {
        val returns = calculator().calculateReturns(BigDecimal("100"), 365, true, ternoaChainId)
        val expectedGain = compound(0.01) * 100
        assertEquals(expectedGain, returns.gainAmount.toDouble(), 1e-6)
        assertEquals(expectedGain, returns.gainPercentage.toDouble(), 1e-4)
    }

    @Test
    fun `simple returns follow the best uncompounded validator rate`() = runTest {
        // First validator earns 0.01 a day, 3.65 a year without compounding.
        val returns = calculator().calculateReturns(BigDecimal("100"), 365, false, ternoaChainId)
        assertEquals(365.0, returns.gainAmount.toDouble(), 1e-6)
        assertEquals(365.0, returns.gainPercentage.toDouble(), 1e-4)
    }

    @Test
    fun `returns for a target validator scale with days and stay zero percent for a zero amount`() = runTest {
        val calculator = calculator()
        val daily = compound(0.01) / 365
        val returns = calculator.calculateReturns(50.0, 30, true, first.toHexString())
        assertEquals(50.0 * daily * 30, returns.gainAmount.toDouble(), 1e-6)

        val zero = calculator.calculateReturns(0.0, 30, false, first.toHexString())
        assertEquals(0, zero.gainAmount.signum())
        assertEquals(BigDecimal.ZERO, zero.gainPercentage)
    }

    @Test
    fun `returns for an unknown target validator fail`() = runTest {
        val failure = runCatching { calculator().calculateReturns(1.0, 1, true, "unknown") }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
        assertTrue(failure!!.message!!.contains("unknown"))
    }

    @Test
    fun `no validators give a zero maximum and zero simple returns`() = runTest {
        val calculator = calculator(emptyList())
        assertEquals(0, calculator.calculateMaxAPY(ternoaChainId).signum())
        val returns = calculator.calculateReturns(BigDecimal.TEN, 365, false, ternoaChainId)
        assertEquals(0, returns.gainAmount.signum())
        assertEquals(0, returns.gainPercentage.signum())
    }
}
