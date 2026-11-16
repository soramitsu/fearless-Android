package jp.co.soramitsu.staking.impl.domain.recommendations.settings

import androidx.lifecycle.Lifecycle
import jp.co.soramitsu.common.data.memory.ComputationalCache
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.staking.api.data.StakingSharedState
import jp.co.soramitsu.staking.impl.data.repository.StakingConstantsRepository

// One entry per staking type: a shared entry handed a relay chain provider to a parachain caller and back.
private const val PARACHAIN_SETTINGS_PROVIDER_KEY = "PARACHAIN_SETTINGS_PROVIDER_KEY"
private const val RELAYCHAIN_SETTINGS_PROVIDER_KEY = "RELAYCHAIN_SETTINGS_PROVIDER_KEY"

class RecommendationSettingsProviderFactory(
    private val computationalCache: ComputationalCache,
    private val stakingConstantsRepository: StakingConstantsRepository,
    private val sharedState: StakingSharedState
) {

    // Not cached itself: the typed providers are, and a computation awaiting its own cache entry never completes.
    suspend fun create(lifecycle: Lifecycle, stakingType: Asset.StakingType): RecommendationSettingsProvider<*> {
        return when (stakingType) {
            Asset.StakingType.PARACHAIN -> createParachain(lifecycle)
            Asset.StakingType.RELAYCHAIN -> createRelayChain(lifecycle)
            else -> error("Unsupported staking type")
        }
    }

    suspend fun createParachain(lifecycle: Lifecycle): RecommendationSettingsProvider.Parachain {
        return computationalCache.useCache(PARACHAIN_SETTINGS_PROVIDER_KEY, lifecycle) {
            val chainId = sharedState.chainId()
            return@useCache RecommendationSettingsProvider.Parachain(
                maxTopDelegationPerCandidate = stakingConstantsRepository.maxTopDelegationsPerCandidate(chainId),
                maxDelegationsPerDelegator = stakingConstantsRepository.maxDelegationsPerDelegator(chainId)
            )
        }
    }

    suspend fun createRelayChain(lifecycle: Lifecycle): RecommendationSettingsProvider.RelayChain {
        return computationalCache.useCache(RELAYCHAIN_SETTINGS_PROVIDER_KEY, lifecycle) {
            val chainId = sharedState.chainId()
            return@useCache RecommendationSettingsProvider.RelayChain(
                maximumRewardedNominators = stakingConstantsRepository.maxRewardedNominatorPerValidator(chainId),
                maximumValidatorsPerNominator = stakingConstantsRepository.maxValidatorsPerNominator(chainId)
            )
        }
    }
}
