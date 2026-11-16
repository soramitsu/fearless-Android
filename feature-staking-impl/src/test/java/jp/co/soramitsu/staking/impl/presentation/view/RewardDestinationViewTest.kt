package jp.co.soramitsu.staking.impl.presentation.view

import android.view.View
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import jp.co.soramitsu.common.compose.theme.fw.FwRestyle
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.databinding.ViewPayoutTargetBinding
import jp.co.soramitsu.staking.impl.presentation.StakingViewFixture
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RewardDestinationViewTest : StakingViewFixture() {
    @After
    fun restorePalette() { unmockkObject(FwRestyle) }

    @Test
    fun checkedAndEnabledAttributesControlTheChoiceIndicator() {
        val attrs = Robolectric.buildAttributeSet().addAttribute(android.R.attr.checked, "true")
            .addAttribute(R.attr.targetName, "Restake").addAttribute(R.attr.enabled, "false").build()
        val view = RewardDestinationView(context, attrs)
        val binding = ViewPayoutTargetBinding.bind(view)
        assertTrue(view.isChecked)
        assertEquals("Restake", binding.payoutTargetName.text.toString())
        assertEquals(View.GONE, binding.payoutTargetCheck.visibility)
        assertEquals(View.VISIBLE, binding.payoutTargetCheckedDisabled.visibility)
        view.isEnabled = true
        assertEquals(View.VISIBLE, binding.payoutTargetCheck.visibility)
        assertEquals(View.GONE, binding.payoutTargetCheckedDisabled.visibility)
        view.isChecked = false
        assertFalse(binding.payoutTargetCheck.isChecked)
        view.toggle()
        assertTrue(view.isChecked)
        assertTrue(android.R.attr.state_checked in view.drawableState)
    }

    @Test
    fun amountAndFiatLabelsRenderInBothPalettes() {
        mockkObject(FwRestyle)
        for (enabled in listOf(true, false)) {
            every { FwRestyle.isEnabled(any()) } returns enabled
            val view = RewardDestinationView(context)
            val binding = ViewPayoutTargetBinding.bind(view)
            view.setName("Payout")
            view.setTokenAmount("3 DOT")
            view.setPercentageGain("12%")
            view.setFiatAmount("$6")
            assertTrue(texts(view).containsAll(listOf("Payout", "3 DOT", "12%", "$6")))
            assertEquals(View.VISIBLE, binding.payoutTargetAmountFiat.visibility)
            view.setFiatAmount(null)
            assertEquals(View.GONE, binding.payoutTargetAmountFiat.visibility)
        }
    }
}
