package jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings

import jp.co.soramitsu.common.utils.Filter
import jp.co.soramitsu.feature_staking_api.domain.model.Validator
import java.util.Comparator
import jp.co.soramitsu.feature_staking_api.domain.model.Collator

typealias RecommendationFilter = Filter<Validator>
typealias RecommendationSorting = Comparator<Validator>
typealias RecommendationCollatorSorting = Comparator<Collator>
typealias RecommendationPostProcessor = (List<Validator>) -> List<Validator>

data class RecommendationSettings(
    val alwaysEnabledFilters: List<RecommendationFilter>,
    val customEnabledFilters: List<RecommendationFilter>,
    val postProcessors: List<RecommendationPostProcessor>,
    val sorting: RecommendationSorting,
    val limit: Int? = null
) {

    val allFilters = alwaysEnabledFilters + customEnabledFilters
}
