package jp.co.soramitsu.feature_staking_impl.domain.recommendations

import androidx.lifecycle.Lifecycle
import jp.co.soramitsu.common.data.memory.ComputationalCache
import jp.co.soramitsu.feature_staking_api.domain.model.Collator
import jp.co.soramitsu.feature_staking_api.data.StakingSharedState
import jp.co.soramitsu.feature_staking_impl.domain.validators.CollatorProvider
import jp.co.soramitsu.runtime.state.chain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val ELECTED_COLLATORS_CACHE = "ELECTED_COLLATORS_CACHE"

class CollatorRecommendatorFactory(
    private val collatorProvider: CollatorProvider,
    private val sharedState: StakingSharedState,
    private val computationalCache: ComputationalCache
) : BlockCreatorsRecommendatorFactory<Collator> {

    private suspend fun loadCollators(lifecycle: Lifecycle) = computationalCache.useCache(ELECTED_COLLATORS_CACHE, lifecycle) {
        collatorProvider.getCollators(sharedState.chain())
    }

    override suspend fun create(lifecycle: Lifecycle): CollatorRecommendator = withContext(Dispatchers.IO) {
        val collators: List<Collator> = loadCollators(lifecycle).values.toList()
        CollatorRecommendator(collators)
    }

    override suspend fun awaitBlockCreatorsLoading(lifecycle: Lifecycle) {
        loadCollators(lifecycle)
    }
}
