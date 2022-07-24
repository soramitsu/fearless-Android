package jp.co.soramitsu.feature_staking_impl.domain.recommendations

import androidx.lifecycle.Lifecycle
import jp.co.soramitsu.common.data.memory.ComputationalCache
import jp.co.soramitsu.feature_staking_api.domain.model.Validator
import jp.co.soramitsu.feature_staking_api.data.StakingSharedState
import jp.co.soramitsu.feature_staking_impl.domain.validators.ValidatorProvider
import jp.co.soramitsu.feature_staking_impl.domain.validators.ValidatorSource
import jp.co.soramitsu.runtime.state.chain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val ELECTED_VALIDATORS_CACHE = "ELECTED_VALIDATORS_CACHE"

class ValidatorRecommendatorFactory(
    private val validatorProvider: ValidatorProvider,
    private val sharedState: StakingSharedState,
    private val computationalCache: ComputationalCache
) : BlockCreatorsRecommendatorFactory<Validator> {

    private suspend fun loadValidators(lifecycle: Lifecycle) = computationalCache.useCache(ELECTED_VALIDATORS_CACHE, lifecycle) {
        validatorProvider.getValidators(sharedState.chain(), ValidatorSource.Elected)
    }

    override suspend fun create(lifecycle: Lifecycle): ValidatorRecommendator = withContext(Dispatchers.IO) {
        val validators: List<Validator> = loadValidators(lifecycle)

        ValidatorRecommendator(validators)
    }

    override suspend fun awaitBlockCreatorsLoading(lifecycle: Lifecycle) {
        loadValidators(lifecycle)
    }
}
