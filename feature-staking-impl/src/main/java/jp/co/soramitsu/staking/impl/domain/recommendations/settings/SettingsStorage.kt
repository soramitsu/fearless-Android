package jp.co.soramitsu.staking.impl.domain.recommendations.settings

import jp.co.soramitsu.staking.impl.domain.recommendations.settings.filters.Filters
import jp.co.soramitsu.staking.impl.domain.recommendations.settings.filters.Sorting
import jp.co.soramitsu.staking.impl.domain.recommendations.settings.filters.toFiltersSchema
import jp.co.soramitsu.staking.impl.domain.recommendations.settings.filters.toSortingSchema
import jp.co.soramitsu.staking.impl.presentation.validators.change.custom.settings.SettingsSchema
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

class SettingsStorage {

    private val defaultSelectedFilters: MutableSet<Filters> = mutableSetOf(
        Filters.HavingOnChainIdentity,
        Filters.NotOverSubscribed
    )
    private val defaultSelectedSorting: Sorting = Sorting.EstimatedRewards

    private val selectedFilters: MutableStateFlow<Set<Filters>> =
        MutableStateFlow(defaultSelectedFilters)
    private val selectedSorting: MutableStateFlow<Sorting> =
        MutableStateFlow(defaultSelectedSorting)

    var quickFilters: Set<Filters> = setOf()

    val currentFiltersSet: MutableStateFlow<Set<Filters>> = MutableStateFlow(setOf())
    val currentSortingSet: MutableStateFlow<Set<Sorting>> = MutableStateFlow(setOf())

    val schema: Flow<SettingsSchema> =
        combine(
            currentFiltersSet,
            currentSortingSet,
            selectedFilters,
            selectedSorting
        ) { filters, sorting, selectedFilters, selectedSorting ->
            SettingsSchema(
                filters.toFiltersSchema(selectedFilters),
                sorting.toSortingSchema(selectedSorting)
            )
        }

    fun setDefaultSelectedFilters(filters: Set<Filters>) {
        selectedFilters.value = filters
    }

    fun resetFilters() {
        selectedFilters.value = defaultSelectedFilters
    }

    fun resetSorting() {
        selectedSorting.value = defaultSelectedSorting
    }

    fun filterSelected(filter: Filters) {
        selectedFilters.value =
            if (filter in selectedFilters.value) {
                selectedFilters.value.minus(filter)
            } else {
                selectedFilters.value.plus(filter)
            }
    }

    fun sortingSelected(sorting: Sorting) {
        if (sorting == selectedSorting.value) {
            selectedSorting.value = defaultSelectedSorting
        } else {
            selectedSorting.value = sorting
        }
    }
}
