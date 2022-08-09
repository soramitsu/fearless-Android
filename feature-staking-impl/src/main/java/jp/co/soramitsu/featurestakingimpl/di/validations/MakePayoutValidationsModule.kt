package jp.co.soramitsu.featurestakingimpl.di.validations

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import jp.co.soramitsu.common.validation.CompositeValidation
import jp.co.soramitsu.common.validation.ValidationSystem
import jp.co.soramitsu.featureaccountapi.domain.interfaces.AccountRepository
import jp.co.soramitsu.featurestakingapi.data.StakingSharedState
import jp.co.soramitsu.featurestakingimpl.domain.validations.payout.PayoutFeeValidation
import jp.co.soramitsu.featurestakingimpl.domain.validations.payout.PayoutValidationFailure
import jp.co.soramitsu.featurestakingimpl.domain.validations.payout.ProfitablePayoutValidation
import jp.co.soramitsu.featurestakingimpl.domain.validations.setup.SetupStakingFeeValidation
import jp.co.soramitsu.featurewalletapi.domain.interfaces.WalletRepository
import jp.co.soramitsu.featurewalletapi.domain.validation.EnoughToPayFeesValidation
import jp.co.soramitsu.featurewalletapi.domain.validation.assetBalanceProducer
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
class MakePayoutValidationsModule {

    @Provides
    @Singleton
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

    @Provides
    @Singleton
    fun provideProfitableValidation() = ProfitablePayoutValidation()

    @Provides
    @Singleton
    fun provideValidationSystem(
        enoughToPayFeesValidation: PayoutFeeValidation,
        profitablePayoutValidation: ProfitablePayoutValidation
    ) = ValidationSystem(
        CompositeValidation(
            listOf(
                enoughToPayFeesValidation,
                profitablePayoutValidation
            )
        )
    )
}
