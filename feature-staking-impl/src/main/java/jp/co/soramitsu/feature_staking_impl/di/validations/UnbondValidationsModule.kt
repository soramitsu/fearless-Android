package jp.co.soramitsu.feature_staking_impl.di.validations

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import jp.co.soramitsu.common.validation.CompositeValidation
import jp.co.soramitsu.feature_staking_impl.domain.validations.unbond.CrossExistentialValidation
import jp.co.soramitsu.feature_staking_impl.domain.validations.unbond.EnoughToUnbondValidation
import jp.co.soramitsu.feature_staking_impl.domain.validations.unbond.NotZeroUnbondValidation
import jp.co.soramitsu.feature_staking_impl.domain.validations.unbond.UnbondFeeValidation
import jp.co.soramitsu.feature_staking_impl.domain.validations.unbond.UnbondLimitValidation
import jp.co.soramitsu.feature_staking_impl.domain.validations.unbond.UnbondValidationFailure
import jp.co.soramitsu.feature_staking_impl.domain.validations.unbond.UnbondValidationSystem
import jp.co.soramitsu.feature_staking_impl.scenarios.StakingScenarioInteractor

@InstallIn(SingletonComponent::class)
@Module
class UnbondValidationsModule {

    @Provides
    fun provideFeeValidation() = UnbondFeeValidation(
        feeExtractor = { it.fee },
        availableBalanceProducer = { it.asset.transferable },
        errorProducer = { UnbondValidationFailure.CannotPayFees }
    )

    @Provides
    fun provideNotZeroUnbondValidation() = NotZeroUnbondValidation(
        amountExtractor = { it.amount },
        errorProvider = { UnbondValidationFailure.ZeroUnbond }
    )

    @Provides
    fun provideUnbondLimitValidation(
        stakingScenarioInteractor: StakingScenarioInteractor,
    ) = UnbondLimitValidation(
        stakingScenarioInteractor = stakingScenarioInteractor,
        errorProducer = UnbondValidationFailure::UnbondLimitReached
    )

    @Provides
    fun provideEnoughToUnbondValidation(
        stakingScenarioInteractor: StakingScenarioInteractor
    ) = EnoughToUnbondValidation(stakingScenarioInteractor)

    @Provides
    fun provideCrossExistentialValidation(
        stakingScenarioInteractor: StakingScenarioInteractor
    ) = CrossExistentialValidation(stakingScenarioInteractor)

    @Provides
    fun provideUnbondValidationSystem(
        unbondFeeValidation: UnbondFeeValidation,
        notZeroUnbondValidation: NotZeroUnbondValidation,
        unbondLimitValidation: UnbondLimitValidation,
        enoughToUnbondValidation: EnoughToUnbondValidation,
        crossExistentialValidation: CrossExistentialValidation
    ) = UnbondValidationSystem(
        CompositeValidation(
            validations = listOf(
                unbondFeeValidation,
                notZeroUnbondValidation,
                unbondLimitValidation,
                enoughToUnbondValidation,
                crossExistentialValidation
            )
        )
    )
}
