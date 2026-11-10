package jp.co.soramitsu.staking.impl.domain

import jp.co.soramitsu.staking.api.domain.model.IndividualExposure
import jp.co.soramitsu.staking.api.domain.model.LegacyExposure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigInteger

/**
 * The minimum stake that earns rewards comes from `MinimumActiveStake` or, when that read fails, from the cached
 * exposures. With neither it is unavailable: never zero and never an exception, even with missing nominator pages.
 */
class MinimumActiveStakeTest {
    private fun exposure(vararg nominations: Long) = LegacyExposure(
        BigInteger.TEN, BigInteger.ONE, nominations.map { IndividualExposure(ByteArray(32), it.toBigInteger()) }
    )

    @Test
    fun `a read minimum wins over the exposures`() {
        assertEquals(500.toBigInteger(), lowestActiveStake(500.toBigInteger(), listOf(exposure(40))))
    }

    @Test
    fun `a failed read falls back to the lowest cached nomination, missing pages included`() {
        assertEquals(40.toBigInteger(), lowestActiveStake(null, listOf(exposure(), exposure(70, 40), exposure())))
    }

    @Test
    fun `no read and no cached nomination is unavailable`() {
        assertNull(lowestActiveStake(null, emptyList()))
        assertNull(lowestActiveStake(null, listOf(exposure(), exposure())))
    }
}
