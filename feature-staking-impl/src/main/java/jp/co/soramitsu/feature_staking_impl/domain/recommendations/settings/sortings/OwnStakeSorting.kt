package jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.sortings

import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.RecommendationSorting
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.notElected

object OwnStakeSorting : RecommendationSorting by Comparator.comparing({
    it.electedInfo?.ownStake ?: notElected(it.accountIdHex)
})
