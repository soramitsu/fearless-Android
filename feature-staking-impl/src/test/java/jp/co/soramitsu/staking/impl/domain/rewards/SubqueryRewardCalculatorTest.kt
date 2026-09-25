package jp.co.soramitsu.staking.impl.domain.rewards

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Test

class SubqueryRewardCalculatorTest {

    @Test
    fun `empty Subsquid response has zero maximum reward`() {
        assertEquals(BigDecimal.ZERO, maximumRewardOrZero(emptyMap()))
    }

    @Test
    fun `null Subsquid rewards have zero maximum reward`() {
        val rewards = mapOf(
            "collator-1" to null,
            "collator-2" to null
        )

        assertEquals(BigDecimal.ZERO, maximumRewardOrZero(rewards))
    }

    @Test
    fun `negative Subsquid rewards are clamped to zero`() {
        val rewards = mapOf(
            "collator-1" to BigDecimal("-1.25"),
            "collator-2" to BigDecimal("-0.01")
        )

        assertEquals(BigDecimal.ZERO, maximumRewardOrZero(rewards))
    }

    @Test
    fun `maximum Subsquid reward is preserved`() {
        val rewards = mapOf(
            "collator-1" to BigDecimal("1.25"),
            "collator-2" to BigDecimal("3.50"),
            "collator-3" to null
        )

        assertEquals(BigDecimal("3.50"), maximumRewardOrZero(rewards))
    }
}
