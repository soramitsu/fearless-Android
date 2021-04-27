package jp.co.soramitsu.feature_staking_impl.di.validations

import dagger.Module
import dagger.Provides
import jp.co.soramitsu.common.di.scope.FeatureScope
import jp.co.soramitsu.feature_staking_impl.domain.validations.unbond.UnbondFeeValidation
import jp.co.soramitsu.feature_staking_impl.domain.validations.unbond.UnbondValidationFailure

@Module
class UnbondValidationsModule {

    @FeatureScope
    @Provides
    fun provideFeeValidation() = UnbondFeeValidation(
        feeExtractor = { it.fee },
        availableBalanceProducer = { it.available },
        errorProducer = { UnbondValidationFailure.CannotPayFees }
    )
}
