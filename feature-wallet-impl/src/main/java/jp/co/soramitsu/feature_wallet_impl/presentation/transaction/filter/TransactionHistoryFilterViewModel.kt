package jp.co.soramitsu.feature_wallet_impl.presentation.transaction.filter

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.common.utils.invoke
import jp.co.soramitsu.feature_wallet_impl.presentation.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.filter.filters.ExtrinsicFilter
import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.filter.filters.HistoryFilter
import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.filter.filters.HistoryFilters
import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.filter.filters.RewardFilter
import jp.co.soramitsu.feature_wallet_impl.presentation.transaction.filter.filters.TransferFilter
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

class TransactionHistoryFilterViewModel(
    private val router: WalletRouter,
    private val historyFiltersProviderFactory: HistoryFiltersProviderFactory
) : BaseViewModel() {

    private val historyFiltersProvider by lazy {
        async { historyFiltersProviderFactory.get() }
    }

    private val initialFiltersFlow = flow { emit(historyFiltersProvider.invoke().currentFilters()) }
        .share()

    val filtersEnabledMap = createClassEnabledMap(
        RewardFilter::class.java,
        TransferFilter::class.java,
        ExtrinsicFilter::class.java
    )

    private val modifiedFilters = combine(filtersEnabledMap.values) {
        val result = historyFiltersProvider.invoke().createModifiedFilters(
            filterIncluder = { filtersEnabledMap.checkEnabled(it::class.java) }
        )
        result
    }.inBackground()
        .share()

    val isApplyButtonEnabled = combine(initialFiltersFlow, modifiedFilters) { initial, modified ->
        initial != modified && modified.filters.isNotEmpty()
    }.share()

    init {
        viewModelScope.launch {
            initFromSettings(initialFiltersFlow.first())
        }
    }

    private fun initFromSettings(currentSettings: HistoryFilters) {
        currentSettings.filters.forEach {
            filtersEnabledMap[it::class.java]?.value = true
        }
    }

    fun resetFilter() {
        viewModelScope.launch{
            val defaultFilters = historyFiltersProvider().defaultFilters()

            initFromSettings(defaultFilters)
        }
    }

    fun backClicked() {
        router.back()
    }


    private fun <T> createClassEnabledMap(vararg classes: Class<out T>) = classes.associate {
        it to MutableStateFlow(false)
    }

    fun applyClicked() {
        viewModelScope.launch {
            historyFiltersProvider.invoke().setCustomFilters(modifiedFilters.first())

            router.back()
        }
    }

    private fun <T> Map<out T, MutableStateFlow<Boolean>>.checkEnabled(key: T) = get(key)?.value ?: false

}
