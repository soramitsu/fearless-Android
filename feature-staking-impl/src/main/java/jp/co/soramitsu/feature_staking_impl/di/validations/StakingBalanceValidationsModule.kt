package jp.co.soramitsu.feature_staking_impl.di.validations

import dagger.Module
import dagger.Provides
import jp.co.soramitsu.common.di.scope.FeatureScope
import jp.co.soramitsu.common.validation.CompositeValidation
import jp.co.soramitsu.common.validation.ValidationSystem
import jp.co.soramitsu.feature_staking_impl.domain.validations.balance.BALANCE_REQUIRED_CONTROLLER
import jp.co.soramitsu.feature_staking_impl.domain.validations.balance.BALANCE_REQUIRED_STASH
import jp.co.soramitsu.feature_staking_impl.domain.validations.balance.BalanceAccountRequiredValidation
import jp.co.soramitsu.feature_staking_impl.domain.validations.balance.BalanceUnlockingLimitValidation
import jp.co.soramitsu.feature_staking_impl.domain.validations.balance.ManageStakingValidationFailure
import jp.co.soramitsu.feature_staking_impl.domain.validations.balance.SYSTEM_MANAGE_STAKING_BOND_MORE
import jp.co.soramitsu.feature_staking_impl.domain.validations.balance.SYSTEM_MANAGE_STAKING_REBOND
import jp.co.soramitsu.feature_staking_impl.domain.validations.balance.SYSTEM_MANAGE_STAKING_REDEEM
import jp.co.soramitsu.feature_staking_impl.domain.validations.balance.SYSTEM_MANAGE_STAKING_UNBOND
import jp.co.soramitsu.feature_staking_impl.scenarios.StakingRelayChainScenarioRepository
import jp.co.soramitsu.feature_staking_impl.scenarios.StakingScenarioInteractor
import javax.inject.Named

@Module
class StakingBalanceValidationsModule {

    @FeatureScope
    @Named(BALANCE_REQUIRED_CONTROLLER)
    @Provides
    fun provideControllerValidation(
        stakingScenarioInteractor: StakingScenarioInteractor,
    ) = BalanceAccountRequiredValidation(
        stakingScenarioInteractor,
        accountAddressExtractor = { it.stashState?.controllerAddress.orEmpty() },
        errorProducer = ManageStakingValidationFailure::ControllerRequired,
    )

    @FeatureScope
    @Named(BALANCE_REQUIRED_STASH)
    @Provides
    fun provideStashValidation(
        stakingScenarioInteractor: StakingScenarioInteractor,
    ) = BalanceAccountRequiredValidation(
        stakingScenarioInteractor,
        accountAddressExtractor = { it.stashState?.stashAddress.orEmpty() },
        errorProducer = ManageStakingValidationFailure::StashRequired,
    )

    @FeatureScope
    @Provides
    fun provideUnbondingLimitValidation(
        stakingScenarioInteractor: StakingScenarioInteractor,
        stakingRepository: StakingRelayChainScenarioRepository,
    ) = BalanceUnlockingLimitValidation(
        stakingScenarioInteractor,
        stakingRepository,
        errorProducer = ManageStakingValidationFailure::UnbondingRequestLimitReached
    )

    @FeatureScope
    @Named(SYSTEM_MANAGE_STAKING_REDEEM)
    @Provides
    fun provideRedeemValidationSystem(
        @Named(BALANCE_REQUIRED_CONTROLLER)
        controllerRequiredValidation: BalanceAccountRequiredValidation,
    ) = ValidationSystem(
        CompositeValidation(
            validations = listOf(
                controllerRequiredValidation
            )
        )
    )

    @FeatureScope
    @Named(SYSTEM_MANAGE_STAKING_BOND_MORE)
    @Provides
    fun provideBondMoreValidationSystem(
        @Named(BALANCE_REQUIRED_STASH)
        stashRequiredValidation: BalanceAccountRequiredValidation,
    ) = ValidationSystem(
        CompositeValidation(
            validations = listOf(
                stashRequiredValidation,
            )
        )
    )

    @FeatureScope
    @Named(SYSTEM_MANAGE_STAKING_UNBOND)
    @Provides
    fun provideUnbondValidationSystem(
        @Named(BALANCE_REQUIRED_CONTROLLER)
        controllerRequiredValidation: BalanceAccountRequiredValidation,
        balanceUnlockingLimitValidation: BalanceUnlockingLimitValidation
    ) = ValidationSystem(
        CompositeValidation(
            validations = listOf(
                controllerRequiredValidation,
                balanceUnlockingLimitValidation
            )
        )
    )

    @FeatureScope
    @Named(SYSTEM_MANAGE_STAKING_REBOND)
    @Provides
    fun provideRebondValidationSystem(
        @Named(BALANCE_REQUIRED_CONTROLLER)
        controllerRequiredValidation: BalanceAccountRequiredValidation
    ) = ValidationSystem(
        CompositeValidation(
            validations = listOf(
                controllerRequiredValidation
            )
        )
    )
}
