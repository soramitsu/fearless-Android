package jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings

import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.filters.HasIdentityFilter
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.filters.NotBlockedFilter
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.filters.NotOverSubscribedFilter
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.filters.NotSlashedFilter
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.postprocessors.RemoveClusteringPostprocessor
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.sortings.APYSorting
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class RecommendationSettingsProvider(
    maximumRewardedNominators: Int,
    private val maximumValidatorsPerNominator: Int
) {

    private val alwaysEnabledFilters = listOf(
        NotBlockedFilter
    )

    private val customizableFilters = listOf(
        NotSlashedFilter,
        HasIdentityFilter,
        NotOverSubscribedFilter(maximumRewardedNominators)
    )

    private val allPostProcessors = listOf(
        RemoveClusteringPostprocessor
    )

    private val customSettingsFlow = MutableStateFlow(defaultSelectCustomSettings())

    fun createModifiedCustomValidatorsSettings(
        filterIncluder: (RecommendationFilter) -> Boolean,
        postProcessorIncluder: (RecommendationPostProcessor) -> Boolean,
        sorting: RecommendationSorting? = null
    ): RecommendationSettings {
        val current = customSettingsFlow.value

        return current.copy(
            alwaysEnabledFilters = alwaysEnabledFilters,
            customEnabledFilters = customizableFilters.filter(filterIncluder),
            postProcessors = allPostProcessors.filter(postProcessorIncluder),
            sorting = sorting ?: current.sorting
        )
    }

    fun setCustomValidatorsSettings(recommendationSettings: RecommendationSettings) {
        customSettingsFlow.value = recommendationSettings
    }

    fun observeRecommendationSettings(): Flow<RecommendationSettings> = customSettingsFlow

    fun currentSettings() = customSettingsFlow.value

    fun defaultSettings(): RecommendationSettings {
        return RecommendationSettings(
            alwaysEnabledFilters = alwaysEnabledFilters,
            customEnabledFilters = customizableFilters,
            sorting = APYSorting,
            postProcessors = allPostProcessors,
            limit = maximumValidatorsPerNominator
        )
    }

    fun defaultSelectCustomSettings() = RecommendationSettings(
        alwaysEnabledFilters = alwaysEnabledFilters,
        customEnabledFilters = customizableFilters,
        sorting = APYSorting,
        postProcessors = allPostProcessors,
        limit = null
    )
}
