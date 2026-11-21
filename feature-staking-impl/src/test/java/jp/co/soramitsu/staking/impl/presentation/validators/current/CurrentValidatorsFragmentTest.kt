package jp.co.soramitsu.staking.impl.presentation.validators.current

import android.graphics.Picture
import android.graphics.drawable.PictureDrawable
import android.os.Looper
import android.view.View
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.common.address.AddressModel
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.impl.presentation.HiltFragmentHarness
import jp.co.soramitsu.staking.impl.presentation.StakingFragmentFixture
import jp.co.soramitsu.staking.impl.presentation.validators.current.model.NominatedValidatorModel
import jp.co.soramitsu.staking.impl.presentation.validators.current.model.NominatedValidatorStatusModel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test
import org.robolectric.Shadows.shadowOf

class CurrentValidatorsFragmentTest : StakingFragmentFixture() {
    private val vm = mockk<CurrentValidatorsViewModel>(relaxed = true)
    private val warning = MutableStateFlow(false)
    private val group = NominatedValidatorStatusModel(null, "Elected validators")
    private val alice = NominatedValidatorModel(AddressModel("alice", PictureDrawable(Picture()), "Alice"), "10 DOT", false, false)

    @Suppress("UNCHECKED_CAST")
    private fun <T> LiveData<T>.emit(value: T) {
        (this as MutableLiveData<T>).value = value
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun launch(): CurrentValidatorsFragment {
        every { vm.currentValidatorModelsLiveData } returns MutableLiveData(LoadingState.Loading())
        every { vm.shouldShowOversubscribedNoRewardWarning } returns warning
        val fragment = CurrentValidatorsFragment()
        HiltFragmentHarness.launch(fragment, vm)
        return fragment
    }

    private fun View.visibilityOf(id: Int) = findViewById<View>(id).visibility

    @Test
    fun loadingThenLoadedValidatorsFillTheList() {
        val fragment = launch()
        val root = fragment.requireView()
        assertEquals(View.VISIBLE, root.visibilityOf(R.id.currentValidatorsProgress))
        assertEquals(View.GONE, root.visibilityOf(R.id.currentValidatorsList))
        assertEquals(View.GONE, root.visibilityOf(R.id.validatorsEmptyState))
        assertEquals(View.GONE, root.visibilityOf(R.id.currentValidatorsOversubscribedMessage))
        assertEquals(View.GONE, root.visibilityOf(R.id.payoutDivider))

        vm.currentValidatorModelsLiveData.emit(LoadingState.Loaded(listOf(group, alice)))

        assertEquals(View.GONE, root.visibilityOf(R.id.currentValidatorsProgress))
        assertEquals(View.VISIBLE, root.visibilityOf(R.id.currentValidatorsList))
        assertEquals(listOf<Any?>(group, alice), fragment.adapter.currentList)
    }

    @Test
    fun emptyResultShowsTheEmptyStateAndWarningFollowsTheViewModel() {
        val fragment = launch()
        val root = fragment.requireView()

        vm.currentValidatorModelsLiveData.emit(LoadingState.Loaded(emptyList()))
        warning.value = true
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(View.GONE, root.visibilityOf(R.id.currentValidatorsProgress))
        assertEquals(View.GONE, root.visibilityOf(R.id.currentValidatorsList))
        assertEquals(View.VISIBLE, root.visibilityOf(R.id.validatorsEmptyState))
        assertEquals(View.VISIBLE, root.visibilityOf(R.id.currentValidatorsOversubscribedMessage))
        assertEquals(View.VISIBLE, root.visibilityOf(R.id.payoutDivider))
    }

    @Test
    fun toolbarAndValidatorInfoActionsReachTheViewModel() {
        val fragment = launch()
        val toolbar = fragment.requireView().findViewById<View>(R.id.currentValidatorsToolbar)

        toolbar.findViewById<View>(jp.co.soramitsu.common.R.id.backImg).performClick()
        toolbar.findViewById<View>(jp.co.soramitsu.common.R.id.rightText).performClick()
        fragment.infoClicked(alice)

        verify { vm.backClicked() }
        verify { vm.changeClicked() }
        verify { vm.validatorInfoClicked("alice") }
    }
}
