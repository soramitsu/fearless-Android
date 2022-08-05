package jp.co.soramitsu.feature_staking_impl.di.validations

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import jp.co.soramitsu.feature_staking_impl.domain.validations.balance.BalanceAccountRequiredValidation
import jp.co.soramitsu.feature_staking_impl.domain.validations.balance.BalanceUnlockingLimitValidation
import jp.co.soramitsu.feature_staking_impl.domain.validations.balance.ManageStakingValidationFailure
import jp.co.soramitsu.feature_staking_impl.scenarios.StakingScenarioInteractor

@InstallIn(SingletonComponent::class)
@Module
class StakingBalanceValidationsModule {

    @Provides
    fun provideControllerValidation(
        stakingScenarioInteractor: StakingScenarioInteractor,
    ) = BalanceAccountRequiredValidation(
        stakingScenarioInteractor,
        accountAddressExtractor = { it.stashState?.controllerAddress },
        errorProducer = ManageStakingValidationFailure::ControllerRequired,
    )

    @Provides
    fun provideStashValidation(
        stakingScenarioInteractor: StakingScenarioInteractor,
    ) = BalanceAccountRequiredValidation(
        stakingScenarioInteractor,
        accountAddressExtractor = { it.stashState?.stashAddress },
        errorProducer = ManageStakingValidationFailure::StashRequired,
    )

    @Provides
    fun provideUnbondingLimitValidation(
        stakingScenarioInteractor: StakingScenarioInteractor,
    ) = BalanceUnlockingLimitValidation(
        stakingScenarioInteractor,
        errorProducer = ManageStakingValidationFailure::UnbondingRequestLimitReached
    )
}
