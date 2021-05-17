package jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.filters

import jp.co.soramitsu.feature_staking_api.domain.model.Validator
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.RecommendationFilter

object NotBlockedFilter : RecommendationFilter {

    override fun shouldInclude(model: Validator) = model.prefs?.blocked?.not() ?: false
}
