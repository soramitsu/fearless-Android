package jp.co.soramitsu.feature_staking_impl.domain.validators

import jp.co.soramitsu.common.utils.mapValuesNotNull
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.feature_staking_api.domain.api.AccountIdMap
import jp.co.soramitsu.feature_staking_api.domain.api.IdentityRepository
import jp.co.soramitsu.feature_staking_api.domain.model.Collator
import jp.co.soramitsu.feature_staking_impl.data.repository.StakingConstantsRepository
import jp.co.soramitsu.feature_staking_impl.domain.rewards.RewardCalculatorFactory
import jp.co.soramitsu.feature_staking_impl.presentation.mappers.toCollator
import jp.co.soramitsu.feature_staking_impl.scenarios.parachain.StakingParachainScenarioRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain

class CollatorProvider(
    private val stakingParachainScenarioRepository: StakingParachainScenarioRepository,
    private val identityRepository: IdentityRepository,
    private val rewardCalculatorFactory: RewardCalculatorFactory,
    private val stakingConstantsRepository: StakingConstantsRepository
) {

    suspend fun getCollators(
        chain: Chain
    ): AccountIdMap<Collator> {
        val chainId = chain.id
        val rewardCalculator = rewardCalculatorFactory.createSubquery()

        val maxTopDelegationsPerCandidate = stakingConstantsRepository.maxTopDelegationsPerCandidate(chainId)
        val maxBottomDelegationsPerCandidate = stakingConstantsRepository.maxBottomDelegationsPerCandidate(chainId)
        val selectedCandidates = stakingParachainScenarioRepository.getSelectedCandidates(chainId) ?: return emptyMap()
        val candidateInfos = stakingParachainScenarioRepository.getCandidateInfos(chainId, selectedCandidates)
        val identityInfo = identityRepository.getIdentitiesFromIdsBytes(chain, selectedCandidates)
        val apy = selectedCandidates.map {
            val accountId = it.toHexString(true)
            accountId to rewardCalculator.getApyFor(accountId)
        }.toMap()

        val collators = candidateInfos.mapValuesNotNull {
            it.value.toCollator(it.key, identityInfo[it.key], apy[it.key])
        }
        return collators
    }
}
