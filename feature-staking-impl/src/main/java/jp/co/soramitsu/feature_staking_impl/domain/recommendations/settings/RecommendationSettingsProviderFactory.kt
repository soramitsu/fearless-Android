package jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings

import jp.co.soramitsu.feature_staking_impl.data.repository.StakingConstantsRepository

class RecommendationSettingsProviderFactory(
    private val stakingConstantsRepository: StakingConstantsRepository,
) {

    private var instance: RecommendationSettingsProvider? = null

    @Synchronized
    suspend fun get(): RecommendationSettingsProvider {
        if (instance != null) return instance!!

        instance = RecommendationSettingsProvider(
            maximumRewardedNominators = stakingConstantsRepository.maxRewardedNominatorPerValidatorPrefs(),
            maximumValidatorsPerNominator = stakingConstantsRepository.maxValidatorsPerNominator()
        )

        return instance!!
    }
}
