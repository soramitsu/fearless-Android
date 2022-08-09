package jp.co.soramitsu.staking.impl.di.validations

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import jp.co.soramitsu.staking.impl.domain.validations.balance.BalanceAccountRequiredValidation
import jp.co.soramitsu.staking.impl.domain.validations.balance.BalanceUnlockingLimitValidation
import jp.co.soramitsu.staking.impl.domain.validations.balance.ManageStakingValidationFailure
import jp.co.soramitsu.staking.impl.scenarios.StakingScenarioInteractor
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
class StakingBalanceValidationsModule {

    @Provides
    @Singleton
    fun provideControllerValidation(
        stakingScenarioInteractor: StakingScenarioInteractor
    ) = BalanceAccountRequiredValidation(
        stakingScenarioInteractor,
        accountAddressExtractor = { it.stashState?.controllerAddress },
        errorProducer = ManageStakingValidationFailure::ControllerRequired
    )

    @Provides
    @Singleton
    fun provideStashValidation(
        stakingScenarioInteractor: StakingScenarioInteractor
    ) = BalanceAccountRequiredValidation(
        stakingScenarioInteractor,
        accountAddressExtractor = { it.stashState?.stashAddress },
        errorProducer = ManageStakingValidationFailure::StashRequired
    )

    @Provides
    @Singleton
    fun provideUnbondingLimitValidation(
        stakingScenarioInteractor: StakingScenarioInteractor
    ) = BalanceUnlockingLimitValidation(
        stakingScenarioInteractor,
        errorProducer = ManageStakingValidationFailure::UnbondingRequestLimitReached
    )
}
