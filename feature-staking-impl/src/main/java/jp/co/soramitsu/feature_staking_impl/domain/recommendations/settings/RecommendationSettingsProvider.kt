package jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings

import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.filters.HasIdentityFilter
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.filters.NotOverSubscribedFilter
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.filters.NotSlashedFilter
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.sortings.APYSorting
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.sortings.CommissionSorting
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.sortings.StakeSorting
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

private const val MAX_SUBSCRIBERS = 64

private val ALL_FILTERS = listOf(
    NotSlashedFilter,
    HasIdentityFilter,
    NotOverSubscribedFilter(MAX_SUBSCRIBERS)
)

private val ALL_SORTINGS = listOf(
    APYSorting,
    StakeSorting,
    CommissionSorting
)

class RecommendationSettingsProvider {

    private val settingsFlow = MutableStateFlow(defaultSettings())

    suspend fun setRecommendationSettings(settings: RecommendationSettings) {
        settingsFlow.emit(settings)
    }

    fun observeRecommendationSettings(): Flow<RecommendationSettings> = settingsFlow

    fun getAllFilters(): List<RecommendationFilter> = ALL_FILTERS

    fun getAllSortings(): List<RecommendationSorting> = ALL_SORTINGS

    private fun defaultSettings(): RecommendationSettings {
        return RecommendationSettings(
            filters = ALL_FILTERS,
            sorting = APYSorting
        )
    }
}