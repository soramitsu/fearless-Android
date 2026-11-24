package jp.co.soramitsu.staking.impl.presentation.payouts.list

import android.os.Looper
import android.view.View
import android.widget.TextView
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.impl.presentation.HiltFragmentHarness
import jp.co.soramitsu.staking.impl.presentation.StakingFragmentFixture
import jp.co.soramitsu.staking.impl.presentation.payouts.list.model.PendingPayoutModel
import jp.co.soramitsu.staking.impl.presentation.payouts.list.model.PendingPayoutsStatisticsModel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test
import org.robolectric.Shadows.shadowOf

class PayoutsListFragmentTest : StakingFragmentFixture() {
    private val vm = mockk<PayoutsListViewModel>(relaxed = true)
    private val state = MutableStateFlow<LoadingState<PendingPayoutsStatisticsModel>>(LoadingState.Loading())
    private val payout = PendingPayoutModel("Alice", 86_400_000L, System.currentTimeMillis(), R.color.green, "10 DOT", "$20")

    private fun launch(): PayoutsListFragment {
        every { vm.payoutsStatisticsState } returns state
        val fragment = PayoutsListFragment()
        HiltFragmentHarness.launch(fragment, vm)
        return fragment
    }

    private fun View.visibilityOf(id: Int) = findViewById<View>(id).visibility

    private fun show(model: PendingPayoutsStatisticsModel) {
        state.value = LoadingState.Loaded(model)
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun loadedPayoutsFillTheListAndPayoutAllButton() {
        val fragment = launch()
        val root = fragment.requireView()
        assertEquals(View.VISIBLE, root.visibilityOf(R.id.payoutsListProgress))

        show(PendingPayoutsStatisticsModel(listOf(payout), "Payout all (10 DOT)", placeholderVisible = false))

        assertEquals(View.GONE, root.visibilityOf(R.id.payoutsListProgress))
        assertEquals(View.VISIBLE, root.visibilityOf(R.id.payoutListContentGroup))
        assertEquals(View.GONE, root.visibilityOf(R.id.payoutsPlaceholderGroup))
        assertEquals("Payout all (10 DOT)", root.findViewById<TextView>(R.id.payoutsListAll).text.toString())
        assertEquals(listOf(payout), fragment.adapter.currentList)
    }

    @Test
    fun emptyPayoutsShowThePlaceholder() {
        val fragment = launch()
        val root = fragment.requireView()

        show(PendingPayoutsStatisticsModel(emptyList(), "Payout all", placeholderVisible = true))

        assertEquals(View.GONE, root.visibilityOf(R.id.payoutsListProgress))
        assertEquals(View.GONE, root.visibilityOf(R.id.payoutListContentGroup))
        assertEquals(View.VISIBLE, root.visibilityOf(R.id.payoutsPlaceholderGroup))
    }

    @Test
    fun buttonsBackAndRowClicksReachTheViewModel() {
        val fragment = launch()
        val root = fragment.requireView()

        root.findViewById<View>(R.id.payoutsListAll).performClick()
        root.findViewById<View>(R.id.payoutsListToolbar).findViewById<View>(jp.co.soramitsu.common.R.id.backImg).performClick()
        fragment.requireActivity().onBackPressedDispatcher.onBackPressed()
        fragment.payoutClicked(2)

        verify { vm.payoutAllClicked() }
        verify(exactly = 2) { vm.backClicked() }
        verify { vm.payoutClicked(2) }
    }
}
