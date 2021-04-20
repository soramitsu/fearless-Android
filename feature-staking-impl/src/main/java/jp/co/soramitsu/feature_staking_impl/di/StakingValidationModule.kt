package jp.co.soramitsu.feature_staking_impl.di

import dagger.Module
import dagger.Provides
import jp.co.soramitsu.common.di.scope.FeatureScope
import jp.co.soramitsu.common.validation.CompositeValidation
import jp.co.soramitsu.common.validation.ValidationSystem
import jp.co.soramitsu.feature_staking_impl.domain.validations.payout.MakePayoutPayload
import jp.co.soramitsu.feature_staking_impl.domain.validations.payout.PayoutFeeValidation
import jp.co.soramitsu.feature_staking_impl.domain.validations.payout.PayoutValidationFailure
import jp.co.soramitsu.feature_staking_impl.domain.validations.payout.ProfitablePayoutValidation
import jp.co.soramitsu.feature_staking_impl.domain.validations.setup.MinimumAmountValidation
import jp.co.soramitsu.feature_staking_impl.domain.validations.setup.SetupStakingFeeValidation
import jp.co.soramitsu.feature_staking_impl.domain.validations.setup.SetupStakingPayload
import jp.co.soramitsu.feature_staking_impl.domain.validations.setup.SetupStakingValidationFailure
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletConstants
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.domain.validation.EnoughToPayFeesValidation

@Module
class StakingValidationModule {

    @Provides
    @FeatureScope
    fun provideSetupStakingFeeValidation(
        walletRepository: WalletRepository,
    ): SetupStakingFeeValidation {
        return EnoughToPayFeesValidation(
            walletRepository = walletRepository,
            feeExtractor = SetupStakingPayload::maxFee,
            originAddressExtractor = { it.stashSetup.controllerAddress },
            tokenTypeExtractor = SetupStakingPayload::tokenType,
            errorProducer = { SetupStakingValidationFailure.CannotPayFee },
            extraAmountExtractor = SetupStakingPayload::amount
        )
    }

    @Provides
    @FeatureScope
    fun providePayoutFeeValidation(
        walletRepository: WalletRepository,
    ): PayoutFeeValidation {
        return EnoughToPayFeesValidation(
            walletRepository = walletRepository,
            feeExtractor = MakePayoutPayload::fee,
            originAddressExtractor = { it.originAddress },
            tokenTypeExtractor = MakePayoutPayload::tokenType,
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
