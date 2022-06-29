package jp.co.soramitsu.feature_staking_impl.scenarios.parachain

import java.math.BigInteger
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.common.utils.sumByBigInteger
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.domain.model.MetaAccount
import jp.co.soramitsu.feature_account_api.domain.model.accountId
import jp.co.soramitsu.feature_staking_api.domain.api.IdentityRepository
import jp.co.soramitsu.feature_staking_api.domain.model.AtStake
import jp.co.soramitsu.feature_staking_api.domain.model.CandidateInfo
import jp.co.soramitsu.feature_staking_api.domain.model.Identity
import jp.co.soramitsu.feature_staking_api.domain.model.RewardDestination
import jp.co.soramitsu.feature_staking_api.domain.model.Round
import jp.co.soramitsu.feature_staking_api.domain.model.StakingLedger
import jp.co.soramitsu.feature_staking_api.domain.model.StakingState
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.data.repository.StakingConstantsRepository
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractor
import jp.co.soramitsu.feature_staking_impl.domain.getSelectedChain
import jp.co.soramitsu.feature_staking_impl.domain.model.NetworkInfo
import jp.co.soramitsu.feature_staking_impl.domain.model.Unbonding
import jp.co.soramitsu.feature_staking_impl.presentation.staking.balance.model.StakingBalanceModel
import jp.co.soramitsu.feature_staking_impl.scenarios.StakingScenarioInteractor
import jp.co.soramitsu.feature_wallet_api.presentation.model.mapAmountToAmountModel
import jp.co.soramitsu.runtime.ext.accountIdOf
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.state.SingleAssetSharedState
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class StakingParachainScenarioInteractor(
    private val stakingInteractor: StakingInteractor,
    private val accountRepository: AccountRepository,
    private val stakingConstantsRepository: StakingConstantsRepository,
    private val stakingParachainScenarioRepository: StakingParachainScenarioRepository,
    private val identityRepositoryImpl: IdentityRepository,
) : StakingScenarioInteractor {

    override suspend fun observeNetworkInfoState(): Flow<NetworkInfo> {
        val chainId = stakingInteractor.getSelectedChain().id
        val lockupPeriod = getParachainLockupPeriodInDays(chainId)
        val minimumStakeInPlanks = getMinimumStake(chainId)

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

    override fun getStakingStateFlow(): Flow<StakingState> {
        return combine(
            stakingInteractor.selectedChainFlow(),
            stakingInteractor.currentAssetFlow()
        ) { chain, asset -> chain to asset }.flatMapConcat { (chain, asset) ->
            val accountId = accountRepository.getSelectedMetaAccount().accountId(chain) ?: error("cannot find accountId")
            stakingParachainScenarioRepository.stakingStateFlow(chain, accountId)
        }
    }

    suspend fun getIdentities(collatorsIds: List<AccountId>): Map<String, Identity?> {
        if (collatorsIds.isEmpty()) return emptyMap()
        val chain = stakingInteractor.getSelectedChain()
        return identityRepositoryImpl.getIdentitiesFromIds(chain, collatorsIds.map { it.toHexString(false) })
    }

    suspend fun getCurrentRound(chainId: ChainId): Round {
        return stakingParachainScenarioRepository.getCurrentRound(chainId)
    }

    suspend fun getAtStake(chainId: ChainId, collatorId: AccountId): Result<AtStake> {
        return runCatching { stakingParachainScenarioRepository.getAtStakeOfCollator(chainId, collatorId, getCurrentRound(chainId).current) }
    }

    fun selectedAccountStakingStateFlow(
        metaAccount: MetaAccount,
        assetWithChain: SingleAssetSharedState.AssetWithChain
    ) = flow {
        val chain = assetWithChain.chain
        val accountId = metaAccount.accountId(chain)!! // TODO may be null for ethereum chains

        emitAll(stakingParachainScenarioRepository.stakingStateFlow(chain, accountId))
    }

    fun selectedAccountStakingStateFlow() = stakingInteractor.selectionStateFlow().flatMapLatest { (selectedAccount, assetWithChain) ->
        selectedAccountStakingStateFlow(selectedAccount, assetWithChain)
    }

    suspend fun maxDelegationsPerDelegator(): Int {
        return stakingConstantsRepository.maxDelegationsPerDelegator(stakingInteractor.getSelectedChain().id)
    }

    override suspend fun getMinimumStake(chainId: ChainId): BigInteger {
        return stakingConstantsRepository.parachainMinimumStaking(chainId)
    }

    override suspend fun maxNumberOfStakesIsReached(chainId: ChainId): Boolean {
        val chain = stakingInteractor.getChain(chainId)
        val accountId = accountRepository.getSelectedMetaAccount().accountId(chain) ?: error("cannot find accountId")
        val maxDelegations = maxDelegationsPerDelegator()
        val delegatorState = stakingParachainScenarioRepository.getDelegatorState(chainId, accountId)
        val currentDelegationsCount = delegatorState?.delegations?.size ?: return false
        return currentDelegationsCount >= maxDelegations
    }

    override suspend fun currentUnbondingsFlow(): Flow<List<Unbonding>> {
        val chain = stakingInteractor.getSelectedChain()
        val accountId = accountRepository.getSelectedMetaAccount().accountId(chain) ?: error("cannot find accountId")
        return stakingParachainScenarioRepository.stakingStateFlow(chain, accountId).map { stakingState: StakingState ->
            val round = stakingParachainScenarioRepository.getCurrentRound(chain.id)
            (stakingState as? StakingState.Parachain.Delegator)?.delegations?.map {
                it.collatorId // todo SubQuery
            }
            emptyList()
        }
    }

    override suspend fun getSelectedAccountStakingState() = selectedAccountStakingStateFlow().first()

    override suspend fun getStakingBalanceFlow(collatorId: AccountId?): Flow<StakingBalanceModel> {
        collatorId ?: error("cannot find collatorId")
        val chain = stakingInteractor.getSelectedChain()
        val accountId = accountRepository.getSelectedMetaAccount().accountId(chain) ?: error("cannot find accountId")
        val delegatorState = stakingParachainScenarioRepository.getDelegatorState(chain.id, accountId)

        val staked = delegatorState?.delegations?.firstOrNull {
            it.owner.contentEquals(collatorId)
        }?.amount.orZero()

        val currentRound = getCurrentRound(chain.id)

        val delegationScheduledRequests = stakingParachainScenarioRepository.getDelegationScheduledRequests(chain.id, collatorId)
        val userRequests = delegationScheduledRequests?.filter {
            it.delegator.contentEquals(accountId)
        }.orEmpty()
        val unstaking = userRequests.filter {
            it.whenExecutable >= currentRound.current
        }.sumByBigInteger { it.actionValue }

        val readyForUnlocking = userRequests.filter {
            it.whenExecutable < currentRound.current
        }.sumByBigInteger { it.actionValue }

        return stakingInteractor.currentAssetFlow().map { asset ->
            StakingBalanceModel(
                staked = mapAmountToAmountModel(staked, asset, R.string.staking_main_stake_balance_staked),
                unstaking = mapAmountToAmountModel(unstaking, asset, R.string.wallet_balance_unbonding_v1_9_0),
                redeemable = mapAmountToAmountModel(readyForUnlocking, asset, R.string.staking_balance_ready_for_unlocking)
            )
        }
    }

    override fun overrideRedeemActionTitle(): Int = R.string.parachain_staking_unlock

    override suspend fun accountIsNotController(controllerAddress: String): Boolean {
        return true
    }

    override suspend fun ledger(): StakingLedger? {
        return null
    }

    override suspend fun checkAccountRequiredValidation(accountAddress: String?): Boolean {
        accountAddress ?: return true
        val currentStakingState = selectedAccountStakingStateFlow().first()
        val chain = currentStakingState.chain

        return accountRepository.isAccountExists(chain.accountIdOf(accountAddress))
    }

    override suspend fun maxStakersPerBlockProducer(): Int {
        return maxDelegationsPerDelegator()
    }

    override suspend fun unstakingPeriod(): Int {
        val chainId = stakingInteractor.getSelectedChain().id
        return getParachainLockupPeriodInDays(chainId)
    }

    override suspend fun stakePeriodInHours(): Int {
        val chainId = stakingInteractor.getSelectedChain().id
        return hoursInRound[chainId] ?: error("Chain id is not found in round duration map")
    }

    override suspend fun getRewardDestination(accountStakingState: StakingState): RewardDestination {
        require(accountStakingState is StakingState.Parachain)
        return RewardDestination.Payout(accountStakingState.accountId)
    }

    suspend fun getCollator(collatorId: AccountId): CandidateInfo {
        val chainId = stakingInteractor.getSelectedChain().id
        return stakingParachainScenarioRepository.getCandidateInfo(chainId, collatorId)
    }

    suspend fun getIdentity(collatorId: AccountId): Identity? {
        val chain = stakingInteractor.getSelectedChain()
        val identities = identityRepositoryImpl.getIdentitiesFromIdsBytes(chain, listOf(collatorId))
        return identities[collatorId.toHexString()]
    }
}
