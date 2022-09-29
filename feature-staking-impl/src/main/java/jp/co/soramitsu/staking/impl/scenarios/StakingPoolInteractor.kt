package jp.co.soramitsu.staking.impl.scenarios

import java.math.BigInteger
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.accountId
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.runtime.ext.utilityAsset
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.staking.api.domain.api.IdentityRepository
import jp.co.soramitsu.staking.api.domain.model.Identity
import jp.co.soramitsu.staking.api.domain.model.NominationPool
import jp.co.soramitsu.staking.api.domain.model.PoolInfo
import jp.co.soramitsu.staking.api.domain.model.PoolUnbonding
import jp.co.soramitsu.staking.api.domain.model.StakingState
import jp.co.soramitsu.staking.impl.data.model.BondedPool
import jp.co.soramitsu.staking.impl.data.model.PoolMember
import jp.co.soramitsu.staking.impl.data.model.PoolRewards
import jp.co.soramitsu.staking.impl.data.repository.StakingPoolApi
import jp.co.soramitsu.staking.impl.data.repository.StakingPoolDataSource
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.staking.impl.domain.getSelectedChain
import jp.co.soramitsu.staking.impl.scenarios.relaychain.StakingRelayChainScenarioRepository
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletConstants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class StakingPoolInteractor(
    private val api: StakingPoolApi,
    private val dataSource: StakingPoolDataSource,
    private val stakingInteractor: StakingInteractor,
    private val relayChainRepository: StakingRelayChainScenarioRepository,
    private val accountRepository: AccountRepository,
    private val identitiesRepositoryImpl: IdentityRepository,
    private val walletConstants: WalletConstants
) {

    fun stakingStateFlow(): Flow<StakingState> {
        return stakingInteractor.selectedChainFlow().filter { it.supportStakingPool }.flatMapConcat { chain ->
            val accountId = accountRepository.getSelectedMetaAccount().accountId(chain) ?: error("cannot find accountId")
            stakingPoolStateFlow(chain, accountId)
        }
    }

    private fun stakingPoolStateFlow(chain: Chain, accountId: AccountId): Flow<StakingState> {
        return observeCurrentPool(chain, accountId).map {
            when (it) {
                null -> StakingState.Pool.None(chain, accountId)
                else -> StakingState.Pool.Member(chain, accountId, it)
            }
        }.runCatching { this }.getOrDefault(emptyFlow())
    }

    fun observeCurrentPool(
        chain: Chain,
        accountId: AccountId
    ): Flow<NominationPool?> {
        return dataSource.observePoolMembers(chain.id, accountId).flatMapConcat { poolMember ->
            poolMember ?: return@flatMapConcat flowOf(null)
            combine(dataSource.observePool(chain.id, poolMember.poolId), dataSource.observePoolRewards(chain.id, poolMember.poolId)) { bondedPool, rewardPool ->
                bondedPool ?: return@combine null
                val pendingRewards = calculatePendingRewards(chain, poolMember, bondedPool, rewardPool)

                val currentEra = relayChainRepository.getCurrentEraIndex(chain.id)
                val name = dataSource.getPoolMetadata(chain.id, poolMember.poolId)
                val unbondingEras = poolMember.unbondingEras.map { PoolUnbonding(it.era, it.amount) }
                val redeemable = unbondingEras.filter { it.era < currentEra }.sumOf { it.amount }
                val unbonding = unbondingEras.filter { it.era > currentEra }.sumOf { it.amount }
                bondedPool.toNominationPool(poolMember, name, unbondingEras, redeemable, unbonding, poolMember.points, pendingRewards)
            }
        }
    }

    private suspend fun calculatePendingRewards(chain: Chain, poolMember: PoolMember, bondedPool: BondedPool, rewardPool: PoolRewards?): BigInteger {
        rewardPool ?: return BigInteger.ZERO
        val rewardsAccountId = generatePoolRewardAccount(chain, poolMember.poolId)
        val existentialDeposit = walletConstants.existentialDeposit(chain.utilityAsset)
        val rewardsAccountBalance = stakingInteractor.getAccountBalance(chain.id, rewardsAccountId).data.free.subtract(existentialDeposit)
        val payoutSinceLastRecord = rewardsAccountBalance.add(rewardPool.totalRewardsClaimed).subtract(rewardPool.lastRecordedTotalPayouts)
        val rewardCounterBase = BigInteger.valueOf(10).pow(18)
        val currentRewardCounter = payoutSinceLastRecord.multiply(rewardCounterBase).divide(bondedPool.points).add(rewardPool.lastRecordedRewardCounter)
        return currentRewardCounter.subtract(rewardPool.lastRecordedRewardCounter).multiply(poolMember.points).divide(rewardCounterBase)
    }

    private suspend fun generatePoolRewardAccount(chain: Chain, poolId: BigInteger): ByteArray {
        return generatePoolAccountId(1, chain, poolId)
    }

    private suspend fun generatePoolStashAccount(chain: Chain, poolId: BigInteger): ByteArray {
        return generatePoolAccountId(0, chain, poolId)
    }

    private suspend fun generatePoolAccountId(index: Int, chain: Chain, poolId: BigInteger): ByteArray {
        val palletId = relayChainRepository.getNominationPoolPalletId(chain.id)
        val modPrefix = "modl".toByteArray()
        val indexBytes = byteArrayOf(index.toByte())
        val poolIdBytes = poolId.toByteArray()
        val empty = ByteArray(32)
        val source = modPrefix + palletId + indexBytes + poolIdBytes + empty
        return SS58Encoder.encode(source.take(32).toByteArray(), chain.addressPrefix.toShort()).toAccountId()
    }

    private fun BondedPool.toNominationPool(
        poolMember: PoolMember,
        name: String?,
        unbondingEras: List<PoolUnbonding>,
        redeemable: BigInteger,
        unbonding: BigInteger,
        myStakedPoints: BigInteger,
        myPendingRewards: BigInteger
    ): NominationPool {
        return NominationPool(
            poolId = poolMember.poolId,
            name = name,
            myStakeInPlanks = myStakedPoints,
            totalStakedInPlanks = points,
            lastRecordedRewardCounter = poolMember.lastRecordedRewardCounter,
            state = state,
            redeemable = redeemable,
            unbonding = unbonding,
            unbondingEras = unbondingEras,
            pendingRewards = myPendingRewards,
            members = memberCounter,
            depositor = depositor,
            root = root,
            nominator = nominator,
            stateToggler = stateToggler
        )
    }

    suspend fun getMinToJoinPool(chainId: ChainId): BigInteger {
        return dataSource.minJoinBond(chainId)
    }

    suspend fun getMinToCreate(chainId: ChainId): BigInteger {
        return dataSource.minCreateBond(chainId)
    }

    suspend fun getExistingPools(chainId: ChainId): BigInteger {
        return dataSource.existingPools(chainId)
    }

    suspend fun getPossiblePools(chainId: ChainId): BigInteger {
        return dataSource.maxPools(chainId) ?: BigInteger.ZERO
    }

    suspend fun getMaxMembersInPool(chainId: ChainId): BigInteger {
        return dataSource.maxMembersInPool(chainId) ?: BigInteger.ZERO
    }

    suspend fun getMaxPoolsMembers(chainId: ChainId): BigInteger {
        return dataSource.maxPoolMembers(chainId) ?: BigInteger.ZERO
    }

    suspend fun getPoolMembers(chainId: ChainId, accountId: AccountId): PoolMember? {
        return dataSource.poolMembers(chainId, accountId)
    }

    suspend fun getAllPools(chainId: ChainId): List<PoolInfo> {
        val poolsMetadata = dataSource.poolsMetadata(chainId)
        val pools = dataSource.bondedPools(chainId)
        return pools.mapNotNull { (id, pool) ->
            pool ?: return@mapNotNull null
            val name = poolsMetadata[id] ?: "Pool #$id"
            PoolInfo(
                id,
                name,
                pool.points,
                pool.state,
                pool.memberCounter,
                pool.depositor,
                pool.root,
                pool.nominator,
                pool.stateToggler
            )
        }
    }

    suspend fun getIdentities(accountIds: List<AccountId>): Map<String, Identity?> {
        if (accountIds.isEmpty()) return emptyMap()
        val chain = stakingInteractor.getSelectedChain()
        return identitiesRepositoryImpl.getIdentitiesFromIds(chain, accountIds.map { it.toHexString(false) })
    }

    suspend fun estimateJoinFee(amount: BigInteger, poolId: BigInteger = BigInteger.ZERO) = api.estimateJoinFee(amount, poolId)

    suspend fun joinPool(address: String, amount: BigInteger, poolId: BigInteger) = api.joinPool(address, amount, poolId)

    suspend fun estimateBondMoreFee(amount: BigInteger) = api.estimateBondExtraFee(amount)

    suspend fun bondMore(address: String, amount: BigInteger) = api.bondExtra(address, amount)

    suspend fun estimateUnstakeFee(address: String, amount: BigInteger) = api.estimateUnbondFee(address, amount)

    suspend fun unstake(address: String, amount: BigInteger) = api.unbond(address, amount)

    suspend fun estimateRedeemFee(address: String) = api.estimateWithdrawUnbondedFee(address)

    suspend fun redeem(address: String) = api.withdrawUnbonded(address)

    suspend fun estimateClaimFee() = api.estimateClaimPayoutFee()

    suspend fun claim(address: String) = api.claimPayout(address)
}
