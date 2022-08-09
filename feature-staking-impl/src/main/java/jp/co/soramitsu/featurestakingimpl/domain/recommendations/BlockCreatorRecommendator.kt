package jp.co.soramitsu.featurestakingimpl.domain.recommendations

import jp.co.soramitsu.featurestakingimpl.domain.recommendations.settings.RecommendationSettings

interface BlockCreatorRecommendator<T> {
    suspend fun recommendations(settings: RecommendationSettings<T>): List<T>
}
