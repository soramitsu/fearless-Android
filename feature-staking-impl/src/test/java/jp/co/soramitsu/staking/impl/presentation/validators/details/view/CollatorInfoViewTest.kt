package jp.co.soramitsu.staking.impl.presentation.validators.details.view

import android.view.View
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.databinding.ViewCollatorInfoBinding
import jp.co.soramitsu.staking.impl.presentation.StakingViewFixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CollatorInfoViewTest : StakingViewFixture() {
    @Test
    fun rendersBondingRewardAndDelegationValuesAndForwardsStakeClicks() {
        val view = CollatorInfoView(context)
        val binding = ViewCollatorInfoBinding.bind(view)
        view.setMinBond("5 MOVR")
        view.setSelfBonded("10 MOVR")
        view.setEffectiveAmountBonded("90 MOVR")
        view.setTotalStakeValue("100 MOVR")
        view.setTotalStakeValueFiat("$200")
        view.setDelegationsCount("8")
        view.setEstimatedRewardApr("12%")
        view.setStatus("Active", R.color.green)
        assertTrue(texts(view).containsAll(listOf("5 MOVR", "10 MOVR", "90 MOVR", "100 MOVR", "$200", "8", "12%", "Active")))
        var clicks = 0
        view.setTotalStakeClickListener { clicks++ }
        binding.collatorTotalStakeView.performClick()
        assertEquals(1, clicks)
        view.setTotalStakeValue(null)
        assertEquals(View.GONE, binding.collatorTotalStakeView.visibility)
        view.hideActiveStakeFields()
        assertEquals(View.GONE, binding.collatorEstimatedReward.visibility)
        view.showActiveStakeFields()
        assertEquals(View.VISIBLE, binding.collatorTotalStakeView.visibility)
        assertEquals(View.VISIBLE, binding.collatorEstimatedReward.visibility)
    }
}
