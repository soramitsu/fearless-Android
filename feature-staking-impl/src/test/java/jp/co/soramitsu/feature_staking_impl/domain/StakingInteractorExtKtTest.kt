package jp.co.soramitsu.feature_staking_impl.domain

import jp.co.soramitsu.feature_staking_api.domain.model.Exposure
import jp.co.soramitsu.feature_staking_api.domain.model.IndividualExposure
import org.junit.Assert.assertEquals
import org.junit.Test

class StakingInteractorExtKtTest {

    private val exposures = listOf(
        Exposure(
            total = 6.toBigInteger(),
            own = 0.toBigInteger(),
            others = listOf(
                IndividualExposure(byteArrayOf(3), 3.toBigInteger()),
                IndividualExposure(byteArrayOf(1), 1.toBigInteger()),
                IndividualExposure(byteArrayOf(2), 2.toBigInteger()),
            )
        ),
        Exposure(
            total = 3.toBigInteger(),
            own = 0.toBigInteger(),
            others = listOf(
                IndividualExposure(byteArrayOf(1), 1.toBigInteger()),
                IndividualExposure(byteArrayOf(2), 2.toBigInteger()),
            )
        )
    )

    @Test
    fun `should report not-active if stash is not in any validators nominations`() {
        runIsActiveTest(expected = false, who = byteArrayOf(4), maxRewarded = 3)
    }

    @Test
    fun `should report active if at least one stake portion is not in oversubscribed`() {
        // 1 is in oversubscribed section for first validator, but not for the second
        runIsActiveTest(expected = true,  who = byteArrayOf(1), maxRewarded = 2)
    }

    @Test
    fun `should report not active if all stake portions are in oversubscribed`() {
        runIsActiveTest(expected = false, who =  byteArrayOf(1), maxRewarded = 1)
    }

    @Test
    fun `should report active if all stake portions are not in oversubscribed`() {
        runIsActiveTest(expected = true, who =  byteArrayOf(3), maxRewarded = 1)
    }

    private fun runIsActiveTest(expected: Boolean, who: ByteArray, maxRewarded: Int) {
        val actual = isNominationActive(who, exposures, maxRewarded)

        assertEquals(expected, actual)
    }
}
