package jp.co.soramitsu.feature_staking_impl.di.validations

import dagger.Module
import dagger.Provides
import jp.co.soramitsu.common.di.scope.FeatureScope
import jp.co.soramitsu.common.validation.CompositeValidation
import jp.co.soramitsu.common.validation.ValidationSystem
import jp.co.soramitsu.feature_staking_api.domain.api.StakingRepository
import jp.co.soramitsu.feature_staking_impl.domain.validations.welcome.WelcomeStakingElectionValidation
import jp.co.soramitsu.feature_staking_impl.domain.validations.welcome.WelcomeStakingMaxNominatorsValidation
import jp.co.soramitsu.feature_staking_impl.domain.validations.welcome.WelcomeStakingValidationFailure

@Module
class WelcomeStakingValidationModule {

    @Provides
    @FeatureScope
    fun provideElectionPeriodValidation(
        stakingRepository: StakingRepository
    ): WelcomeStakingElectionValidation {
        return WelcomeStakingElectionValidation(
            stakingRepository = stakingRepository,
            networkTypeProvider = { it.networkType },
            errorProducer = { WelcomeStakingValidationFailure.Election }
        )
    }

    @Provides
    @FeatureScope
    fun provideMaxNominatorsReachedValidation(
        stakingRepository: StakingRepository
    ) = WelcomeStakingMaxNominatorsValidation(
        stakingRepository = stakingRepository,
        errorProducer = { WelcomeStakingValidationFailure.MaxNominatorsReached }
    )

    @Provides
    @FeatureScope
    fun provideSetupStakingValidationSystem(
        maxNominatorsReachedValidation: WelcomeStakingMaxNominatorsValidation,
        electionPeriodClosedValidation: WelcomeStakingElectionValidation
    ) = ValidationSystem(
        CompositeValidation(
            listOf(
                maxNominatorsReachedValidation,
                electionPeriodClosedValidation
            )
        )
    )
}
