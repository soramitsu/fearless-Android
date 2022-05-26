package jp.co.soramitsu.feature_staking_impl.domain.recommendations

import jp.co.soramitsu.feature_staking_api.domain.model.Collator
import jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings.RecommendationSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CollatorRecommendator(val availableCollators: List<Collator>) {

    suspend fun recommendations(settings: RecommendationSettings) = withContext(Dispatchers.Default) {
        val all = availableCollators
//            .applyFilters(settings.allFilters)
//            .sortedWith(settings.sorting)

//        val postprocessed = settings.postProcessors.fold(all) { acc, postProcessor ->
//            postProcessor(acc)
//        }
        val postprocessed = all

        settings.limit?.let(postprocessed::take) ?: postprocessed
    }
}
