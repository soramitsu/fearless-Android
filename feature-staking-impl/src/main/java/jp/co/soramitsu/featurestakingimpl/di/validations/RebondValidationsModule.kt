package jp.co.soramitsu.featurestakingimpl.di.validations

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import jp.co.soramitsu.common.validation.CompositeValidation
import jp.co.soramitsu.featurestakingimpl.domain.validations.rebond.EnoughToRebondValidation
import jp.co.soramitsu.featurestakingimpl.domain.validations.rebond.NotZeroRebondValidation
import jp.co.soramitsu.featurestakingimpl.domain.validations.rebond.RebondFeeValidation
import jp.co.soramitsu.featurestakingimpl.domain.validations.rebond.RebondValidationFailure
import jp.co.soramitsu.featurestakingimpl.domain.validations.rebond.RebondValidationSystem
import jp.co.soramitsu.featurestakingimpl.scenarios.StakingScenarioInteractor
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
class RebondValidationsModule {

    @Provides
    @Singleton
    fun provideFeeValidation() = RebondFeeValidation(
        feeExtractor = { it.fee },
        availableBalanceProducer = { it.controllerAsset.transferable },
        errorProducer = { RebondValidationFailure.CANNOT_PAY_FEE }
    )

    @Provides
    @Singleton
    fun provideNotZeroUnbondValidation() = NotZeroRebondValidation(
        amountExtractor = { it.rebondAmount },
        errorProvider = { RebondValidationFailure.ZERO_AMOUNT }
    )

    @Provides
    @Singleton
    fun provideEnoughToRebondValidation(stakingScenarioInteractor: StakingScenarioInteractor) = EnoughToRebondValidation(stakingScenarioInteractor)

    @Provides
    @Singleton
    fun provideRebondValidationSystem(
        rebondFeeValidation: RebondFeeValidation,
        notZeroRebondValidation: NotZeroRebondValidation,
        enoughToRebondValidation: EnoughToRebondValidation
    ) = RebondValidationSystem(
        CompositeValidation(
            validations = listOf(
                rebondFeeValidation,
                notZeroRebondValidation,
                enoughToRebondValidation
            )
        )
    )
}
