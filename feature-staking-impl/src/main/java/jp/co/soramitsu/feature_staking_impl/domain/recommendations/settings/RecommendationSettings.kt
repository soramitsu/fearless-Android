package jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings

import java.util.Comparator
import jp.co.soramitsu.common.utils.Filter
import jp.co.soramitsu.feature_staking_api.domain.model.Validator

typealias RecommendationFilter = Filter<Validator>
typealias RecommendationSorting = Comparator<Validator>

class RecommendationSettings(
    val filters: List<RecommendationFilter>,
    val sorting: RecommendationSorting,
    val limit: Int? = null
)
