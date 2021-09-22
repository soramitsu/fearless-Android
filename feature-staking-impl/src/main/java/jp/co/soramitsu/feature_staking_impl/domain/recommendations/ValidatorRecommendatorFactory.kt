package jp.co.soramitsu.feature_staking_impl.domain.recommendations

import androidx.lifecycle.Lifecycle
import jp.co.soramitsu.common.data.memory.ComputationalCache
import jp.co.soramitsu.feature_staking_api.domain.model.Validator
import jp.co.soramitsu.feature_staking_impl.data.StakingSharedState
import jp.co.soramitsu.feature_staking_impl.domain.validators.ValidatorProvider
import jp.co.soramitsu.feature_staking_impl.domain.validators.ValidatorSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val ELECTED_VALIDATORS_CACHE = "ELECTED_VALIDATORS_CACHE"

class ValidatorRecommendatorFactory(
    private val validatorProvider: ValidatorProvider,
    private val sharedState: StakingSharedState,
    private val computationalCache: ComputationalCache
) {

    suspend fun awaitValidatorLoading(lifecycle: Lifecycle) {
        loadValidators(lifecycle)
    }

    private suspend fun loadValidators(lifecycle: Lifecycle) = computationalCache.useCache(ELECTED_VALIDATORS_CACHE, lifecycle) {
        validatorProvider.getValidators(sharedState.chainId(), ValidatorSource.Elected)
    }

    suspend fun create(lifecycle: Lifecycle): ValidatorRecommendator = withContext(Dispatchers.IO) {
        val validators: List<Validator> = loadValidators(lifecycle)

        ValidatorRecommendator(validators)
    }
}
