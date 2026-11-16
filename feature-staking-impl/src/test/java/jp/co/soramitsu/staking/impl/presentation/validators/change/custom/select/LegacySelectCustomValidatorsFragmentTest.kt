package jp.co.soramitsu.staking.impl.presentation.validators.change.custom.select

import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.impl.presentation.HiltFragmentHarness
import jp.co.soramitsu.staking.impl.presentation.StakingFragmentFixture
import jp.co.soramitsu.staking.impl.presentation.validators.change.custom.select.model.ContinueButtonState
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.robolectric.Shadows.shadowOf

class LegacySelectCustomValidatorsFragmentTest : StakingFragmentFixture() {
    @Test
    fun listAndSelectionControlsReflectStateAndForwardActions() {
        val vm = mockk<SelectCustomValidatorsViewModel>(relaxed = true)
        val button = MutableStateFlow(ContinueButtonState(true, "Continue with one"))
        every { vm.validatorModelsFlow } returns MutableStateFlow(listOf(validatorRow))
        every { vm.selectedTitle } returns MutableStateFlow("1 of 16")
        every { vm.buttonState } returns button
        every { vm.scoringHeader } returns MutableStateFlow("APY")
        every { vm.fillWithRecommendedEnabled } returns MutableStateFlow(true)
        every { vm.deselectAllEnabled } returns MutableStateFlow(true)
        val fragment = SelectCustomValidatorsFragment()
        val activity = HiltFragmentHarness.launch(fragment, vm)
        val root = fragment.requireView()
        assertEquals("1 of 16", root.findViewById<TextView>(R.id.selectCustomValidatorsCount).text.toString())
        assertEquals("APY", root.findViewById<TextView>(R.id.selectCustomValidatorsSorting).text.toString())
        assertEquals(listOf(validatorRow), fragment.adapter.currentList)
        assertEquals(View.GONE, root.findViewById<View>(R.id.recommendedValidatorsProgress).visibility)
        root.findViewById<View>(R.id.selectCustomValidatorsFillWithRecommended).performClick()
        root.findViewById<View>(R.id.selectCustomValidatorsDeselectAll).performClick()
        root.findViewById<View>(R.id.selectCustomValidatorsNext).performClick()
        verify { vm.fillRestWithRecommended() }
        verify { vm.deselectAll() }
        verify { vm.nextClicked() }
        val holder = fragment.adapter.onCreateViewHolder(FrameLayout(activity), 0)
        fragment.adapter.onBindViewHolder(holder, 0)
        holder.itemView.performClick()
        holder.itemView.findViewById<View>(R.id.itemValidatorInfo).performClick()
        verify { vm.validatorClicked(validatorRow) }
        verify { vm.validatorInfoClicked(validatorRow) }
        button.value = ContinueButtonState(false, "Select validators")
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(root.findViewById<View>(R.id.selectCustomValidatorsNext).isEnabled)
        activity.onBackPressedDispatcher.onBackPressed()
        verify { vm.backClicked() }
    }
}
