package jp.co.soramitsu.staking.impl.domain.recommendations

import jp.co.soramitsu.staking.api.domain.model.Validator
import jp.co.soramitsu.staking.impl.domain.recommendations.settings.RecommendationSettings
import jp.co.soramitsu.staking.impl.domain.recommendations.settings.filters.BlockProducerFilters
import jp.co.soramitsu.staking.impl.domain.recommendations.settings.filters.applyFilters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ValidatorRecommendator(val availableValidators: List<Validator>) : BlockCreatorRecommendator<Validator> {

    override suspend fun recommendations(settings: RecommendationSettings<Validator>): List<Validator> = withContext(Dispatchers.Default) {
        val notElectedValidators = availableValidators.filter { it.electedInfo == null }
        val electedValidators = availableValidators.filter { it.electedInfo != null }

        val filteredAndSortedElectedValidators = electedValidators.applyFilters(settings.allFilters)
            .sortedWith(settings.sorting.comparator)

        val allowedForNotElectedValidatorsFilters = settings.allFilters.filter { it is BlockProducerFilters.ValidatorFilter.HasIdentity || it is BlockProducerFilters.ValidatorFilter.HasBlocked }
        val filteredNotElectedValidators = notElectedValidators.filter { item ->
            allowedForNotElectedValidatorsFilters.all { filter -> filter.shouldInclude(item) }
        }

        val all = filteredAndSortedElectedValidators + filteredNotElectedValidators

        val postprocessed = settings.postProcessors.fold(all) { acc, postProcessor ->
            postProcessor.invoke(acc)
        }

        (settings.limit?.let(postprocessed::take) ?: postprocessed)

    }
}
