package jp.co.soramitsu.staking.impl.data.repository

import java.math.BigInteger
import jp.co.soramitsu.staking.impl.data.model.Payout
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pending payouts must show what `pallet-staking` pays: Perbill shares rounded down, each Perbill
 * applied to a balance rounded to the nearest plank (an exact half down), all in exact integers.
 */
class PayoutRepositoryTest {

    @Test
    fun shouldSplitTheEraRewardLikeTheRuntime_whenAmountsAreSmall() = runTest {
        val fixture = PayoutRepositoryFixture()
        fixture.start()

        // 10 000 - 10% commission leaves 9 000; the nominator's 90% of it is 8 100.
        val nominatorPayout = Payout(fixture.validatorAddress, fixture.era, BigInteger.valueOf(8_100))
        assertEquals(listOf(nominatorPayout), fixture.payouts(fixture.nominator()))
        // The validator keeps the 1 000 commission and its own 10% of 9 000.
        val validatorPayout = Payout(fixture.validatorAddress, fixture.era, BigInteger.valueOf(1_900))
        assertEquals(listOf(validatorPayout), fixture.payouts(fixture.validator()))
    }

    @Test
    fun shouldKeepEveryPlank_whenTheRewardHasNoExactDouble() = runTest {
        val fixture = PayoutRepositoryFixture()
        fixture.start()
        val reward = BigInteger("9007199254740993") // 2^53 + 1
        fixture.reward(reward)
        fixture.prefs(commission = BigInteger.ZERO)

        fixture.exposure(own = BigInteger.ZERO, stake = BigInteger.valueOf(1_000))
        assertEquals(reward, fixture.payouts(fixture.nominator()).single().amount)

        fixture.exposure(own = BigInteger.valueOf(1_000), stake = BigInteger.ZERO)
        assertEquals(reward, fixture.payouts(fixture.validator()).single().amount)
    }

    @Test
    fun shouldTruncateTheShareToPerbill_beforeApplyingItToTheReward() = runTest {
        val fixture = PayoutRepositoryFixture()
        fixture.start()
        fixture.prefs(commission = BigInteger.ZERO)
        fixture.exposure(own = BigInteger.ZERO, stake = BigInteger.valueOf(1_000))
        fixture.reward(BigInteger.valueOf(1_500_000_000))
        fixture.points(total = BigInteger.valueOf(3), earned = BigInteger.ONE)

        // One third is 333 333 333 per billion, and 333 333 333 * 1.5 = 499 999 999.5, an exact half.
        assertEquals(BigInteger.valueOf(499_999_999), fixture.payouts(fixture.nominator()).single().amount)
    }

    @Test
    fun shouldRoundTheCommissionToTheNearestPlank_withAnExactHalfDown() = runTest {
        val fixture = PayoutRepositoryFixture()
        fixture.start()
        fixture.exposure(own = BigInteger.ZERO, stake = BigInteger.valueOf(1_000))
        fixture.reward(BigInteger.TEN)

        fixture.prefs(commission = BigInteger.valueOf(350_000_000)) // 3.5 planks
        assertEquals(BigInteger.valueOf(3), fixture.payouts(fixture.validator()).single().amount)
        assertEquals(BigInteger.valueOf(7), fixture.payouts(fixture.nominator()).single().amount)

        fixture.prefs(commission = BigInteger.valueOf(360_000_000)) // 3.6 planks
        assertEquals(BigInteger.valueOf(4), fixture.payouts(fixture.validator()).single().amount)
        assertEquals(BigInteger.valueOf(6), fixture.payouts(fixture.nominator()).single().amount)
    }

    @Test
    fun shouldMatchTheRuntimeToThePlank_forAPolkadotSizedEra() = runTest {
        val fixture = PayoutRepositoryFixture()
        fixture.start()
        fixture.reward(BigInteger("2773841293812345"))
        fixture.points(total = BigInteger.valueOf(295_420), earned = BigInteger.valueOf(1_340))
        fixture.prefs(commission = BigInteger.valueOf(30_000_000))
        fixture.exposure(
            total = BigInteger("25000123456789012"),
            own = BigInteger("1000000000000000"),
            stake = BigInteger("123456789012345")
        )

        assertEquals(BigInteger("60268580657"), fixture.payouts(fixture.nominator()).single().amount)
        assertEquals(BigInteger("865632685935"), fixture.payouts(fixture.validator()).single().amount)
    }

    @Test
    fun shouldMatchTheRuntimeToThePlank_forAnEighteenDecimalEra() = runTest {
        val fixture = PayoutRepositoryFixture()
        fixture.start()
        fixture.reward(BigInteger("12345678901234567890123"))
        fixture.points(total = BigInteger.valueOf(1_000_000), earned = BigInteger.valueOf(37_123))
        fixture.prefs(commission = BigInteger.valueOf(50_000_000))
        fixture.exposure(
            total = BigInteger("98765432109876543210987654"),
            own = BigInteger("1234567890123456789012"),
            stake = BigInteger("4567890123456789012345678")
        )

        assertEquals(BigInteger("20136886576125426573"), fixture.payouts(fixture.nominator()).single().amount)
        assertEquals(BigInteger("22920873872207812285"), fixture.payouts(fixture.validator()).single().amount)
    }

    @Test
    fun shouldShowNoReward_whenTheEraHasNoRewardPoints() = runTest {
        val fixture = PayoutRepositoryFixture()
        fixture.start()
        fixture.points(total = BigInteger.ZERO, earned = BigInteger.ZERO)

        assertEquals(BigInteger.ZERO, fixture.payouts(fixture.nominator()).single().amount)
        assertEquals(BigInteger.ZERO, fixture.payouts(fixture.validator()).single().amount)
    }
}
