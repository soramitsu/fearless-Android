package jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings

import jp.co.soramitsu.common.utils.Filter
import jp.co.soramitsu.feature_staking_api.domain.model.Validator
import java.util.Comparator

typealias RecommendationFilter = Filter<Validator>
typealias RecommendationSorting = Comparator<Validator>

class RecommendationSettings(
    val filters: List<RecommendationFilter>,
    val sorting: RecommendationSorting,
    val limit: Int? = null
)