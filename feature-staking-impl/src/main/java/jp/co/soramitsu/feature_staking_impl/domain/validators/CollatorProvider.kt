package jp.co.soramitsu.feature_staking_impl.domain.validators

import jp.co.soramitsu.common.utils.mapValuesNotNull
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.feature_staking_api.domain.api.AccountIdMap
import jp.co.soramitsu.feature_staking_api.domain.api.IdentityRepository
import jp.co.soramitsu.feature_staking_api.domain.model.Collator
import jp.co.soramitsu.feature_staking_impl.data.repository.StakingConstantsRepository
import jp.co.soramitsu.feature_staking_impl.domain.rewards.RewardCalculatorFactory
import jp.co.soramitsu.feature_staking_impl.presentation.mappers.toCollator
import jp.co.soramitsu.feature_staking_impl.scenarios.StakingParachainScenarioRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import kotlinx.coroutines.flow.first

class CollatorProvider(
    private val stakingParachainScenarioRepository: StakingParachainScenarioRepository,
    private val identityRepository: IdentityRepository,
    private val rewardCalculatorFactory: RewardCalculatorFactory,
    private val stakingConstantsRepository: StakingConstantsRepository,
) {

    suspend fun getCollators(
        chain: Chain,
//        source: ValidatorSource,
//        cachedExposures: AccountIdMap<Exposure>? = null,
    ): AccountIdMap<Collator> {
        val chainId = chain.id

        val maxTopDelegationsPerCandidate = stakingConstantsRepository.maxTopDelegationsPerCandidate(chainId)
        val maxBottomDelegationsPerCandidate = stakingConstantsRepository.maxBottomDelegationsPerCandidate(chainId)
        val selectedCandidates = stakingParachainScenarioRepository.observeSelectedCandidates(chainId).first()
            .plus("0xDA42293efa4a1bEd74B37317979BA14CE1D242b1".fromHex())
        val candidateInfos = stakingParachainScenarioRepository.getCandidateInfos(chainId, selectedCandidates)
        val identityInfo = identityRepository.getIdentitiesFromIdsBytes(chain, selectedCandidates)
        val collators = candidateInfos.mapValuesNotNull {
            it.value?.toCollator(it.key, identityInfo[it.key])
        }

        return collators
    }
}
