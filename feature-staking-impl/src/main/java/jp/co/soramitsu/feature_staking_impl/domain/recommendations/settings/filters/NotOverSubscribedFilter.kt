package jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.filters

import jp.co.soramitsu.feature_staking_api.domain.model.Validator
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.RecommendationFilter

class NotOverSubscribedFilter(
    private val maxSubscribers: Int
) : RecommendationFilter {

    override fun shouldInclude(model: Validator): Boolean {
        val electedInfo = model.electedInfo

        return if (electedInfo != null) {
            electedInfo.nominatorStakes.size < maxSubscribers
        } else {
            throw IllegalStateException("Filtering validator ${model.accountIdHex} with no prefs")
        }
    }
}
