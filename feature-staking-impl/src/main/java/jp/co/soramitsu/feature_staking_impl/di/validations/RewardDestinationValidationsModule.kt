package jp.co.soramitsu.feature_staking_impl.di.validations

import dagger.Module
import dagger.Provides
import jp.co.soramitsu.common.di.scope.FeatureScope
import jp.co.soramitsu.common.validation.CompositeValidation
import jp.co.soramitsu.feature_staking_impl.domain.validations.rewardDestination.RewardDestinationControllerRequiredValidation
import jp.co.soramitsu.feature_staking_impl.domain.validations.rewardDestination.RewardDestinationFeeValidation
import jp.co.soramitsu.feature_staking_impl.domain.validations.rewardDestination.RewardDestinationValidationFailure
import jp.co.soramitsu.feature_staking_impl.domain.validations.rewardDestination.RewardDestinationValidationSystem
import jp.co.soramitsu.feature_staking_impl.scenarios.StakingScenarioInteractor

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
        stakingScenarioInteractor: StakingScenarioInteractor
    ) = RewardDestinationControllerRequiredValidation(
        stakingScenarioInteractor = stakingScenarioInteractor,
        accountAddressExtractor = { it.stashState.controllerAddress },
        errorProducer = RewardDestinationValidationFailure::MissingController,
    )

    @FeatureScope
    @Provides
    fun provideRedeemValidationSystem(
        feeValidation: RewardDestinationFeeValidation,
        controllerRequiredValidation: RewardDestinationControllerRequiredValidation,
    ) = RewardDestinationValidationSystem(
        CompositeValidation(
            validations = listOf(
                feeValidation,
                controllerRequiredValidation
            )
        )
    )
}
