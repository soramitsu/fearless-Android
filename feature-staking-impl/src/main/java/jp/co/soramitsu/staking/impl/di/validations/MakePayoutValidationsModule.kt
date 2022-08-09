package jp.co.soramitsu.staking.impl.di.validations

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import jp.co.soramitsu.common.validation.CompositeValidation
import jp.co.soramitsu.common.validation.ValidationSystem
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.staking.api.data.StakingSharedState
import jp.co.soramitsu.staking.impl.domain.validations.payout.PayoutFeeValidation
import jp.co.soramitsu.staking.impl.domain.validations.payout.PayoutValidationFailure
import jp.co.soramitsu.staking.impl.domain.validations.payout.ProfitablePayoutValidation
import jp.co.soramitsu.staking.impl.domain.validations.setup.SetupStakingFeeValidation
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletRepository
import jp.co.soramitsu.wallet.impl.domain.validation.EnoughToPayFeesValidation
import jp.co.soramitsu.wallet.impl.domain.validation.assetBalanceProducer
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
