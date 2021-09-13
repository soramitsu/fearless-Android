package jp.co.soramitsu.feature_wallet_impl.presentation.transaction.filter

import jp.co.soramitsu.feature_wallet_api.domain.interfaces.TransactionFilter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class HistoryFiltersProvider {
    val allFilters = TransactionFilter.values().toSet()

    val defaultFilters = allFilters

    private val customFiltersFlow = MutableStateFlow(defaultFilters)

    fun currentFilters() = customFiltersFlow.value

    fun filtersFlow(): Flow<Set<TransactionFilter>> = customFiltersFlow

    fun setCustomFilters(filters: Set<TransactionFilter>) {
        customFiltersFlow.value = filters
    }
}
