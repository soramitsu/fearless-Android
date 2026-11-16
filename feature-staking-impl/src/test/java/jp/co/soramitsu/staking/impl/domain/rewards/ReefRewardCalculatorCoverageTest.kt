package jp.co.soramitsu.staking.impl.domain.rewards

import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.staking.impl.StakingLaneFixtures
import jp.co.soramitsu.staking.impl.data.network.blockhain.bindings.EraRewardPoints
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reef estimates from historical era points and the last era payout. Validator A earns half of the last era's points on
 * a 1 000 REEF stake: with a payout of 2 per era, one REEF earns 0.001 per era, 0.365 a year before commission.
 */
class ReefRewardCalculatorCoverageTest {

    private val asset = StakingLaneFixtures.chainAsset(symbol = "REEF", precision = 10)
    private val a = ByteArray(32) { 1 }
    private val b = ByteArray(32) { 2 }
    private val c = ByteArray(32) { 3 }

    private fun target(id: ByteArray, commission: String, stakeTokens: Long = 1_000) = RewardCalculationTarget(
        accountIdHex = id.toHexString(),
        totalStake = StakingLaneFixtures.planks(stakeTokens),
        ownStake = BigInteger.ZERO,
        nominatorStakes = emptyList(),
        commission = BigDecimal(commission)
    )

    private val points = EraRewardPoints(
        BigInteger.valueOf(100),
        listOf(EraRewardPoints.Individual(a, BigInteger.valueOf(50)), EraRewardPoints.Individual(b, BigInteger.valueOf(50)))
    )

    private fun calculator(
        validators: List<RewardCalculationTarget> = listOf(target(a, "0"), target(b, "0.1"), target(c, "0.5")),
        history: Map<BigInteger, EraRewardPoints> = mapOf(BigInteger.ONE to points, BigInteger.TWO to points),
        targets: List<String> = validators.map { it.accountIdHex }
    ) = ReefRewardCalculator(
        amount = BigDecimal.ONE,
        validators = validators,
        erasPayouts = mapOf(BigInteger.ONE to 1.0, BigInteger.TWO to 2.0),
        asset = asset,
        calculationTargets = targets,
        historicalRewardDistribution = history
    )

    private fun compoundPercentage(apy: Double): Double = (Math.pow(1 + apy / DAYS_IN_YEAR, DAYS_IN_YEAR.toDouble()) - 1) * 100

    @Test
    fun `validator apy depends on its points share, stake and commission`() = runTest {
        val calculator = calculator()

        assertEquals(0.365, calculator.getApyFor(a).toDouble(), 1e-9)
        assertEquals(0.3285, calculator.getApyFor(b).toDouble(), 1e-9)
        // C produced no blocks in the history.
        assertEquals(0.0, calculator.getApyFor(c).toDouble(), 1e-12)
    }

    @Test
    fun `unknown validators get the expected apy after the median commission`() = runTest {
        val calculator = calculator()
        val average = (0.365 + 0.3285 + 0.0) / 3

        assertEquals(average * (1 - 0.1), calculator.getApyFor(ByteArray(32) { 9 }).toDouble(), 1e-9)
    }

    @Test
    fun `maximum and average apy compound daily`() = runTest {
        val calculator = calculator()

        assertEquals(compoundPercentage(0.365), calculator.calculateMaxAPY("reef").toDouble(), 1e-6)
        val average = listOf(0.365, 0.3285, 0.0).map(::compoundPercentage).average()
        assertEquals(average, calculator.calculateAvgAPY().toDouble(), 1e-6)
    }

    @Test
    fun `returns for an amount use the maximum apy`() = runTest {
        val calculator = calculator()

        val simple = calculator.calculateReturns(BigDecimal.TEN, 30, false, "reef")
        assertEquals(10 * 0.365 / DAYS_IN_YEAR * 30, simple.gainAmount.toDouble(), 1e-9)
        assertEquals(0.365 / DAYS_IN_YEAR * 30 * 100, simple.gainPercentage.toDouble(), 1e-6)

        val zero = calculator.calculateReturns(BigDecimal.ZERO, 30, true, "reef")
        assertEquals(0, zero.gainPercentage.signum())
    }

    @Test
    fun `returns for a validator use its own apy`() = runTest {
        val calculator = calculator()

        val compound = calculator.calculateReturns(1.0, DAYS_IN_YEAR, true, b.toHexString())
        assertEquals(compoundPercentage(0.3285), compound.gainPercentage.toDouble(), 1e-6)

        val unknown = runCatching { calculator.calculateReturns(1.0, 30, true, "ff") }
        assertTrue(unknown.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun `missing history gives zero estimates`() = runTest {
        val calculator = calculator(history = emptyMap())

        assertEquals(0.0, calculator.getApyFor(a).toDouble(), 1e-12)
        assertEquals(0, calculator.calculateMaxAPY("reef").signum())
    }

    @Test
    fun `targets outside the validator list are ignored`() = runTest {
        val calculator = calculator(targets = listOf(a.toHexString(), "unknown"))

        assertEquals(compoundPercentage(0.365), calculator.calculateAvgAPY().toDouble(), 1e-6)
    }
}
