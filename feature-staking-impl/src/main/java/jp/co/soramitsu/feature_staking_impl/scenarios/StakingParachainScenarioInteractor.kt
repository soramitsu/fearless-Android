package jp.co.soramitsu.feature_staking_impl.scenarios

import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.domain.model.accountId
import jp.co.soramitsu.feature_staking_api.domain.api.IdentityRepository
import jp.co.soramitsu.feature_staking_api.domain.api.StakingRepository
import jp.co.soramitsu.feature_staking_api.domain.model.Identity
import jp.co.soramitsu.feature_staking_api.domain.model.Round
import jp.co.soramitsu.feature_staking_api.domain.model.StakingState
import jp.co.soramitsu.feature_staking_impl.data.repository.StakingConstantsRepository
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractor
import jp.co.soramitsu.feature_staking_impl.domain.getSelectedChain
import jp.co.soramitsu.feature_staking_impl.domain.model.DelegatorStatus
import jp.co.soramitsu.feature_staking_impl.domain.model.NetworkInfo
import jp.co.soramitsu.feature_staking_impl.domain.model.StakeSummary
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flowOf

class StakingParachainScenarioInteractor(
    private val stakingInteractor: StakingInteractor,
    private val accountRepository: AccountRepository,
    private val stakingConstantsRepository: StakingConstantsRepository,
    private val stakingRepository: StakingRepository,
    private val identityRepositoryImpl: IdentityRepository
) : StakingScenarioInteractor {

    override suspend fun observeNetworkInfoState(): Flow<NetworkInfo> {
        val chainId = stakingInteractor.getSelectedChain().id
        val lockupPeriod = getParachainLockupPeriodInDays(chainId)
        val minimumStakeInPlanks = stakingConstantsRepository.parachainMinimumStaking(chainId)

        return flowOf(
            NetworkInfo.Parachain(
                lockupPeriodInDays = lockupPeriod,
                minimumStake = minimumStakeInPlanks
            )
        )
    }

    private suspend fun getParachainLockupPeriodInDays(chainId: ChainId): Int {
        val hoursInRound = hoursInRound[chainId] ?: return 0
        val lockupPeriodInRounds = stakingConstantsRepository.parachainLockupPeriodInRounds(chainId).toInt()
        val lockupPeriodInHours = lockupPeriodInRounds * hoursInRound
        return lockupPeriodInHours.toDuration(DurationUnit.HOURS).toInt(DurationUnit.DAYS)
    }

    val hoursInRound = mapOf(
        "fe58ea77779b7abda7da4ec526d14db9b1e9cd40a217c34892af80a9b332b76d" to 6, // moonbeam
        "401a1f9dca3da46f5c4091016c8a2f26dcea05865116b286f60f668207d1474b" to 2, // moonriver
        "91bc6e169807aaa54802737e1c504b2577d4fafedd5a02c10293b1cd60e39527" to 2 // moonbase
    )

    override suspend fun getStakingStateFlow(): Flow<StakingState> {
        return combine(
            stakingInteractor.selectedChainFlow(),
            stakingInteractor.currentAssetFlow()
        ) { chain, asset -> chain to asset }.flatMapConcat { (chain, asset) ->
            val accountId = accountRepository.getSelectedMetaAccount().accountId(chain) ?: error("cannot find accountId")
            stakingRepository.observeParachainState(chain, accountId)
        }
    }

    fun observeDelegatorSummary(delegatorState: StakingState.Parachain.Delegator): Flow<StakeSummary<DelegatorStatus>> {
        return emptyFlow()
    }

    suspend fun getIdentities(collatorsIds: List<AccountId>): Map<String, Identity?> {
        if (collatorsIds.isEmpty()) return emptyMap()
        val chainId = stakingInteractor.getSelectedChain().id
        return identityRepositoryImpl.getIdentitiesFromIds(chainId, collatorsIds.map { it.toHexString(false) })
    }

    suspend fun getCurrentRound(chainId: ChainId): Round {
        return stakingRepository.getCurrentRound(chainId)
    }
}
