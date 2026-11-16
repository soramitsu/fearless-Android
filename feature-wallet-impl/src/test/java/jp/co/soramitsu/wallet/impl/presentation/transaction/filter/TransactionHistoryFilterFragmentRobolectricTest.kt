package jp.co.soramitsu.wallet.impl.presentation.transaction.filter

import android.view.View
import android.widget.CompoundButton
import androidx.lifecycle.SavedStateHandle
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.wallet.impl.domain.interfaces.TransactionFilter
import jp.co.soramitsu.wallet.impl.presentation.HiltFragmentHost
import jp.co.soramitsu.wallet.impl.presentation.StubHiltActivity
import jp.co.soramitsu.wallet.impl.presentation.StubHiltApplication
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/**
 * The history filter screen shows only the offered filters, mirrors the stored choice, and applies, resets or closes.
 * The view model runs on the real main dispatcher: its filter set is computed on a background dispatcher.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = StubHiltApplication::class)
class TransactionHistoryFilterFragmentRobolectricTest {

    private val router = mockk<WalletRouter>(relaxed = true)
    private val provider = HistoryFiltersProvider()
    private var host: ActivityController<StubHiltActivity>? = null

    @After
    fun closeHost() {
        HiltFragmentHost.close(host)
    }

    @Test
    fun onlyOfferedFiltersAreShownAndAChangedChoiceIsApplied() {
        provider.setCustomFilters(setOf(TransactionFilter.TRANSFER))
        val view = hostFilters(setOf(TransactionFilter.TRANSFER, TransactionFilter.REWARD))
        val transfers = view.switch(R.id.transactionsFilterSwitchTransfers)
        val rewards = view.switch(R.id.transactionsFilterRewards)
        val apply = view.findViewById<View>(R.id.transactionFilterApplyBtn)

        assertEquals(View.GONE, view.findViewById<View>(R.id.transactionsFilterOtherTransactions).visibility)
        assertEquals(View.VISIBLE, transfers.visibility)
        assertEquals(View.VISIBLE, rewards.visibility)
        awaitUntil("stored choice shown with apply disabled") { transfers.isChecked && !rewards.isChecked && !apply.isEnabled }

        rewards.performClick()
        awaitUntil("apply enabled for a changed choice") { apply.isEnabled }
        apply.performClick()

        awaitUntil("choice stored") { provider.currentFilters() == setOf(TransactionFilter.TRANSFER, TransactionFilter.REWARD) }
        verify { router.back() }
    }

    @Test
    fun resetRestoresEveryFilterAndTheHomeButtonCloses() {
        provider.setCustomFilters(setOf(TransactionFilter.EXTRINSIC))
        val view = hostFilters(null)
        val transfers = view.switch(R.id.transactionsFilterSwitchTransfers)
        val rewards = view.switch(R.id.transactionsFilterRewards)
        val others = view.switch(R.id.transactionsFilterOtherTransactions)
        val toolbar = view.findViewById<View>(R.id.transactionsFilterToolbar)

        assertTrue(listOf(transfers, rewards, others).all { it.visibility == View.VISIBLE })
        awaitUntil("stored choice shown") { others.isChecked && !transfers.isChecked && !rewards.isChecked }

        toolbar.findViewById<View>(jp.co.soramitsu.common.R.id.rightText).performClick()
        awaitUntil("defaults restored") { transfers.isChecked && rewards.isChecked && others.isChecked }
        toolbar.findViewById<View>(jp.co.soramitsu.common.R.id.backImg).performClick()

        verify { router.back() }
        assertEquals(setOf(TransactionFilter.EXTRINSIC), provider.currentFilters())
    }

    @Test
    @Suppress("DEPRECATION")
    fun bundleListsTheFiltersToShow() {
        val all = TransactionHistoryFilterFragment.getBundle()
        val transfersOnly = TransactionHistoryFilterFragment.getBundle(setOf(TransactionFilter.TRANSFER))

        assertEquals(TransactionFilter.entries.toSet(), all.get(TransactionHistoryFilterFragment.KEY_FILTERS_TO_SHOW))
        assertEquals(setOf(TransactionFilter.TRANSFER), transfersOnly.get(TransactionHistoryFilterFragment.KEY_FILTERS_TO_SHOW))
    }

    private fun hostFilters(filtersToShow: Set<TransactionFilter>?): View {
        val handle = filtersToShow?.let { SavedStateHandle(mapOf(TransactionHistoryFilterFragment.KEY_FILTERS_TO_SHOW to it)) }
            ?: SavedStateHandle()
        val viewModel = TransactionHistoryFilterViewModel(router, provider, handle)
        val fragment = TransactionHistoryFilterFragment()
        filtersToShow?.let { fragment.arguments = TransactionHistoryFilterFragment.getBundle(it) }
        host = HiltFragmentHost.host(fragment, mapOf(TransactionHistoryFilterViewModel::class.java to viewModel))
        ShadowLooper.idleMainLooper()
        return fragment.requireView()
    }

    private fun View.switch(id: Int): CompoundButton = findViewById(id)

    /** Idles the main looper until [condition] holds; the filter set is combined on a background dispatcher. */
    private fun awaitUntil(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (!condition() && System.currentTimeMillis() < deadline) {
            ShadowLooper.idleMainLooper()
            Thread.sleep(POLL_MS)
        }
        assertTrue(what, condition())
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
        const val POLL_MS = 10L
    }
}
