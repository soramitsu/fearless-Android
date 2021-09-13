package jp.co.soramitsu.feature_wallet_impl.presentation.transaction.filter

import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.utils.filterToSet
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.TransactionFilter
import jp.co.soramitsu.feature_wallet_impl.presentation.WalletRouter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

class TransactionHistoryFilterViewModel(
    private val router: WalletRouter,
    private val historyFiltersProvider: HistoryFiltersProvider
) : BaseViewModel() {

    private val initialFiltersFlow = flow { emit(historyFiltersProvider.currentFilters()) }
        .share()

    val filtersEnabledMap = createFilterEnabledMap()

    private val modifiedFilters = combine(filtersEnabledMap.values) {
        historyFiltersProvider.allFilters.filterToSet {
            filtersEnabledMap.checkEnabled(it)
        }
    }.inBackground()
        .share()

    val isApplyButtonEnabled = combine(initialFiltersFlow, modifiedFilters) { initial, modified ->
        initial != modified && modified.isNotEmpty()
    }.share()

    init {
        viewModelScope.launch {
            initFromState(initialFiltersFlow.first())
        }
    }

    private fun initFromState(currentState: Set<TransactionFilter>) {
        filtersEnabledMap.forEach { (filter, checked) ->
            checked.value = filter in currentState
        }
    }

    fun resetFilter() {
        viewModelScope.launch {
            val defaultFilters = historyFiltersProvider.defaultFilters

            initFromState(defaultFilters)
        }
    }

    fun backClicked() {
        router.back()
    }

    private fun createFilterEnabledMap() = historyFiltersProvider.allFilters.associateWith { MutableStateFlow(true) }

    fun applyClicked() {
        viewModelScope.launch {
            historyFiltersProvider.setCustomFilters(modifiedFilters.first())

            router.back()
        }
    }

    private fun <T> Map<out T, MutableStateFlow<Boolean>>.checkEnabled(key: T) = get(key)?.value ?: false
}
