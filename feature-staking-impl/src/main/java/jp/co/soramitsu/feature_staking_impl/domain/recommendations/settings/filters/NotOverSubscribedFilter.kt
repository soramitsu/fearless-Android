package jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.filters

import jp.co.soramitsu.feature_staking_api.domain.model.Validator
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.RecommendationFilter

class NotOverSubscribedFilter(
    private val maxSubscribers: Int
) : RecommendationFilter {

    override fun shouldInclude(model: Validator): Boolean {
        return model.nominatorStakes.size < maxSubscribers
    }
}
