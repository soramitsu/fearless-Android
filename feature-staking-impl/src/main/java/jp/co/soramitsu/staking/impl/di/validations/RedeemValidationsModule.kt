package jp.co.soramitsu.staking.impl.di.validations

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import jp.co.soramitsu.common.validation.CompositeValidation
import jp.co.soramitsu.staking.impl.domain.validations.reedeem.RedeemFeeValidation
import jp.co.soramitsu.staking.impl.domain.validations.reedeem.RedeemValidationFailure
import jp.co.soramitsu.staking.impl.domain.validations.reedeem.RedeemValidationSystem
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
class RedeemValidationsModule {

    @Provides
    @Singleton
    fun provideFeeValidation() = RedeemFeeValidation(
        feeExtractor = { it.fee },
        availableBalanceProducer = { it.asset.transferable },
        errorProducer = { RedeemValidationFailure.CANNOT_PAY_FEES }
    )

    @Provides
    @Singleton
    fun provideRedeemValidationSystem(
        feeValidation: RedeemFeeValidation
    ) = RedeemValidationSystem(
        CompositeValidation(
            validations = listOf(
                feeValidation
            )
        )
    )
}
