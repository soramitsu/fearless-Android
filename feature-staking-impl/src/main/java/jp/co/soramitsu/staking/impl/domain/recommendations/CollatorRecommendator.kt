package jp.co.soramitsu.staking.impl.domain.recommendations

import java.math.BigInteger
import jp.co.soramitsu.staking.api.domain.model.Collator
import jp.co.soramitsu.staking.impl.domain.recommendations.settings.RecommendationSettings
import jp.co.soramitsu.staking.impl.domain.recommendations.settings.filters.applyFilters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CollatorRecommendator(val availableCollators: List<Collator>) : BlockCreatorRecommendator<Collator> {

    override suspend fun recommendations(settings: RecommendationSettings<Collator>) = withContext(Dispatchers.Default) {
        val all = availableCollators
            .applyFilters(settings.allFilters)
            .sortedWith(settings.sorting.comparator)

        settings.limit?.let(all::take) ?: all
    }

    suspend fun suggestedCollators(userInputAmount: BigInteger): List<Collator> = withContext(Dispatchers.Default) {
        availableCollators.filter {
            it.lowestTopDelegationAmount < userInputAmount
        }
    }
}
