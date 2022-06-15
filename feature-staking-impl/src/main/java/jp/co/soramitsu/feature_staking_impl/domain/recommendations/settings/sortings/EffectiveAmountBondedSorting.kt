package jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.sortings

import jp.co.soramitsu.feature_staking_api.domain.model.Collator
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.RecommendationCollatorSorting

object EffectiveAmountBondedSorting : RecommendationCollatorSorting by Comparator.comparing({ collator: Collator ->
    collator.totalCounted
}).reversed()
