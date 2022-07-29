package jp.co.soramitsu.feature_staking_impl.di.validations

import dagger.Module
import dagger.Provides
import javax.inject.Named
import jp.co.soramitsu.common.di.scope.FeatureScope
import jp.co.soramitsu.feature_staking_impl.domain.validations.balance.BALANCE_REQUIRED_CONTROLLER
import jp.co.soramitsu.feature_staking_impl.domain.validations.balance.BALANCE_REQUIRED_STASH
import jp.co.soramitsu.feature_staking_impl.domain.validations.balance.BalanceAccountRequiredValidation
import jp.co.soramitsu.feature_staking_impl.domain.validations.balance.BalanceUnlockingLimitValidation
import jp.co.soramitsu.feature_staking_impl.domain.validations.balance.ManageStakingValidationFailure
import jp.co.soramitsu.feature_staking_impl.scenarios.StakingScenarioInteractor

@Module
class StakingBalanceValidationsModule {

    @FeatureScope
    @Named(BALANCE_REQUIRED_CONTROLLER)
    @Provides
    fun provideControllerValidation(
        stakingScenarioInteractor: StakingScenarioInteractor,
    ) = BalanceAccountRequiredValidation(
        stakingScenarioInteractor,
        accountAddressExtractor = { it.stashState?.controllerAddress },
        errorProducer = ManageStakingValidationFailure::ControllerRequired,
    )

    @FeatureScope
    @Named(BALANCE_REQUIRED_STASH)
    @Provides
    fun provideStashValidation(
        stakingScenarioInteractor: StakingScenarioInteractor,
    ) = BalanceAccountRequiredValidation(
        stakingScenarioInteractor,
        accountAddressExtractor = { it.stashState?.stashAddress },
        errorProducer = ManageStakingValidationFailure::StashRequired,
    )

    @FeatureScope
    @Provides
    fun provideUnbondingLimitValidation(
        stakingScenarioInteractor: StakingScenarioInteractor,
    ) = BalanceUnlockingLimitValidation(
        stakingScenarioInteractor,
        errorProducer = ManageStakingValidationFailure::UnbondingRequestLimitReached
    )
}
