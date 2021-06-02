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
    fun `account not from nominators should not be rewarded`() {
        runWillBeRewardedTest(expected = false, who = byteArrayOf(4), maxRewarded = 3)
    }

    @Test
    fun `account from first maxRewarded should be rewarded`() {
        runWillBeRewardedTest(expected = true, who = byteArrayOf(2), maxRewarded = 2)
    }

    @Test
    fun `account not from first maxRewarded should not be rewarded`() {
        runWillBeRewardedTest(expected = true, who = byteArrayOf(3), maxRewarded = 2)
    }

    @Test
    fun `should report not-active if stash is not in any validators nominations`() {
        runIsActiveTest(expected = false, who = byteArrayOf(4), maxRewarded = 3)
    }

    @Test
    fun `active if at least one stake portion is not in oversubscribed section of validator`() {
        // 1 is in oversubscribed section for first validator, but not for the second
        runIsActiveTest(expected = true,  who = byteArrayOf(1), maxRewarded = 2)
    }

    @Test
    fun `not active if all stake portions are in oversubscribed section of validator`() {
        runIsActiveTest(expected = false, who =  byteArrayOf(1), maxRewarded = 1)
    }

    @Test
    fun `active if all stake portions are not in oversubscribed section of validator`() {
        runIsActiveTest(expected = true, who =  byteArrayOf(3), maxRewarded = 1)
    }

    private fun runIsActiveTest(expected: Boolean, who: ByteArray, maxRewarded: Int) {
        val actual = isNominationActive(who, exposures, maxRewarded)

        assertEquals(expected, actual)
    }

    private fun runWillBeRewardedTest(expected: Boolean, who: ByteArray, maxRewarded: Int) {
        val exposure = exposures.first()

        val actual = exposure.willAccountBeRewarded(who, maxRewarded)

        assertEquals(expected, actual)
    }
}
