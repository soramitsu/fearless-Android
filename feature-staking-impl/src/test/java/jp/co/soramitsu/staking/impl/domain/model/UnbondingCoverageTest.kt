package jp.co.soramitsu.staking.impl.domain.model

import java.math.BigDecimal
import java.math.BigInteger
import java.time.Instant
import jp.co.soramitsu.common.data.network.subquery.StakingHistoryRemote
import jp.co.soramitsu.common.data.network.subquery.SubsquidRewardResponse
import jp.co.soramitsu.staking.api.domain.model.DelegationAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Robolectric: the Subsquid timestamp parser branches on android.os.Build.VERSION.SDK_INT. */
@RunWith(RobolectricTestRunner::class)
class UnbondingCoverageTest {

    private fun history(timestamp: String?, amount: BigDecimal?, type: BigInteger?) = StakingHistoryRemote.HistoryElement(
        id = "1",
        blockNumber = BigInteger.ONE,
        delegatorId = "delegator",
        collatorId = "collator",
        timestamp = timestamp,
        type = type,
        roundId = "7",
        amount = amount
    )

    private fun reward(timestamp: String?, amount: BigInteger?) =
        SubsquidRewardResponse.Reward("reward", amount, 10, 2, timestamp, "0xhash")

    private fun assertCalculatedNow(before: Long, unbonding: Unbonding) {
        assertTrue(unbonding.calculatedAt in before..System.currentTimeMillis())
    }

    @Test
    fun `history element measures the time since its timestamp in seconds`() {
        val before = System.currentTimeMillis()
        val timestamp = (before / 1000 - 100).toString()

        val unbonding = history(timestamp, BigDecimal("12.7"), BigInteger.ONE).toUnbonding()

        assertTrue(unbonding.timeLeft.toString(), unbonding.timeLeft in 100L..102L)
        assertEquals(BigInteger.valueOf(12), unbonding.amount)
        assertEquals(DelegationAction.UNSTAKE, unbonding.type)
        assertCalculatedNow(before, unbonding)
    }

    @Test
    fun `history element without readable values falls back to zero and other`() {
        val missing = history(null, null, null).toUnbonding()
        assertEquals(0L, missing.timeLeft)
        assertEquals(BigInteger.ZERO, missing.amount)
        assertEquals(DelegationAction.OTHER, missing.type)

        val unreadable = history("yesterday", BigDecimal.ONE, BigInteger.valueOf(2)).toUnbonding()
        assertEquals(0L, unreadable.timeLeft)
        assertEquals(DelegationAction.REWARD, unreadable.type)
    }

    @Test
    fun `subsquid reward measures the time since its ISO timestamp`() {
        val before = System.currentTimeMillis()
        val timestamp = Instant.ofEpochMilli(before).minusSeconds(60).toString()

        val unbonding = reward(timestamp, BigInteger.valueOf(42)).toUnbonding()

        assertTrue(unbonding.timeLeft.toString(), unbonding.timeLeft in 60L..62L)
        assertEquals(BigInteger.valueOf(42), unbonding.amount)
        assertEquals(DelegationAction.REWARD, unbonding.type)
        assertCalculatedNow(before, unbonding)
    }

    @Test
    fun `subsquid reward without timestamp or amount is zero`() {
        val unbonding = reward(null, null).toUnbonding()

        assertEquals(0L, unbonding.timeLeft)
        assertEquals(BigInteger.ZERO, unbonding.amount)
        assertEquals(DelegationAction.REWARD, unbonding.type)
    }

    @Test
    fun `unbondings are values`() {
        val unbonding = Unbonding(BigInteger.TEN, 5L, 1L, DelegationAction.STAKE)

        assertEquals(unbonding, unbonding.copy())
        assertEquals(BigInteger.ONE, unbonding.copy(amount = BigInteger.ONE).amount)
        assertEquals(null, unbonding.copy(type = null).type)
    }
}
