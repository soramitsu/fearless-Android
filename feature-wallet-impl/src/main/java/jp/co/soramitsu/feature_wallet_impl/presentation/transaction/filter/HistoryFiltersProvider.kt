package jp.co.soramitsu.feature_wallet_impl.presentation.transaction.filter

import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.filter.filters.ExtrinsicFilter
import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.filter.filters.HistoryFilter
import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.filter.filters.HistoryFilters
import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.filter.filters.RewardFilter
import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.filter.filters.TransferFilter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class HistoryFiltersProvider {
    private val customizableFilters: List<HistoryFilter> = listOf(
        RewardFilter,
        TransferFilter,
        ExtrinsicFilter
    )

    private val customFiltersFlow = MutableStateFlow(defaultFilters())

    fun currentFilters() = customFiltersFlow.value

    fun createModifiedFilters(
        filterIncluder: (HistoryFilter) -> Boolean
    ): HistoryFilters {
        val current = customFiltersFlow.value
        return current.copy(
            filters = customizableFilters.filter(filterIncluder)
        )
    }

    fun observeFilters(): Flow<HistoryFilters> = customFiltersFlow

    fun setCustomFilters(filters: HistoryFilters) {
        customFiltersFlow.value = filters
    }

    fun defaultFilters() = HistoryFilters(
        filters = customizableFilters
    )
}
