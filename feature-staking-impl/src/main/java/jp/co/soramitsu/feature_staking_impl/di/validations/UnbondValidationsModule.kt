package jp.co.soramitsu.feature_staking_impl.di.validations

import dagger.Module
import dagger.Provides
import jp.co.soramitsu.common.di.scope.FeatureScope
import jp.co.soramitsu.common.validation.CompositeValidation
import jp.co.soramitsu.feature_staking_api.domain.api.StakingRepository
import jp.co.soramitsu.feature_staking_impl.domain.validations.unbond.CrossExistentialValidation
import jp.co.soramitsu.feature_staking_impl.domain.validations.unbond.EnoughToUnbondValidation
import jp.co.soramitsu.feature_staking_impl.domain.validations.unbond.NotZeroUnbondValidation
import jp.co.soramitsu.feature_staking_impl.domain.validations.unbond.UnbondElectionClosedValidation
import jp.co.soramitsu.feature_staking_impl.domain.validations.unbond.UnbondFeeValidation
import jp.co.soramitsu.feature_staking_impl.domain.validations.unbond.UnbondLimitValidation
import jp.co.soramitsu.feature_staking_impl.domain.validations.unbond.UnbondValidationFailure
import jp.co.soramitsu.feature_staking_impl.domain.validations.unbond.UnbondValidationSystem
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletConstants

@Module
class UnbondValidationsModule {

    @FeatureScope
    @Provides
    fun provideFeeValidation() = UnbondFeeValidation(
        feeExtractor = { it.fee },
        availableBalanceProducer = { it.asset.transferable },
        errorProducer = { UnbondValidationFailure.CannotPayFees }
    )

    @FeatureScope
    @Provides
    fun provideElectionClosedValidation(
        stakingRepository: StakingRepository
    ) = UnbondElectionClosedValidation(
        stakingRepository = stakingRepository,
        networkTypeProvider = { it.tokenType.networkType },
        errorProducer = { UnbondValidationFailure.ElectionIsOpen }
    )

    @FeatureScope
    @Provides
    fun provideNotZeroUnbondValidation() = NotZeroUnbondValidation(
        amountExtractor = { it.amount },
        errorProvider = { UnbondValidationFailure.ZeroUnbond }
    )

    @FeatureScope
    @Provides
    fun provideUnbondLimitValidation(
        stakingRepository: StakingRepository
    ) = UnbondLimitValidation(
        stakingRepository = stakingRepository,
        stashStateProducer = { it.stash },
        errorProducer = UnbondValidationFailure::UnbondLimitReached
    )

    @FeatureScope
    @Provides
    fun provideEnoughToUnbondValidation() = EnoughToUnbondValidation()

    @FeatureScope
    @Provides
    fun provideCrossExistentialValidation(
        walletConstants: WalletConstants
    ) = CrossExistentialValidation(walletConstants)

    @FeatureScope
    @Provides
    fun provideUnbondValidationSystem(
        unbondFeeValidation: UnbondFeeValidation,
        electionClosedValidation: UnbondElectionClosedValidation,
        notZeroUnbondValidation: NotZeroUnbondValidation,
        unbondLimitValidation: UnbondLimitValidation,
        enoughToUnbondValidation: EnoughToUnbondValidation,
        crossExistentialValidation: CrossExistentialValidation
    ) = UnbondValidationSystem(
        CompositeValidation(
            validations = listOf(
                unbondFeeValidation,
                electionClosedValidation,
                notZeroUnbondValidation,
                unbondLimitValidation,
                enoughToUnbondValidation,
                crossExistentialValidation
            )
        )
    )
}
