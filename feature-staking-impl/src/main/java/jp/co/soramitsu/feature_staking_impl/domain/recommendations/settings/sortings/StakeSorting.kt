package jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.sortings

import jp.co.soramitsu.feature_staking_api.domain.model.Validator
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.RecommendationSorting

object StakeSorting : RecommendationSorting by Comparator.comparing(Validator::totalStake)
