package jp.co.soramitsu.staking.impl.domain.model

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Test

class StakingRewardCoverageTest {

    @Test
    fun `a reward keeps its event fields`() {
        val reward = StakingReward(
            accountAddress = "address",
            type = StakingReward.Type.REWARD,
            blockNumber = 100L,
            extrinsicIndex = 2,
            extrinsicHash = "0xhash",
            moduleId = "staking",
            eventIndex = "100-2",
            amount = BigDecimal("1.5"),
            blockTimestamp = 1_700_000_000L
        )

        assertEquals("address", reward.accountAddress)
        assertEquals(StakingReward.Type.REWARD, reward.type)
        assertEquals(100L, reward.blockNumber)
        assertEquals(2, reward.extrinsicIndex)
        assertEquals("0xhash", reward.extrinsicHash)
        assertEquals("staking", reward.moduleId)
        assertEquals("100-2", reward.eventIndex)
        assertEquals(BigDecimal("1.5"), reward.amount)
        assertEquals(1_700_000_000L, reward.blockTimestamp)
    }

    @Test
    fun `rewards add and slashes subtract`() {
        assertEquals(1, StakingReward.Type.REWARD.summingCoefficient)
        assertEquals(-1, StakingReward.Type.SLASH.summingCoefficient)
        assertEquals(listOf(StakingReward.Type.REWARD, StakingReward.Type.SLASH), StakingReward.Type.values().toList())
    }
}
