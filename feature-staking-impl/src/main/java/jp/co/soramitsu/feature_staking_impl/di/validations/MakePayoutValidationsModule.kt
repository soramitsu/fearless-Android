package jp.co.soramitsu.feature_staking_impl.di.validations

import dagger.Module
import dagger.Provides
import jp.co.soramitsu.common.di.scope.FeatureScope
import jp.co.soramitsu.common.validation.CompositeValidation
import jp.co.soramitsu.common.validation.ValidationSystem
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_staking_api.data.StakingSharedState
import jp.co.soramitsu.feature_staking_impl.domain.validations.payout.PayoutFeeValidation
import jp.co.soramitsu.feature_staking_impl.domain.validations.payout.PayoutValidationFailure
import jp.co.soramitsu.feature_staking_impl.domain.validations.payout.ProfitablePayoutValidation
import jp.co.soramitsu.feature_staking_impl.domain.validations.setup.SetupStakingFeeValidation
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.domain.validation.EnoughToPayFeesValidation
import jp.co.soramitsu.feature_wallet_api.domain.validation.assetBalanceProducer

@Module
class MakePayoutValidationsModule {

    @Provides
    @FeatureScope
    fun provideFeeValidation(
        stakingSharedState: StakingSharedState,
        walletRepository: WalletRepository,
        accountRepository: AccountRepository
    ): PayoutFeeValidation {
        return EnoughToPayFeesValidation(
            feeExtractor = { it.fee },
            availableBalanceProducer = SetupStakingFeeValidation.assetBalanceProducer(
                accountRepository,
                walletRepository,
                originAddressExtractor = { it.originAddress },
                chainAssetExtractor = { it.chainAsset },
                stakingSharedState = stakingSharedState
            ),
            errorProducer = { PayoutValidationFailure.CannotPayFee }
        )
    }

    @FeatureScope
    @Provides
    fun provideProfitableValidation() = ProfitablePayoutValidation()

    @Provides
    @FeatureScope
    fun provideValidationSystem(
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
