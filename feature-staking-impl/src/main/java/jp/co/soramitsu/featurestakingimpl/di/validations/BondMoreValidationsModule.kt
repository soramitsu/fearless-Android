package jp.co.soramitsu.featurestakingimpl.di.validations

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import jp.co.soramitsu.common.validation.CompositeValidation
import jp.co.soramitsu.featureaccountapi.domain.interfaces.AccountRepository
import jp.co.soramitsu.featurestakingapi.data.StakingSharedState
import jp.co.soramitsu.featurestakingimpl.domain.validations.bond.BondMoreFeeValidation
import jp.co.soramitsu.featurestakingimpl.domain.validations.bond.BondMoreValidationFailure
import jp.co.soramitsu.featurestakingimpl.domain.validations.bond.BondMoreValidationPayload
import jp.co.soramitsu.featurestakingimpl.domain.validations.bond.BondMoreValidationSystem
import jp.co.soramitsu.featurestakingimpl.domain.validations.bond.NotZeroBondValidation
import jp.co.soramitsu.featurestakingimpl.domain.validations.setup.SetupStakingFeeValidation
import jp.co.soramitsu.featurewalletapi.domain.interfaces.WalletRepository
import jp.co.soramitsu.featurewalletapi.domain.validation.EnoughToPayFeesValidation
import jp.co.soramitsu.featurewalletapi.domain.validation.assetBalanceProducer
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
class BondMoreValidationsModule {

    @Provides
    @Singleton
    fun provideFeeValidation(
        stakingSharedState: StakingSharedState,
        walletRepository: WalletRepository,
        accountRepository: AccountRepository
    ): BondMoreFeeValidation {
        return EnoughToPayFeesValidation(
            feeExtractor = { it.fee },
            availableBalanceProducer = SetupStakingFeeValidation.assetBalanceProducer(
                accountRepository,
                walletRepository,
                originAddressExtractor = { it.stashAddress },
                chainAssetExtractor = { it.chainAsset },
                stakingSharedState = stakingSharedState
            ),
            errorProducer = { BondMoreValidationFailure.NOT_ENOUGH_TO_PAY_FEES },
            extraAmountExtractor = { it.amount }
        )
    }

    @Provides
    @Singleton
    fun provideNotZeroBondValidation() = NotZeroBondValidation(
        amountExtractor = BondMoreValidationPayload::amount,
        errorProvider = { BondMoreValidationFailure.ZERO_BOND }
    )

    @Provides
    @Singleton
    fun provideBondMoreValidationSystem(
        bondMoreFeeValidation: BondMoreFeeValidation,
        notZeroBondValidation: NotZeroBondValidation
    ) = BondMoreValidationSystem(
        validation = CompositeValidation(
            validations = listOf(
                bondMoreFeeValidation,
                notZeroBondValidation
            )
        )
    )
}
