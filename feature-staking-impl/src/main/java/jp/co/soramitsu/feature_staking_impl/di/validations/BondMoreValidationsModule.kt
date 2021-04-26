package jp.co.soramitsu.feature_staking_impl.di.validations

import dagger.Module
import dagger.Provides
import jp.co.soramitsu.common.di.scope.FeatureScope
import jp.co.soramitsu.common.utils.networkType
import jp.co.soramitsu.common.validation.CompositeValidation
import jp.co.soramitsu.feature_staking_api.domain.api.StakingRepository
import jp.co.soramitsu.feature_staking_impl.domain.validations.bond.BondMoreElectionClosedValidation
import jp.co.soramitsu.feature_staking_impl.domain.validations.bond.BondMoreFeeValidation
import jp.co.soramitsu.feature_staking_impl.domain.validations.bond.BondMoreValidationFailure
import jp.co.soramitsu.feature_staking_impl.domain.validations.bond.BondMoreValidationSystem
import jp.co.soramitsu.feature_staking_impl.domain.validations.bond.NotZeroBondValidation
import jp.co.soramitsu.feature_staking_impl.domain.validations.setup.SetupStakingFeeValidation
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.domain.validation.EnoughToPayFeesValidation
import jp.co.soramitsu.feature_wallet_api.domain.validation.assetBalanceProducer

@Module
class BondMoreValidationsModule {

    @FeatureScope
    @Provides
    fun provideElectionValidation(
        stakingRepository: StakingRepository,
    ) = BondMoreElectionClosedValidation(
        stakingRepository,
        networkTypeProvider = { it.stashAddress.networkType() },
        errorProducer = { BondMoreValidationFailure.ELECTION_IS_OPEN }
    )

    @Provides
    @FeatureScope
    fun provideFeeValidation(
        walletRepository: WalletRepository,
    ): BondMoreFeeValidation {
        return EnoughToPayFeesValidation(
            feeExtractor = { it.fee },
            availableBalanceProducer = SetupStakingFeeValidation.assetBalanceProducer(
                walletRepository,
                originAddressExtractor = { it.stashAddress },
                tokenTypeExtractor = { it.tokenType },
            ),
            errorProducer = { BondMoreValidationFailure.NOT_ENOUGH_TO_PAY_FEES },
            extraAmountExtractor = { it.amount }
        )
    }

    @Provides
    @FeatureScope
    fun provideNotZeroBondValidation() = NotZeroBondValidation()

    @Provides
    @FeatureScope
    fun provideBondMoreValidationSystem(
        bondMoreFeeValidation: BondMoreFeeValidation,
        bondMoreElectionClosedValidation: BondMoreElectionClosedValidation,
        notZeroBondValidation: NotZeroBondValidation,
    ) = BondMoreValidationSystem(
        validation = CompositeValidation(
            validations = listOf(
                bondMoreFeeValidation,
                bondMoreElectionClosedValidation,
                notZeroBondValidation
            )
        )
    )
}
