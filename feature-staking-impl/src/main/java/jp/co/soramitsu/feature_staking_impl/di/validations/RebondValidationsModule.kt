package jp.co.soramitsu.feature_staking_impl.di.validations

import dagger.Module
import dagger.Provides
import jp.co.soramitsu.common.di.scope.FeatureScope
import jp.co.soramitsu.common.validation.CompositeValidation
import jp.co.soramitsu.feature_staking_api.domain.api.StakingRepository
import jp.co.soramitsu.feature_staking_impl.domain.validations.rebond.EnoughToRebondValidation
import jp.co.soramitsu.feature_staking_impl.domain.validations.rebond.NotZeroRebondValidation
import jp.co.soramitsu.feature_staking_impl.domain.validations.rebond.RebondElectionClosedValidation
import jp.co.soramitsu.feature_staking_impl.domain.validations.rebond.RebondFeeValidation
import jp.co.soramitsu.feature_staking_impl.domain.validations.rebond.RebondValidationFailure
import jp.co.soramitsu.feature_staking_impl.domain.validations.rebond.RebondValidationSystem

@Module
class RebondValidationsModule {

    @FeatureScope
    @Provides
    fun provideFeeValidation() = RebondFeeValidation(
        feeExtractor = { it.fee },
        availableBalanceProducer = { it.controllerAsset.transferable },
        errorProducer = { RebondValidationFailure.CANNOT_PAY_FEE }
    )

    @FeatureScope
    @Provides
    fun provideElectionClosedValidation(
        stakingRepository: StakingRepository,
    ) = RebondElectionClosedValidation(
        stakingRepository = stakingRepository,
        networkTypeProvider = { it.controllerAsset.token.type.networkType },
        errorProducer = { RebondValidationFailure.ELECTION_OPEN }
    )

    @FeatureScope
    @Provides
    fun provideNotZeroUnbondValidation() = NotZeroRebondValidation(
        amountExtractor = { it.rebondAmount },
        errorProvider = { RebondValidationFailure.ZERO_AMOUNT }
    )

    @FeatureScope
    @Provides
    fun provideEnoughToRebondValidation() = EnoughToRebondValidation()

    @FeatureScope
    @Provides
    fun provideRebondValidationSystem(
        rebondFeeValidation: RebondFeeValidation,
        rebondElectionClosedValidation: RebondElectionClosedValidation,
        notZeroRebondValidation: NotZeroRebondValidation,
        enoughToRebondValidation: EnoughToRebondValidation,
    ) = RebondValidationSystem(
        CompositeValidation(
            validations = listOf(
                rebondFeeValidation,
                rebondElectionClosedValidation,
                notZeroRebondValidation,
                enoughToRebondValidation
            )
        )
    )
}
