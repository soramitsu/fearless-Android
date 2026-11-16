package jp.co.soramitsu.wallet.impl.presentation.transaction.filter

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.wallet.impl.awaitFirst
import jp.co.soramitsu.wallet.impl.domain.interfaces.TransactionFilter
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TransactionHistoryFilterViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    private val router = mockk<WalletRouter>(relaxed = true)
    private val provider = HistoryFiltersProvider()

    @Test
    fun changingAFilterEnablesApplyAndStoresTheChoice() {
        provider.setCustomFilters(setOf(TransactionFilter.TRANSFER, TransactionFilter.REWARD))
        val viewModel = TransactionHistoryFilterViewModel(router, provider, SavedStateHandle())

        assertEquals(TransactionFilter.entries.toSet(), viewModel.usedFilters)
        assertFalse(viewModel.filtersEnabledMap.getValue(TransactionFilter.EXTRINSIC).value)
        viewModel.filtersEnabledMap.getValue(TransactionFilter.EXTRINSIC).value = true
        assertTrue(viewModel.isApplyButtonEnabled.awaitFirst { it })

        viewModel.applyClicked()
        provider.filtersFlow().awaitFirst { it == TransactionFilter.entries.toSet() }
        verify { router.back() }
    }

    @Test
    fun resetRestoresTheDefaultsAndAnEmptyChoiceCannotBeApplied() {
        val viewModel = TransactionHistoryFilterViewModel(
            router, provider, SavedStateHandle(mapOf(TransactionHistoryFilterFragment.KEY_FILTERS_TO_SHOW to setOf(TransactionFilter.TRANSFER)))
        )

        assertEquals(setOf(TransactionFilter.TRANSFER), viewModel.usedFilters)
        viewModel.filtersEnabledMap.getValue(TransactionFilter.TRANSFER).value = false
        assertFalse(viewModel.isApplyButtonEnabled.awaitFirst { true })
        viewModel.resetFilter()
        viewModel.backClicked()

        assertTrue(viewModel.filtersEnabledMap.getValue(TransactionFilter.TRANSFER).value)
        verify { router.back() }
        assertEquals(provider.allFilters, provider.defaultFilters)
    }
}
