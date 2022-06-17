package jp.co.soramitsu.feature_staking_impl.domain.recommendations

import jp.co.soramitsu.feature_staking_api.domain.model.Validator
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.RecommendationSettings
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.filters.applyFilters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ValidatorRecommendator(val availableValidators: List<Validator>) : BlockCreatorRecommendator<Validator> {

    override suspend fun recommendations(settings: RecommendationSettings<Validator>) = withContext(Dispatchers.Default) {
        val all = availableValidators.applyFilters(settings.allFilters)
            .sortedWith(settings.sorting.comparator)

        val postprocessed = settings.postProcessors.fold(all) { acc, postProcessor ->
            postProcessor.invoke(acc)
        }

        settings.limit?.let(postprocessed::take) ?: postprocessed
    }
}
