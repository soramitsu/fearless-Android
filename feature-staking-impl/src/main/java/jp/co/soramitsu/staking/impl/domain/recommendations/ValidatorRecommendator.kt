package jp.co.soramitsu.staking.impl.domain.recommendations

import jp.co.soramitsu.staking.api.domain.model.Validator
import jp.co.soramitsu.staking.impl.domain.recommendations.settings.RecommendationSettings
import jp.co.soramitsu.staking.impl.domain.recommendations.settings.filters.applyFilters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ValidatorRecommendator(val availableValidators: List<Validator>) : BlockCreatorRecommendator<Validator> {

    override suspend fun recommendations(settings: RecommendationSettings<Validator>): List<Validator> = withContext(Dispatchers.Default) {
        val notElectedValidators = availableValidators.filter { it.electedInfo == null }
        val electedValidators = availableValidators.filter { it.electedInfo != null }

        val filteredAndSortedElectedValidators = electedValidators.applyFilters(settings.allFilters)
            .sortedWith(settings.sorting.comparator)

        val all = filteredAndSortedElectedValidators + notElectedValidators

        val postprocessed = settings.postProcessors.fold(all) { acc, postProcessor ->
            postProcessor.invoke(acc)
        }

        (settings.limit?.let(postprocessed::take) ?: postprocessed).sortedBy { it.electedInfo == null }

    }
}
