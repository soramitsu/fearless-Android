package jp.co.soramitsu.feature_staking_impl.domain.recommendations.settings

import androidx.lifecycle.Lifecycle
import jp.co.soramitsu.common.data.memory.ComputationalCache
import jp.co.soramitsu.feature_staking_impl.data.StakingSharedState
import jp.co.soramitsu.feature_staking_impl.data.repository.StakingConstantsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain

private const val SETTINGS_PROVIDER_KEY = "SETTINGS_PROVIDER_KEY"

class RecommendationSettingsProviderFactory(
    private val computationalCache: ComputationalCache,
    private val stakingConstantsRepository: StakingConstantsRepository,
    private val sharedState: StakingSharedState,
) {

    suspend fun create(lifecycle: Lifecycle, stakingType: Chain.Asset.StakingType): RecommendationSettingsProvider<*> {
        return computationalCache.useCache(SETTINGS_PROVIDER_KEY, lifecycle) {
            val chainId = sharedState.chainId()
            return@useCache when (stakingType) {
                Chain.Asset.StakingType.PARACHAIN -> RecommendationSettingsProvider.Parachain(
                    maxTopDelegationPerCandidate = stakingConstantsRepository.maxTopDelegationsPerCandidate(chainId),
                    maxDelegationsPerDelegator = stakingConstantsRepository.maxDelegationsPerDelegator(chainId)
                )

                Chain.Asset.StakingType.RELAYCHAIN -> RecommendationSettingsProvider.RelayChain(
                    maximumRewardedNominators = stakingConstantsRepository.maxRewardedNominatorPerValidator(chainId),
                    maximumValidatorsPerNominator = stakingConstantsRepository.maxValidatorsPerNominator(chainId)
                )

                else -> error("Unsupported staking type")
            }
        }
    }

    suspend fun createParachain(lifecycle: Lifecycle): RecommendationSettingsProvider.Parachain {
        return computationalCache.useCache(SETTINGS_PROVIDER_KEY, lifecycle) {
            val chainId = sharedState.chainId()
            return@useCache RecommendationSettingsProvider.Parachain(
                maxTopDelegationPerCandidate = stakingConstantsRepository.maxTopDelegationsPerCandidate(chainId),
                maxDelegationsPerDelegator = stakingConstantsRepository.maxDelegationsPerDelegator(chainId)
            )
        }
    }
    suspend fun createRelayChain(lifecycle: Lifecycle): RecommendationSettingsProvider.RelayChain {
        return computationalCache.useCache(SETTINGS_PROVIDER_KEY, lifecycle) {
            val chainId = sharedState.chainId()
            return@useCache RecommendationSettingsProvider.RelayChain(
                maximumRewardedNominators = stakingConstantsRepository.maxRewardedNominatorPerValidator(chainId),
                maximumValidatorsPerNominator = stakingConstantsRepository.maxValidatorsPerNominator(chainId)
            )
        }
    }
}
