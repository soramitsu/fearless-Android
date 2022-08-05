package jp.co.soramitsu.feature_staking_impl.di.validations

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import jp.co.soramitsu.common.validation.CompositeValidation
import jp.co.soramitsu.feature_staking_impl.domain.validations.rebond.EnoughToRebondValidation
import jp.co.soramitsu.feature_staking_impl.domain.validations.rebond.NotZeroRebondValidation
import jp.co.soramitsu.feature_staking_impl.domain.validations.rebond.RebondFeeValidation
import jp.co.soramitsu.feature_staking_impl.domain.validations.rebond.RebondValidationFailure
import jp.co.soramitsu.feature_staking_impl.domain.validations.rebond.RebondValidationSystem
import jp.co.soramitsu.feature_staking_impl.scenarios.StakingScenarioInteractor

@InstallIn(SingletonComponent::class)
@Module
class RebondValidationsModule {

    @Provides
    fun provideFeeValidation() = RebondFeeValidation(
        feeExtractor = { it.fee },
        availableBalanceProducer = { it.controllerAsset.transferable },
        errorProducer = { RebondValidationFailure.CANNOT_PAY_FEE }
    )

    @Provides
    fun provideNotZeroUnbondValidation() = NotZeroRebondValidation(
        amountExtractor = { it.rebondAmount },
        errorProvider = { RebondValidationFailure.ZERO_AMOUNT }
    )

    @Provides
    fun provideEnoughToRebondValidation(stakingScenarioInteractor: StakingScenarioInteractor) = EnoughToRebondValidation(stakingScenarioInteractor)

    @Provides
    fun provideRebondValidationSystem(
        rebondFeeValidation: RebondFeeValidation,
        notZeroRebondValidation: NotZeroRebondValidation,
        enoughToRebondValidation: EnoughToRebondValidation,
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
