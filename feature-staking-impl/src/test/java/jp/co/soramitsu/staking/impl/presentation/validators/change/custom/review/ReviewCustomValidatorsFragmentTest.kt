package jp.co.soramitsu.staking.impl.presentation.validators.change.custom.review

import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.common.view.ButtonState
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.impl.presentation.HiltFragmentHarness
import jp.co.soramitsu.staking.impl.presentation.StakingFragmentFixture
import jp.co.soramitsu.staking.impl.presentation.validators.ValidatorsAdapter
import jp.co.soramitsu.staking.impl.presentation.validators.change.custom.review.model.ValidatorsSelectionState
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.robolectric.Shadows.shadowOf

class ReviewCustomValidatorsFragmentTest : StakingFragmentFixture() {
    @Test
    fun reviewRowsOpenInformationAndEditModeOffersDeletion() {
        val vm = mockk<ReviewCustomValidatorsViewModel>(relaxed = true)
        val editing = MutableStateFlow(false)
        val selection = MutableStateFlow(ValidatorsSelectionState("1 of 16", "Continue", false, ButtonState.NORMAL))
        every { vm.selectedValidatorModels } returns MutableStateFlow(listOf(validatorRow))
        every { vm.selectionStateFlow } returns selection
        every { vm.isInEditMode } returns editing
        val fragment = ReviewCustomValidatorsFragment()
        val activity = HiltFragmentHarness.launch(fragment, vm)
        val root = fragment.requireView()
        val adapter = root.findViewById<RecyclerView>(R.id.reviewCustomValidatorsList).adapter as ValidatorsAdapter
        assertEquals(listOf(validatorRow), adapter.currentList)
        assertEquals("1 of 16", root.findViewById<TextView>(R.id.reviewCustomValidatorsAccounts).text.toString())
        root.findViewById<View>(R.id.reviewCustomValidatorsNext).performClick()
        verify { vm.nextClicked() }
        val holder = adapter.onCreateViewHolder(FrameLayout(activity), 0)
        adapter.onBindViewHolder(holder, 0)
        holder.itemView.performClick()
        holder.itemView.findViewById<View>(R.id.itemValidatorInfo).performClick()
        verify(exactly = 2) { vm.validatorInfoClicked(validatorRow) }
        editing.value = true
        selection.value = ValidatorsSelectionState("17 of 16", "Select up to 16", true, ButtonState.DISABLED)
        shadowOf(Looper.getMainLooper()).idle()
        adapter.onBindViewHolder(holder, 0)
        holder.itemView.findViewById<View>(R.id.itemValidatorActionIcon).performClick()
        verify { vm.deleteClicked(validatorRow) }
        assertFalse(root.findViewById<View>(R.id.reviewCustomValidatorsNext).isEnabled)
        assertEquals("17 of 16", root.findViewById<TextView>(R.id.reviewCustomValidatorsAccounts).text.toString())
        activity.onBackPressedDispatcher.onBackPressed()
        verify { vm.backClicked() }
    }
}
