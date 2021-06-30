package jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings

import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.filters.HasIdentityFilter
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.filters.NotBlockedFilter
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.filters.NotOverSubscribedFilter
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.filters.NotSlashedFilter
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.postprocessors.RemoveClusteringPostprocessor
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.sortings.APYSorting
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.sortings.OwnStakeSorting
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.sortings.TotalStakeSorting
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class RecommendationSettingsProvider(
    maximumRewardedNominators: Int,
    val maximumValidatorsPerNominator: Int
) {

    private val allFilters = listOf(
        NotSlashedFilter,
        HasIdentityFilter,
        NotBlockedFilter,
        NotOverSubscribedFilter(maximumRewardedNominators)
    )

    val allSortings = listOf(
        APYSorting,
        TotalStakeSorting,
        OwnStakeSorting
    )

    private val allPostProcessors = listOf(
        RemoveClusteringPostprocessor
    )

    private val customSettingsFlow = MutableStateFlow(defaultSelectCustomSettings())

    suspend fun setRecommendationSettings(settings: RecommendationSettings) {
        customSettingsFlow.emit(settings)
    }

    fun observeRecommendationSettings(): Flow<RecommendationSettings> = customSettingsFlow

    fun defaultSettings(): RecommendationSettings {
        return RecommendationSettings(
            filters = allFilters,
            sorting = APYSorting,
            postProcessors = allPostProcessors,
            limit = maximumValidatorsPerNominator
        )
    }

    fun defaultSelectCustomSettings() = RecommendationSettings(
        filters = allFilters,
        sorting = APYSorting,
        postProcessors = allPostProcessors,
        limit = null
    )
}
