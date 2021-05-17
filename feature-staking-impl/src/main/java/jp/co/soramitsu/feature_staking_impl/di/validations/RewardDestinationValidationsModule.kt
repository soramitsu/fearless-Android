package jp.co.soramitsu.feature_staking_impl.di.validations

import dagger.Module
import dagger.Provides
import jp.co.soramitsu.common.di.scope.FeatureScope
import jp.co.soramitsu.common.utils.networkType
import jp.co.soramitsu.common.validation.CompositeValidation
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_staking_api.domain.api.StakingRepository
import jp.co.soramitsu.feature_staking_impl.domain.validations.rewardDestination.RewardDestinationControllerRequiredValidation
import jp.co.soramitsu.feature_staking_impl.domain.validations.rewardDestination.RewardDestinationElectionPeriodValidation
import jp.co.soramitsu.feature_staking_impl.domain.validations.rewardDestination.RewardDestinationFeeValidation
import jp.co.soramitsu.feature_staking_impl.domain.validations.rewardDestination.RewardDestinationValidationFailure
import jp.co.soramitsu.feature_staking_impl.domain.validations.rewardDestination.RewardDestinationValidationSystem

@Module
class RewardDestinationValidationsModule {

    @FeatureScope
    @Provides
    fun provideFeeValidation() = RewardDestinationFeeValidation(
        feeExtractor = { it.fee },
        availableBalanceProducer = { it.availableControllerBalance },
        errorProducer = { RewardDestinationValidationFailure.CannotPayFees }
    )

    @Provides
    @FeatureScope
    fun controllerRequiredValidation(
        accountRepository: AccountRepository,
    ) = RewardDestinationControllerRequiredValidation(
        accountRepository = accountRepository,
        accountAddressExtractor = { it.stashState.controllerAddress },
        errorProducer = RewardDestinationValidationFailure::MissingController
    )

    @FeatureScope
    @Provides
    fun provideElectionClosedValidation(
        stakingRepository: StakingRepository,
    ) = RewardDestinationElectionPeriodValidation(
        stakingRepository = stakingRepository,
        networkTypeProvider = { it.stashState.controllerAddress.networkType() },
        errorProducer = { RewardDestinationValidationFailure.OpenElection }
    )

    @FeatureScope
    @Provides
    fun provideRedeemValidationSystem(
        feeValidation: RewardDestinationFeeValidation,
        electionClosedValidation: RewardDestinationElectionPeriodValidation,
        controllerRequiredValidation: RewardDestinationControllerRequiredValidation,
    ) = RewardDestinationValidationSystem(
        CompositeValidation(
            validations = listOf(
                feeValidation,
                electionClosedValidation,
                controllerRequiredValidation
            )
        )
    )
}
