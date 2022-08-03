package jp.co.soramitsu.feature_staking_impl.di.validations

import dagger.Module
import dagger.Provides
import jp.co.soramitsu.common.di.scope.FeatureScope
import jp.co.soramitsu.common.validation.CompositeValidation
import jp.co.soramitsu.feature_staking_impl.domain.validations.unbond.CrossExistentialValidation
import jp.co.soramitsu.feature_staking_impl.domain.validations.unbond.EnoughToUnbondValidation
import jp.co.soramitsu.feature_staking_impl.domain.validations.unbond.NotZeroUnbondValidation
import jp.co.soramitsu.feature_staking_impl.domain.validations.unbond.UnbondFeeValidation
import jp.co.soramitsu.feature_staking_impl.domain.validations.unbond.UnbondLimitValidation
import jp.co.soramitsu.feature_staking_impl.domain.validations.unbond.UnbondValidationFailure
import jp.co.soramitsu.feature_staking_impl.domain.validations.unbond.UnbondValidationSystem
import jp.co.soramitsu.feature_staking_impl.scenarios.StakingScenarioInteractor

@Module
class UnbondValidationsModule {

    @FeatureScope
    @Provides
    fun provideFeeValidation() = UnbondFeeValidation(
        feeExtractor = { it.fee },
        availableBalanceProducer = { it.asset.transferable },
        errorProducer = { UnbondValidationFailure.CannotPayFees }
    )

    @FeatureScope
    @Provides
    fun provideNotZeroUnbondValidation() = NotZeroUnbondValidation(
        amountExtractor = { it.amount },
        errorProvider = { UnbondValidationFailure.ZeroUnbond }
    )

    @FeatureScope
    @Provides
    fun provideUnbondLimitValidation(
        stakingScenarioInteractor: StakingScenarioInteractor,
    ) = UnbondLimitValidation(
        stakingScenarioInteractor = stakingScenarioInteractor,
        errorProducer = UnbondValidationFailure::UnbondLimitReached
    )

    @FeatureScope
    @Provides
    fun provideEnoughToUnbondValidation(
        stakingScenarioInteractor: StakingScenarioInteractor
    ) = EnoughToUnbondValidation(stakingScenarioInteractor)

    @FeatureScope
    @Provides
    fun provideCrossExistentialValidation(
        stakingScenarioInteractor: StakingScenarioInteractor
    ) = CrossExistentialValidation(stakingScenarioInteractor)

    @FeatureScope
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
