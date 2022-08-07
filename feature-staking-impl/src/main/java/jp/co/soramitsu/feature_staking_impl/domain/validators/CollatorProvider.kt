package jp.co.soramitsu.feature_staking_impl.domain.validators

import jp.co.soramitsu.common.utils.mapValuesNotNull
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.domain.model.accountId
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
    private val stakingConstantsRepository: StakingConstantsRepository,
    private val accountRepository: AccountRepository
) {

    suspend fun getCollators(
        chain: Chain
    ): AccountIdMap<Collator> {
        val chainId = chain.id
        val rewardCalculator = rewardCalculatorFactory.createSubquery()

        val usedCollatorIds = accountRepository.getSelectedMetaAccount().accountId(chain)?.let { accountId ->
            val state = stakingParachainScenarioRepository.getDelegatorState(chainId, accountId)
            state?.delegations?.map { it.owner }
        } ?: emptyList()

        val maxTopDelegationsPerCandidate = stakingConstantsRepository.maxTopDelegationsPerCandidate(chainId)
        val maxBottomDelegationsPerCandidate = stakingConstantsRepository.maxBottomDelegationsPerCandidate(chainId)
        val selectedCandidates =
            stakingParachainScenarioRepository.getSelectedCandidates(chainId)?.filter { candidate -> usedCollatorIds.any { it.contentEquals(candidate) }.not() }
                ?: return emptyMap()
        val candidateInfos = stakingParachainScenarioRepository.getCandidateInfos(chainId, selectedCandidates)
        val identityInfo = identityRepository.getIdentitiesFromIdsBytes(chain, selectedCandidates)
        val apyMap = rewardCalculator.getApy(selectedCandidates)

        val collators = candidateInfos.mapValuesNotNull {
            it.value.toCollator(it.key, identityInfo[it.key], apyMap[it.key])
        }
        return collators
    }
}
