package jp.co.soramitsu.staking.impl.domain.rewards

import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.core.models.Asset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

/**
 * Ternoa's estimates average over the elected validators and the era payouts. With no validators, or no payout
 * history, the average is NaN, and NaN.toBigDecimal() threw NumberFormatException out of the staking screens; a
 * missing estimate is zero instead, like the other calculators.
 */
class TernoaRewardCalculatorTest {

    private val caps = mock<Asset> { on { precision } doReturn 18 }

    @Test
    fun `no validators give zero estimates instead of throwing`() = runTest {
        val calculator = TernoaRewardCalculator(emptyList(), averageValidatorPayout = 1_000.0, asset = caps)

        assertZero(calculator.calculateAvgAPY())
        assertZero(calculator.calculateMaxAPY("ternoa"))
        assertZero(calculator.getApyFor(ByteArray(32) { 3 }))
    }

    @Test
    fun `no payout history gives zero estimates instead of throwing`() = runTest {
        val calculator = TernoaRewardCalculator(listOf(target("aa", 1)), averageValidatorPayout = Double.NaN, asset = caps)

        assertZero(calculator.calculateAvgAPY())
        assertZero(calculator.calculateMaxAPY("ternoa"))
        assertZero(calculator.calculateReturns(BigDecimal.TEN, 30, isCompound = true, chainId = "ternoa").gainAmount)
    }

    @Test
    fun `a validator with stake keeps its estimate`() = runTest {
        val calculator = TernoaRewardCalculator(listOf(target("aa", 1_000)), averageValidatorPayout = 1.0, asset = caps)

        // 1 CAPS paid per era on 1,000 CAPS staked, at 10 % commission, compounded daily for a year.
        val daily = 1.0 / 1_000.0 * (1 - BigDecimal("0.1").toDouble())
        val expected = ((1 + daily).toBigDecimal().pow(DAYS_IN_YEAR) - BigDecimal.ONE).toDouble().toBigDecimal()
        assertEquals(0, expected.compareTo(calculator.getApyFor(ByteArray(1) { 0xaa.toByte() })))
        assertTrue(calculator.calculateAvgAPY().signum() > 0)
    }

    private fun target(idHex: String, stakeInCaps: Long) = RewardCalculationTarget(
        accountIdHex = idHex,
        totalStake = BigInteger.TEN.pow(18) * stakeInCaps.toBigInteger(),
        ownStake = BigInteger.ZERO,
        nominatorStakes = emptyList(),
        commission = BigDecimal("0.1")
    )

    private fun assertZero(value: BigDecimal) = assertEquals(value.toPlainString(), 0, BigDecimal.ZERO.compareTo(value))
}
