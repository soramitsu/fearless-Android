package jp.co.soramitsu.feature_staking_impl.domain.recommendations

import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.RecommendationSettings

interface BlockCreatorRecommendator<T>{
    suspend fun recommendations(settings: RecommendationSettings): List<T>
}
