package jp.co.soramitsu.staking.impl.domain.recommendations.settings

import androidx.lifecycle.Lifecycle
import jp.co.soramitsu.common.data.memory.ComputationalCache
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.staking.api.data.StakingSharedState
import jp.co.soramitsu.staking.impl.data.repository.StakingConstantsRepository

private const val SETTINGS_PROVIDER_KEY = "SETTINGS_PROVIDER_KEY"

class RecommendationSettingsProviderFactory(
    private val computationalCache: ComputationalCache,
    private val stakingConstantsRepository: StakingConstantsRepository,
    private val sharedState: StakingSharedState
) {

    suspend fun create(lifecycle: Lifecycle, stakingType: Chain.Asset.StakingType): RecommendationSettingsProvider<*> {
        return computationalCache.useCache(SETTINGS_PROVIDER_KEY, lifecycle) {
            return@useCache when (stakingType) {
                Chain.Asset.StakingType.PARACHAIN -> createParachain(lifecycle)
                Chain.Asset.StakingType.RELAYCHAIN -> createRelayChain(lifecycle)
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
