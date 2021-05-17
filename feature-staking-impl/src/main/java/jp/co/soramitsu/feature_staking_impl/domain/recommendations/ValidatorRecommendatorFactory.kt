package jp.co.soramitsu.feature_staking_impl.domain.recommendations

import jp.co.soramitsu.feature_staking_impl.domain.validators.ValidatorProvider
import jp.co.soramitsu.feature_staking_impl.domain.validators.ValidatorSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ValidatorRecommendatorFactory(
    private val validatorProvider: ValidatorProvider,
) {

    suspend fun create(): ValidatorRecommendator = withContext(Dispatchers.IO) {
        val validators = validatorProvider.getValidators(ValidatorSource.Elected)

        ValidatorRecommendator(validators)
    }
}
