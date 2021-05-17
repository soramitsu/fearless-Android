package jp.co.soramitsu.feature_staking_impl.di.validations

import dagger.Module
import dagger.Provides
import jp.co.soramitsu.common.di.scope.FeatureScope
import jp.co.soramitsu.common.validation.CompositeValidation
import jp.co.soramitsu.feature_staking_api.domain.api.StakingRepository
import jp.co.soramitsu.feature_staking_impl.domain.validations.reedeem.RedeemElectionClosedValidation
import jp.co.soramitsu.feature_staking_impl.domain.validations.reedeem.RedeemFeeValidation
import jp.co.soramitsu.feature_staking_impl.domain.validations.reedeem.RedeemValidationFailure
import jp.co.soramitsu.feature_staking_impl.domain.validations.reedeem.RedeemValidationSystem

@Module
class RedeemValidationsModule {

    @FeatureScope
    @Provides
    fun provideFeeValidation() = RedeemFeeValidation(
        feeExtractor = { it.fee },
        availableBalanceProducer = { it.asset.transferable },
        errorProducer = { RedeemValidationFailure.CANNOT_PAY_FEES }
    )

    @FeatureScope
    @Provides
    fun provideElectionClosedValidation(
        stakingRepository: StakingRepository,
    ) = RedeemElectionClosedValidation(
        stakingRepository = stakingRepository,
        networkTypeProvider = { it.networkType },
        errorProducer = { RedeemValidationFailure.ELECTION_IS_OPEN }
    )

    @FeatureScope
    @Provides
    fun provideRedeemValidationSystem(
        feeValidation: RedeemFeeValidation,
        electionClosedValidation: RedeemElectionClosedValidation,
    ) = RedeemValidationSystem(
        CompositeValidation(
            validations = listOf(
                feeValidation,
                electionClosedValidation
            )
        )
    )
}
