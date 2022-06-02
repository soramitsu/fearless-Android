package jp.co.soramitsu.feature_staking_impl.di.validations

import dagger.Module
import dagger.Provides
import jp.co.soramitsu.common.di.scope.FeatureScope
import jp.co.soramitsu.common.validation.CompositeValidation
import jp.co.soramitsu.common.validation.ValidationSystem
import jp.co.soramitsu.feature_staking_impl.data.StakingSharedState
import jp.co.soramitsu.feature_staking_impl.domain.validations.welcome.WelcomeStakingMaxNominatorsValidation
import jp.co.soramitsu.feature_staking_impl.domain.validations.welcome.WelcomeStakingValidationFailure
import jp.co.soramitsu.feature_staking_impl.scenarios.StakingRelayChainScenarioRepository

@Module
class WelcomeStakingValidationModule {

    @Provides
    @FeatureScope
    fun provideMaxNominatorsReachedValidation(
        stakingSharedState: StakingSharedState,
        stakingRepository: StakingRelayChainScenarioRepository
    ) = WelcomeStakingMaxNominatorsValidation(
        stakingRepository = stakingRepository,
        errorProducer = { WelcomeStakingValidationFailure.MAX_NOMINATORS_REACHED },
        isAlreadyNominating = { false },
        sharedState = stakingSharedState
    )

    @Provides
    @FeatureScope
    fun provideSetupStakingValidationSystem(
        maxNominatorsReachedValidation: WelcomeStakingMaxNominatorsValidation
    ) = ValidationSystem(
        CompositeValidation(
            listOf(
                maxNominatorsReachedValidation,
            )
        )
    )
}
