package jp.co.soramitsu.feature_staking_impl.domain.recommendations

import jp.co.soramitsu.common.utils.applyFilters
import jp.co.soramitsu.feature_staking_api.domain.model.Validator
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.RecommendationSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ValidatorRecommendator(val availableValidators: List<Validator>): BlockCreatorRecommendator<Validator> {

    override suspend fun recommendations(settings: RecommendationSettings) = withContext(Dispatchers.Default) {
        val all = availableValidators.applyFilters(settings.allFilters)
            .sortedWith(settings.sorting)

        val postprocessed = settings.postProcessors.fold(all) { acc, postProcessor ->
            postProcessor(acc)
        }

        settings.limit?.let(postprocessed::take) ?: postprocessed
    }
}
