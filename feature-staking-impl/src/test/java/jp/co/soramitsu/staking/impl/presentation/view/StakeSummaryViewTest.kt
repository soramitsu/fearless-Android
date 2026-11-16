package jp.co.soramitsu.staking.impl.presentation.view

import android.view.View
import jp.co.soramitsu.feature_staking_impl.databinding.ViewStakeSummaryBinding
import jp.co.soramitsu.feature_staking_impl.databinding.ViewStakingInfoBinding
import jp.co.soramitsu.staking.impl.presentation.StakingViewFixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StakeSummaryViewTest : StakingViewFixture() {
    @Test
    fun totalsAndRewardsReplaceLoadingAndExposeOptionalFiat() {
        val view = StakeSummaryView(context)
        val binding = ViewStakeSummaryBinding.bind(view)
        view.setTitle("My stake")
        view.setTotalStaked("100 DOT")
        view.setTotalStakedFiat("$200")
        view.setRewardsApr("12 DOT")
        view.setRewardsAprFiat("$24")
        view.hideLoading()
        view.showTotalStakedFiat()
        view.showRewardsAprFiat()
        assertTrue(texts(view).containsAll(listOf("My stake", "100 DOT", "$200", "12 DOT", "$24")))
        val stake = ViewStakingInfoBinding.bind(binding.stakeTotalStakedView)
        val rewards = ViewStakingInfoBinding.bind(binding.stakeRewardsAprView)
        assertEquals(View.VISIBLE, stake.stakingInfoExtraBlock.visibility)
        assertEquals(View.VISIBLE, rewards.stakingInfoExtraBlock.visibility)
        view.hideTotalStakeFiat()
        view.hideRewardsAprFiat()
        assertEquals(View.INVISIBLE, stake.stakingInfoExtraBlock.visibility)
        assertEquals(View.INVISIBLE, rewards.stakingInfoExtraBlock.visibility)
    }

    @Test
    fun statusTextAndClickabilityFollowEveryElectionState() {
        val view = StakeSummaryView(context)
        val binding = ViewStakeSummaryBinding.bind(view)
        val statuses = listOf(StakeSummaryView.Status.Active("Era 1"), StakeSummaryView.Status.Inactive("Era 2"),
            StakeSummaryView.Status.Waiting(0), StakeSummaryView.Status.ActiveCollator(0),
            StakeSummaryView.Status.IdleCollator(), StakeSummaryView.Status.LeavingCollator(0),
            StakeSummaryView.Status.ReadyToUnlockCollator)
        for (status in statuses) {
            view.setElectionStatus(status)
            assertEquals(context.getString(status.textRes), binding.stakeSummaryStatus.text.toString())
            assertEquals(if (status.statusClickable) View.VISIBLE else View.GONE, binding.imageView2.visibility)
        }
        view.setElectionStatus(StakeSummaryView.Status.Active("Era 3"))
        assertEquals("Era 3", binding.stakeSummaryStatusHelper.text.toString())
        var statusClicks = 0
        var stakeClicks = 0
        var moreClicks = 0
        view.setStatusClickListener { statusClicks++ }
        view.setStakeInfoClickListener { stakeClicks++ }
        view.moreActions.setOnClickListener { moreClicks++ }
        binding.statusTapZone.performClick()
        view.performClick()
        view.moreActions.performClick()
        assertEquals(listOf(1, 1, 1), listOf(statusClicks, stakeClicks, moreClicks))
    }
}
