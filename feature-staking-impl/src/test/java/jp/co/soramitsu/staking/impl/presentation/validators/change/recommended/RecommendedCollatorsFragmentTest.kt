package jp.co.soramitsu.staking.impl.presentation.validators.change.recommended

import android.view.View
import android.widget.FrameLayout
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.impl.presentation.HiltFragmentHarness
import jp.co.soramitsu.staking.impl.presentation.StakingFragmentFixture
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommendedCollatorsFragmentTest : StakingFragmentFixture() {
    @Test
    fun recommendationsEnableContinueAndForwardInformationActions() {
        val vm = mockk<RecommendedCollatorsViewModel>(relaxed = true)
        every { vm.recommendedCollatorModels } returns MutableStateFlow(LoadingState.Loaded(listOf(collatorRow)))
        every { vm.selectedTitle } returns MutableStateFlow("1 available")
        every { vm.toolbarTitle } returns "Suggested collators"
        val fragment = RecommendedCollatorsFragment()
        val activity = HiltFragmentHarness.launch(fragment, vm)
        val root = fragment.requireView()
        assertEquals(listOf(collatorRow), fragment.adapter.currentList)
        assertEquals(View.GONE, root.findViewById<View>(R.id.recommendedValidatorsProgress).visibility)
        assertTrue(root.findViewById<View>(R.id.recommendedValidatorsNext).isEnabled)
        root.findViewById<View>(R.id.recommendedValidatorsNext).performClick()
        verify { vm.nextClicked() }
        val holder = fragment.adapter.onCreateViewHolder(FrameLayout(activity), 0)
        fragment.adapter.onBindViewHolder(holder, 0)
        holder.itemView.findViewById<View>(R.id.itemValidatorInfo).performClick()
        verify { vm.collatorInfoClicked(collatorRow) }
        holder.itemView.performClick()
        verify { vm.collatorClicked(collatorRow) }
        activity.onBackPressedDispatcher.onBackPressed()
        verify { vm.backClicked() }
    }

    @Test
    fun emptyRecommendationsDisableContinue() {
        val vm = mockk<RecommendedCollatorsViewModel>(relaxed = true)
        every { vm.recommendedCollatorModels } returns MutableStateFlow(LoadingState.Loaded(emptyList()))
        every { vm.selectedTitle } returns MutableStateFlow("0 available")
        every { vm.toolbarTitle } returns "Suggested collators"
        val fragment = RecommendedCollatorsFragment()
        HiltFragmentHarness.launch(fragment, vm)
        assertFalse(fragment.requireView().findViewById<View>(R.id.recommendedValidatorsNext).isEnabled)
        assertTrue(fragment.adapter.currentList.isEmpty())
    }
}
