package jp.co.soramitsu.staking.impl.domain.recommendations

import jp.co.soramitsu.staking.impl.domain.recommendations.settings.RecommendationSettings

interface BlockCreatorRecommendator<T> {
    suspend fun recommendations(settings: RecommendationSettings<T>): List<T>
}
