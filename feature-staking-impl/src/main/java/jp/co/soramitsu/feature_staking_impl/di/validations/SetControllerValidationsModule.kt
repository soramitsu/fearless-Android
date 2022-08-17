package jp.co.soramitsu.feature_staking_impl.di.validations

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import jp.co.soramitsu.common.validation.CompositeValidation
import jp.co.soramitsu.feature_staking_api.data.StakingSharedState
import jp.co.soramitsu.feature_staking_impl.domain.validations.NotZeroBalanceValidation
import jp.co.soramitsu.feature_staking_impl.domain.validations.controller.IsNotControllerAccountValidation
import jp.co.soramitsu.feature_staking_impl.domain.validations.controller.SetControllerFeeValidation
import jp.co.soramitsu.feature_staking_impl.domain.validations.controller.SetControllerValidationFailure
import jp.co.soramitsu.feature_staking_impl.domain.validations.controller.SetControllerValidationSystem
import jp.co.soramitsu.feature_staking_impl.scenarios.StakingScenarioInteractor
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.domain.validation.EnoughToPayFeesValidation
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
class SetControllerValidationsModule {

    @Provides
    @Singleton
    fun provideFeeValidation(): SetControllerFeeValidation {
        return EnoughToPayFeesValidation(
            feeExtractor = { it.fee },
            availableBalanceProducer = { it.transferable },
            errorProducer = { SetControllerValidationFailure.NOT_ENOUGH_TO_PAY_FEES }
        )
    }

    @Provides
    @Singleton
    fun provideControllerValidation(
        stakingScenarioInteractor: StakingScenarioInteractor
    ) = IsNotControllerAccountValidation(
        controllerAddressProducer = { it.controllerAddress },
        errorProducer = { SetControllerValidationFailure.ALREADY_CONTROLLER },
        stakingScenarioInteractor = stakingScenarioInteractor
    )

    @Provides
    @Singleton
    fun provideZeroBalanceControllerValidation(
        stakingSharedState: StakingSharedState,
        walletRepository: WalletRepository
    ): NotZeroBalanceValidation {
        return NotZeroBalanceValidation(
            walletRepository = walletRepository,
            stakingSharedState = stakingSharedState
        )
    }

    @Provides
    @Singleton
    fun provideSetControllerValidationSystem(
        enoughToPayFeesValidation: SetControllerFeeValidation,
        isNotControllerAccountValidation: IsNotControllerAccountValidation,
        controllerAccountIsNotZeroBalance: NotZeroBalanceValidation
    ) = SetControllerValidationSystem(
        CompositeValidation(
            validations = listOf(
                enoughToPayFeesValidation,
                isNotControllerAccountValidation,
                controllerAccountIsNotZeroBalance
            )
        )
    )
}
