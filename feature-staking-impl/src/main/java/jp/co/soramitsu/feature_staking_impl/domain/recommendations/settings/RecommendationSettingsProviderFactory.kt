package jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings

import androidx.lifecycle.Lifecycle
import jp.co.soramitsu.common.data.memory.ComputationalCache
import jp.co.soramitsu.feature_staking_impl.data.StakingSharedState
import jp.co.soramitsu.feature_staking_impl.data.repository.StakingConstantsRepository
import jp.co.soramitsu.runtime.state.chain

private const val SETTINGS_PROVIDER_KEY = "SETTINGS_PROVIDER_KEY"

class RecommendationSettingsProviderFactory(
    private val computationalCache: ComputationalCache,
    private val stakingConstantsRepository: StakingConstantsRepository,
    private val sharedState: StakingSharedState,
) {

    suspend fun create(lifecycle: Lifecycle): RecommendationSettingsProvider {
        return computationalCache.useCache(SETTINGS_PROVIDER_KEY, lifecycle) {
            val chainId = sharedState.chainId()
            when (sharedState.chain().isEthereumBased) {
                true -> RecommendationSettingsProvider(
                    maximumRewardedNominators = stakingConstantsRepository.maxTopDelegationsPerCandidate(chainId),
                    maximumValidatorsPerNominator = stakingConstantsRepository.maxDelegationsPerDelegator(chainId)
                )

                else -> RecommendationSettingsProvider(
                    maximumRewardedNominators = stakingConstantsRepository.maxRewardedNominatorPerValidator(chainId),
                    maximumValidatorsPerNominator = stakingConstantsRepository.maxValidatorsPerNominator(chainId)
                )
            }
        }
    }
}
