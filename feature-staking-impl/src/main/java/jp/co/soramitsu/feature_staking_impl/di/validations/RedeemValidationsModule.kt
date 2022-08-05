package jp.co.soramitsu.feature_staking_impl.di.validations

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import jp.co.soramitsu.common.validation.CompositeValidation
import jp.co.soramitsu.feature_staking_impl.domain.validations.reedeem.RedeemFeeValidation
import jp.co.soramitsu.feature_staking_impl.domain.validations.reedeem.RedeemValidationFailure
import jp.co.soramitsu.feature_staking_impl.domain.validations.reedeem.RedeemValidationSystem

@InstallIn(SingletonComponent::class)
@Module
class RedeemValidationsModule {

    @Provides
    fun provideFeeValidation() = RedeemFeeValidation(
        feeExtractor = { it.fee },
        availableBalanceProducer = { it.asset.transferable },
        errorProducer = { RedeemValidationFailure.CANNOT_PAY_FEES }
    )

    @Provides
    fun provideRedeemValidationSystem(
        feeValidation: RedeemFeeValidation,
    ) = RedeemValidationSystem(
        CompositeValidation(
            validations = listOf(
                feeValidation
            )
        )
    )
}
