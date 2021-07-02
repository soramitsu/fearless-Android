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
    val maximumValidatorsPerNominator: Int
) {

    private val alwaysEnabledFilters = listOf(
        NotBlockedFilter
    )

    private val customizableFilters = listOf(
        NotSlashedFilter,
        HasIdentityFilter,
        NotOverSubscribedFilter(maximumRewardedNominators)
    )

    private val allFilters = alwaysEnabledFilters + customizableFilters

    private val allPostProcessors = listOf(
        RemoveClusteringPostprocessor
    )

    private val customSettingsFlow = MutableStateFlow(defaultSelectCustomSettings())

    fun createModifiedCustomValidatorsSettings(
        filterIncluder: ((RecommendationFilter) -> Boolean)? = null,
        postProcessorIncluder: ((RecommendationPostProcessor) -> Boolean)? = null,
        sorting: RecommendationSorting? = null
    ): RecommendationSettings {
        val current = customSettingsFlow.value

        val filters = filterIncluder?.let { alwaysEnabledFilters + customizableFilters.filter(it) }
            ?: current.filters

        val postProcessors = postProcessorIncluder?.let { allPostProcessors.filter(it) }
            ?: current.postProcessors

        return current.copy(
            filters = filters,
            postProcessors = postProcessors,
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
