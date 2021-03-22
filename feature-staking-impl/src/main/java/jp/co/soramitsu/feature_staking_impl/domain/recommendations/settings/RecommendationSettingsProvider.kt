package jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings

import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.filters.HasIdentityFilter
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.filters.NotOverSubscribedFilter
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.filters.NotSlashedFilter
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.sortings.APYSorting
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.sortings.CommissionSorting
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.sortings.StakeSorting
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class RecommendationSettingsProvider(
    maximumRewardedNominators: Int,
    val maximumValidatorsPerNominator: Int
) {

    private val allFilters = listOf(
        NotSlashedFilter,
        HasIdentityFilter,
        NotOverSubscribedFilter(maximumRewardedNominators)
    )

    private val allSortings = listOf(
        APYSorting,
        StakeSorting,
        CommissionSorting
    )

    private val settingsFlow = MutableStateFlow(defaultSettings())

    suspend fun setRecommendationSettings(settings: RecommendationSettings) {
        settingsFlow.emit(settings)
    }

    fun observeRecommendationSettings(): Flow<RecommendationSettings> = settingsFlow

    fun getAllFilters(): List<RecommendationFilter> = allFilters

    fun getAllSortings(): List<RecommendationSorting> = allSortings

    fun defaultSettings(): RecommendationSettings {
        return RecommendationSettings(
            filters = allFilters,
            sorting = APYSorting,
            limit = maximumValidatorsPerNominator
        )
    }
}
