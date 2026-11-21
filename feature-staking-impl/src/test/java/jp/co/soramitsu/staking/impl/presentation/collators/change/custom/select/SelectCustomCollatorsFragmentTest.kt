package jp.co.soramitsu.staking.impl.presentation.collators.change.custom.select

import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import androidx.lifecycle.MutableLiveData
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.impl.presentation.HiltFragmentHarness
import jp.co.soramitsu.staking.impl.presentation.StakingFragmentFixture
import jp.co.soramitsu.staking.impl.presentation.validators.change.CollatorModel
import jp.co.soramitsu.staking.impl.presentation.validators.change.custom.select.model.ContinueButtonState
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.robolectric.Shadows.shadowOf

class SelectCustomCollatorsFragmentTest : StakingFragmentFixture() {
    @Test
    fun loadingBecomesASelectableListWithQuickFilters() {
        val vm = mockk<SelectCustomCollatorsViewModel>(relaxed = true)
        val rows = MutableStateFlow<LoadingState<List<CollatorModel>>>(LoadingState.Loading())
        val identity = MutableLiveData(false)
        every { vm.collatorModelsFlow } returns rows
        every { vm.selectedTitle } returns MutableStateFlow("1 collator")
        every { vm.buttonState } returns MutableStateFlow(ContinueButtonState(true, "Choose Alice"))
        every { vm.scoringHeader } returns MutableStateFlow("APR")
        every { vm.clearFiltersEnabled } returns MutableStateFlow(true)
        every { vm.identityFilterEnabled } returns identity
        every { vm.minimumBondFilterEnabled } returns MutableLiveData(true)
        val fragment = SelectCustomCollatorsFragment()
        val activity = HiltFragmentHarness.launch(fragment, vm)
        val root = fragment.requireView()
        assertEquals(View.VISIBLE, root.findViewById<View>(R.id.recommendedValidatorsProgress).visibility)
        assertEquals(View.GONE, root.findViewById<View>(R.id.selectCustomValidatorsList).visibility)
        rows.value = LoadingState.Loaded(listOf(collatorRow))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf(collatorRow), fragment.adapter.currentList)
        assertEquals(View.VISIBLE, root.findViewById<View>(R.id.selectCustomValidatorsNext).visibility)
        assertEquals(View.GONE, root.findViewById<View>(R.id.recommendedValidatorsProgress).visibility)
        identity.value = true
        assertNotNull(root.findViewById<View>(R.id.selectCustomCollatorsOnChainIdentity).background)
        root.findViewById<View>(R.id.selectCustomCollatorsOnChainIdentity).performClick()
        root.findViewById<View>(R.id.selectCustomCollatorsRelevantBond).performClick()
        root.findViewById<View>(R.id.selectCustomValidatorsNext).performClick()
        root.findViewById<View>(R.id.selectCustomValidatorsClearFilters).performClick()
        root.findViewById<View>(R.id.selectCustomValidatorsDeselectAll).performClick()
        verify { vm.havingOnChainIdentityFilterClicked() }
        verify { vm.relevantBondFilterCLicked() }
        verify { vm.nextClicked() }
        verify { vm.clearFilters() }
        verify { vm.deselectAll() }
        val holder = fragment.adapter.onCreateViewHolder(FrameLayout(activity), 0)
        fragment.adapter.onBindViewHolder(holder, 0)
        holder.itemView.performClick()
        holder.itemView.findViewById<View>(R.id.itemValidatorInfo).performClick()
        verify { vm.collatorClicked(collatorRow) }
        verify { vm.collatorInfoClicked(collatorRow) }
        activity.onBackPressedDispatcher.onBackPressed()
        verify { vm.backClicked() }
    }
}
