package jp.co.soramitsu.staking.impl.presentation.staking.main.view

import android.view.View
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.databinding.ViewStakingInfoBinding
import jp.co.soramitsu.staking.impl.presentation.StakingViewFixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StakingInfoViewTest : StakingViewFixture() {
    @Test
    fun attributesAndSettersPopulateTheVisibleLabels() {
        val attrs = Robolectric.buildAttributeSet().addAttribute(R.attr.titleText, "Initial title")
            .addAttribute(R.attr.titleDetail, "Initial detail").addAttribute(R.attr.showTitle, "false")
            .addAttribute(R.attr.includeExtraBlock, "false").addAttribute(R.attr.startWithLoading, "true").build()
        val view = StakingInfoView(context, attrs)
        val binding = ViewStakingInfoBinding.bind(view)
        assertEquals("Initial title", binding.stakingInfoTitle.text.toString())
        assertEquals("Initial detail", binding.stakingInfoTitleDetail.text.toString())
        assertEquals(View.GONE, binding.stakingInfoTitle.visibility)
        assertEquals(View.GONE, binding.stakingInfoExtraBlock.visibility)
        assertEquals(View.VISIBLE, binding.stakingInfoBodyShimmer.visibility)
        view.setTitle("Stake")
        view.setTitleVisibility(true)
        view.setTitleDetail("This era")
        view.setBody("100 DOT")
        view.setExtraBlockValueText("$200")
        view.setExtraBlockAdditionalText("+5%")
        view.hideLoading()
        view.showWholeExtraBlock()
        assertTrue(texts(view).containsAll(listOf("Stake", "This era", "100 DOT", "$200", "+5%")))
        assertEquals(View.VISIBLE, binding.stakingInfoTitle.visibility)
        assertEquals(View.GONE, binding.stakingInfoBodyShimmer.visibility)
        assertEquals(View.VISIBLE, binding.stakingInfoBody.visibility)
    }

    @Test
    fun loadingAndOptionalValuesHaveIndependentVisibility() {
        val view = StakingInfoView(context)
        val binding = ViewStakingInfoBinding.bind(view)
        view.showLoading()
        assertEquals(View.GONE, binding.stakingInfoBody.visibility)
        assertEquals(View.VISIBLE, binding.stakingInfoExtraBlockShimmer.visibility)
        view.hideLoading()
        assertEquals(View.GONE, binding.stakingInfoExtraBlockShimmer.visibility)
        view.hideExtraBlockValue()
        assertEquals(View.GONE, binding.stakingInfoExtraBlockValue.visibility)
        view.showExtraBlockValue()
        assertEquals(View.VISIBLE, binding.stakingInfoExtraBlockValue.visibility)
        view.makeExtraBlockInvisible()
        assertEquals(View.INVISIBLE, binding.stakingInfoExtraBlock.visibility)
        view.hideWholeExtraBlock()
        assertEquals(View.GONE, binding.stakingInfoExtraBlock.visibility)
        view.showWholeExtraBlock()
        assertEquals(View.VISIBLE, binding.stakingInfoExtraBlock.visibility)
    }
}
