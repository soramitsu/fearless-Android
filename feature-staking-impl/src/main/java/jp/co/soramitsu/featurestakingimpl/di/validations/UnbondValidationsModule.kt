package jp.co.soramitsu.featurestakingimpl.di.validations

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import jp.co.soramitsu.common.validation.CompositeValidation
import jp.co.soramitsu.featurestakingimpl.domain.validations.unbond.CrossExistentialValidation
import jp.co.soramitsu.featurestakingimpl.domain.validations.unbond.EnoughToUnbondValidation
import jp.co.soramitsu.featurestakingimpl.domain.validations.unbond.NotZeroUnbondValidation
import jp.co.soramitsu.featurestakingimpl.domain.validations.unbond.UnbondFeeValidation
import jp.co.soramitsu.featurestakingimpl.domain.validations.unbond.UnbondLimitValidation
import jp.co.soramitsu.featurestakingimpl.domain.validations.unbond.UnbondValidationFailure
import jp.co.soramitsu.featurestakingimpl.domain.validations.unbond.UnbondValidationSystem
import jp.co.soramitsu.featurestakingimpl.scenarios.StakingScenarioInteractor
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
class UnbondValidationsModule {

    @Provides
    @Singleton
    fun provideFeeValidation() = UnbondFeeValidation(
        feeExtractor = { it.fee },
        availableBalanceProducer = { it.asset.transferable },
        errorProducer = { UnbondValidationFailure.CannotPayFees }
    )

    @Provides
    @Singleton
    fun provideNotZeroUnbondValidation() = NotZeroUnbondValidation(
        amountExtractor = { it.amount },
        errorProvider = { UnbondValidationFailure.ZeroUnbond }
    )

    @Provides
    @Singleton
    fun provideUnbondLimitValidation(
        stakingScenarioInteractor: StakingScenarioInteractor
    ) = UnbondLimitValidation(
        stakingScenarioInteractor = stakingScenarioInteractor,
        errorProducer = UnbondValidationFailure::UnbondLimitReached
    )

    @Provides
    @Singleton
    fun provideEnoughToUnbondValidation(
        stakingScenarioInteractor: StakingScenarioInteractor
    ) = EnoughToUnbondValidation(stakingScenarioInteractor)

    @Provides
    @Singleton
    fun provideCrossExistentialValidation(
        stakingScenarioInteractor: StakingScenarioInteractor
    ) = CrossExistentialValidation(stakingScenarioInteractor)

    @Provides
    @Singleton
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
