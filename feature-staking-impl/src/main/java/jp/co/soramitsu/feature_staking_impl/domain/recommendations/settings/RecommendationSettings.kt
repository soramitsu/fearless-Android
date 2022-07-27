package jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings

import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.filters.BlockProducerFilters
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.filters.RecommendationPostProcessor
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.sortings.BlockProducersSorting

data class RecommendationSettings<T>(
    val alwaysEnabledFilters: List<BlockProducerFilters<T>>,
    val customEnabledFilters: List<BlockProducerFilters<T>>,
    val postProcessors: List<RecommendationPostProcessor<T>>,
    val sorting: BlockProducersSorting<T>,
    val limit: Int? = null
) {

    val allFilters = alwaysEnabledFilters + customEnabledFilters
}
