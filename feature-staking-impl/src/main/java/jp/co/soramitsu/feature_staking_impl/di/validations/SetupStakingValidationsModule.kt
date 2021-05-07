package jp.co.soramitsu.feature_staking_impl.di.validations

import dagger.Module
import dagger.Provides
import jp.co.soramitsu.common.di.scope.FeatureScope
import jp.co.soramitsu.common.validation.CompositeValidation
import jp.co.soramitsu.common.validation.ValidationSystem
import jp.co.soramitsu.feature_staking_impl.domain.validations.setup.MinimumAmountValidation
import jp.co.soramitsu.feature_staking_impl.domain.validations.setup.SetupStakingFeeValidation
import jp.co.soramitsu.feature_staking_impl.domain.validations.setup.SetupStakingValidationFailure
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletConstants
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.domain.validation.EnoughToPayFeesValidation
import jp.co.soramitsu.feature_wallet_api.domain.validation.assetBalanceProducer

@Module
class SetupStakingValidationsModule {

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
}
