package jp.co.soramitsu.feature_staking_impl.domain.recommendations

import jp.co.soramitsu.common.utils.applyFilters
import jp.co.soramitsu.feature_staking_api.domain.model.Validator
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.RecommendationSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ValidatorRecommendator(
    private val allRecommendedValidators: List<Validator>,
    private val recommendationLimit: Int
) {

    suspend fun recommendations(settings: RecommendationSettings) = withContext(Dispatchers.Default) {
        allRecommendedValidators.applyFilters(settings.filters)
            .sortedWith(settings.sorting)
            .take(recommendationLimit)
    }
}