package jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.sortings

import jp.co.soramitsu.feature_staking_api.domain.model.Validator
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.RecommendationSorting
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.notElected
import java.util.Comparator

object APYSorting : RecommendationSorting by Comparator.comparing({ validator: Validator ->
    validator.electedInfo?.apy ?: notElected(validator.accountIdHex)
}).reversed()
