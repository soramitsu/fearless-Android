package jp.co.soramitsu.featurestakingimpl.di.validations

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import jp.co.soramitsu.common.validation.CompositeValidation
import jp.co.soramitsu.featurestakingimpl.domain.validations.rewardDestination.RewardDestinationControllerRequiredValidation
import jp.co.soramitsu.featurestakingimpl.domain.validations.rewardDestination.RewardDestinationFeeValidation
import jp.co.soramitsu.featurestakingimpl.domain.validations.rewardDestination.RewardDestinationValidationFailure
import jp.co.soramitsu.featurestakingimpl.domain.validations.rewardDestination.RewardDestinationValidationSystem
import jp.co.soramitsu.featurestakingimpl.scenarios.StakingScenarioInteractor
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
class RewardDestinationValidationsModule {

    @Provides
    @Singleton
    fun provideFeeValidation() = RewardDestinationFeeValidation(
        feeExtractor = { it.fee },
        availableBalanceProducer = { it.availableControllerBalance },
        errorProducer = { RewardDestinationValidationFailure.CannotPayFees }
    )

    @Provides
    @Singleton
    fun controllerRequiredValidation(
        stakingScenarioInteractor: StakingScenarioInteractor
    ) = RewardDestinationControllerRequiredValidation(
        stakingScenarioInteractor = stakingScenarioInteractor,
        accountAddressExtractor = { it.stashState.controllerAddress },
        errorProducer = RewardDestinationValidationFailure::MissingController
    )

    @Provides
    @Singleton
    fun provideRedeemValidationSystem(
        feeValidation: RewardDestinationFeeValidation,
        controllerRequiredValidation: RewardDestinationControllerRequiredValidation
    ) = RewardDestinationValidationSystem(
        CompositeValidation(
            validations = listOf(
                feeValidation,
                controllerRequiredValidation
            )
        )
    )
}
