package jp.co.soramitsu.feature_staking_impl.di

import dagger.Module
import dagger.Provides
import jp.co.soramitsu.common.di.scope.FeatureScope
import jp.co.soramitsu.common.utils.networkType
import jp.co.soramitsu.common.validation.CompositeValidation
import jp.co.soramitsu.common.validation.ValidationSystem
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_staking_api.domain.api.StakingRepository
import jp.co.soramitsu.feature_staking_impl.domain.validations.balance.BalanceControllerRequiredValidation
import jp.co.soramitsu.feature_staking_impl.domain.validations.balance.BalanceElectionPeriodValidation
import jp.co.soramitsu.feature_staking_impl.domain.validations.balance.BalanceUnlockingLimitValidation
import jp.co.soramitsu.feature_staking_impl.domain.validations.balance.ManageStakingValidationFailure
import jp.co.soramitsu.feature_staking_impl.domain.validations.balance.SYSTEM_MANAGE_STAKING_DEFAULT
import jp.co.soramitsu.feature_staking_impl.domain.validations.balance.SYSTEM_MANAGE_STAKING_UNBOND
import jp.co.soramitsu.feature_staking_impl.domain.validations.payout.PayoutFeeValidation
import jp.co.soramitsu.feature_staking_impl.domain.validations.payout.PayoutValidationFailure
import jp.co.soramitsu.feature_staking_impl.domain.validations.payout.ProfitablePayoutValidation
import jp.co.soramitsu.feature_staking_impl.domain.validations.setup.MinimumAmountValidation
import jp.co.soramitsu.feature_staking_impl.domain.validations.setup.SetupStakingFeeValidation
import jp.co.soramitsu.feature_staking_impl.domain.validations.setup.SetupStakingValidationFailure
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletConstants
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.domain.validation.EnoughToPayFeesValidation
import jp.co.soramitsu.feature_wallet_api.domain.validation.assetBalanceProducer
import javax.inject.Named

@Module
class StakingValidationModule {

    @Provides
    @FeatureScope
    fun provideSetupStakingFeeValidation(
        walletRepository: WalletRepository,
    ): SetupStakingFeeValidation {
        return EnoughToPayFeesValidation(
            feeExtractor = { it.maxFee },
            availableBalanceProducer = SetupStakingFeeValidation.assetBalanceProducer(
                walletRepository,
                originAddressExtractor = { it.stashSetup.controllerAddress },
                tokenTypeExtractor = { it.tokenType }
            ),
            errorProducer = { SetupStakingValidationFailure.CannotPayFee },
            extraAmountExtractor = { it.amount }
        )
    }

    @Provides
    @FeatureScope
    fun providePayoutFeeValidation(
        walletRepository: WalletRepository,
    ): PayoutFeeValidation {
        return EnoughToPayFeesValidation(
            feeExtractor = { it.fee },
            availableBalanceProducer = SetupStakingFeeValidation.assetBalanceProducer(
                walletRepository,
                originAddressExtractor = { it.originAddress },
                tokenTypeExtractor = { it.tokenType }
            ),
            errorProducer = { PayoutValidationFailure.CannotPayFee }
        )
    }

    @Provides
    @FeatureScope
    fun provideMinimumAmountValidation(
        walletConstants: WalletConstants,
    ) = MinimumAmountValidation(walletConstants)

    @Provides
    @FeatureScope
    fun provideSetupStakingValidationSystem(
        enoughToPayFeesValidation: SetupStakingFeeValidation,
        minimumAmountValidation: MinimumAmountValidation,
    ) = ValidationSystem(
        CompositeValidation(listOf(enoughToPayFeesValidation, minimumAmountValidation))
    )

    @FeatureScope
    @Provides
    fun provideProfitablePayoutValidation() = ProfitablePayoutValidation()

    @FeatureScope
    @Provides
    fun provideBalanceControllerValidation(
        accountRepository: AccountRepository
    ) = BalanceControllerRequiredValidation(
        accountRepository,
        controllerAddressExtractor = { it.stashState.controllerAddress },
        errorProducer = ManageStakingValidationFailure::ControllerRequired
    )

    @FeatureScope
    @Provides
    fun provideBalanceElectionValidation(
        stakingRepository: StakingRepository,
    ) = BalanceElectionPeriodValidation(
        stakingRepository,
        networkTypeProvider = { it.stashState.controllerAddress.networkType() },
        errorProducer = { ManageStakingValidationFailure.ElectionPeriodOpen }
    )

    @FeatureScope
    @Provides
    fun provideBalanceUnbondingLimitValidation(
        stakingRepository: StakingRepository,
    ) = BalanceUnlockingLimitValidation(
        stakingRepository,
        stashStateProducer = { it.stashState },
        errorProducer = ManageStakingValidationFailure::UnbondingRequestLimitReached
    )

    @FeatureScope
    @Named(SYSTEM_MANAGE_STAKING_DEFAULT)
    @Provides
    fun provideDefaultManageStakingValidationSystem(
        balanceElectionPeriodValidation: BalanceElectionPeriodValidation,
        balanceControllerRequiredValidation: BalanceControllerRequiredValidation,
    ) = ValidationSystem(
        CompositeValidation(
            validators = listOf(
                balanceControllerRequiredValidation,
                balanceElectionPeriodValidation
            )
        )
    )

    @FeatureScope
    @Named(SYSTEM_MANAGE_STAKING_UNBOND)
    @Provides
    fun provideUnbondManageStakingValidationSystem(
        balanceElectionPeriodValidation: BalanceElectionPeriodValidation,
        balanceControllerRequiredValidation: BalanceControllerRequiredValidation,
        balanceUnlockingLimitValidation: BalanceUnlockingLimitValidation
    ) = ValidationSystem(
        CompositeValidation(
            validators = listOf(
                balanceControllerRequiredValidation,
                balanceElectionPeriodValidation,
                balanceUnlockingLimitValidation
            )
        )
    )

    @Provides
    @FeatureScope
    fun provideMakePayoutValidationSystem(
        enoughToPayFeesValidation: PayoutFeeValidation,
        profitablePayoutValidation: ProfitablePayoutValidation,
    ) = ValidationSystem(
        CompositeValidation(
            listOf(
                enoughToPayFeesValidation,
                profitablePayoutValidation,
            )
        )
    )
}
