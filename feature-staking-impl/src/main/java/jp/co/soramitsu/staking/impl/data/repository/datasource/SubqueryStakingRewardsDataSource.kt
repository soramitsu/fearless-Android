package jp.co.soramitsu.staking.impl.data.repository.datasource

import jp.co.soramitsu.common.base.errors.RewardsNotSupportedWarning
import jp.co.soramitsu.common.data.network.subquery.ReefStakingRewardsResponse
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.common.utils.sumByBigInteger
import jp.co.soramitsu.core.utils.utilityAsset
import jp.co.soramitsu.coredb.dao.StakingTotalRewardDao
import jp.co.soramitsu.coredb.model.TotalRewardLocal
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.staking.api.data.SyntheticStakingType
import jp.co.soramitsu.staking.api.data.syntheticStakingType
import jp.co.soramitsu.staking.impl.data.mappers.mapSubqueryHistoryToTotalReward
import jp.co.soramitsu.staking.impl.data.mappers.mapTotalRewardLocalToTotalReward
import jp.co.soramitsu.staking.impl.data.network.subquery.StakingApi
import jp.co.soramitsu.staking.impl.data.network.subquery.request.GiantsquidRewardAmountRequest
import jp.co.soramitsu.staking.impl.data.network.subquery.request.ReefStakingRewardsRequest
import jp.co.soramitsu.staking.impl.data.network.subquery.request.StakingSumRewardRequest
import jp.co.soramitsu.staking.impl.data.network.subquery.request.SubsquidEthRewardAmountRequest
import jp.co.soramitsu.staking.impl.data.network.subquery.request.SubsquidRelayRewardAmountRequest
import jp.co.soramitsu.staking.impl.domain.model.TotalReward
import jp.co.soramitsu.wallet.impl.domain.model.planksFromAmount
import jp.co.soramitsu.xnetworking.lib.datasources.blockexplorer.api.BlockExplorerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.math.BigDecimal
import java.math.BigInteger

private const val REEF_REWARDS_TIMEOUT_MILLIS = 60_000L
private const val MAX_REEF_REWARDS_PAGES = 1000
private const val MAX_REEF_REWARDS_PER_PAGE = 100
private const val MAX_REEF_REWARDS_CURSOR_LENGTH = 4096

class SubqueryStakingRewardsDataSource(
    private val stakingApi: StakingApi,
    private val stakingTotalRewardDao: StakingTotalRewardDao,
    private val chainRegistry: ChainRegistry,
    private val blockExplorerRepository: BlockExplorerRepository,
) : StakingRewardsDataSource {

    override fun totalRewardsFlow(accountAddress: String): Flow<TotalReward> {
        return stakingTotalRewardDao.observeTotalRewards(accountAddress)
            .flowOn(Dispatchers.IO)
            .filterNotNull()
            .map(::mapTotalRewardLocalToTotalReward)
    }

    override suspend fun sync(chainId: ChainId, accountAddress: String) = withContext(Dispatchers.Default) {
        val chain = chainRegistry.getChain(chainId)
        val stakingUrl = chain.externalApi?.staking?.url
        val stakingType = chain.externalApi?.staking?.type
        val syntheticStakingType = chain.utilityAsset?.syntheticStakingType()

        when {
            stakingUrl == null -> throw RewardsNotSupportedWarning()

            syntheticStakingType == SyntheticStakingType.SORA -> {
                val sum = kotlin.runCatching {
                    blockExplorerRepository.getStakingRewarded(chainId, accountAddress).sumOf {
                        it.toBigDecimal()
                    }
                }.getOrNull() ?: BigDecimal.ZERO
                val total = chain.utilityAsset?.planksFromAmount(sum) ?: BigInteger.ZERO
                stakingTotalRewardDao.insert(TotalRewardLocal(accountAddress, total))
            }
            stakingType == Chain.ExternalApi.Section.Type.SUBQUERY -> {
                syncSubquery(stakingUrl, accountAddress)
            }

            stakingType == Chain.ExternalApi.Section.Type.SUBSQUID && chain.isEthereumBased -> {
                syncSubsquidEth(stakingUrl, accountAddress)
            }

            stakingType == Chain.ExternalApi.Section.Type.SUBSQUID -> {
                syncSubsquidRelay(stakingUrl, accountAddress)
            }

            stakingType == Chain.ExternalApi.Section.Type.GIANTSQUID -> {
                syncGiantsquidRelay(stakingUrl, accountAddress)
            }

            stakingType == Chain.ExternalApi.Section.Type.REEF -> {
                syncReefRewards(stakingUrl, accountAddress)
            }

            else -> throw RewardsNotSupportedWarning()
        }
    }

    private suspend fun syncSubsquidEth(stakingUrl: String, accountAddress: String) = withContext(Dispatchers.IO) {
        val rewards = stakingApi.getEthRewardAmounts(stakingUrl, SubsquidEthRewardAmountRequest(accountAddress))
        val totalReward = rewards.data.rewards.sumByBigInteger { it.amount.orZero() }
        stakingTotalRewardDao.insert(TotalRewardLocal(accountAddress, totalReward))
    }

    private suspend fun syncSubsquidRelay(stakingUrl: String, accountAddress: String) = withContext(Dispatchers.IO) {
        val rewards = stakingApi.getRelayRewardAmounts(stakingUrl, SubsquidRelayRewardAmountRequest(accountAddress))
        val totalReward = rewards.data.historyElements.sumByBigInteger { it.reward?.amount.orZero() }
        stakingTotalRewardDao.insert(TotalRewardLocal(accountAddress, totalReward))
    }

    private suspend fun syncGiantsquidRelay(stakingUrl: String, accountAddress: String) = withContext(Dispatchers.IO) {
        val rewards = stakingApi.getRelayRewardAmounts(stakingUrl, GiantsquidRewardAmountRequest(accountAddress.toAccountId().toHexString(true)))
        val totalReward = rewards.data.stakingRewards.sumByBigInteger { it.amount }
        stakingTotalRewardDao.insert(TotalRewardLocal(accountAddress, totalReward))
    }

    private suspend fun syncReefRewards(stakingUrl: String, accountAddress: String) {
        val rewardsTotal = fetchReefRewardsTotal { cursor ->
            stakingApi.getReefRewards(stakingUrl, ReefStakingRewardsRequest(accountAddress, offset = cursor)).data
        }

        stakingTotalRewardDao.insert(TotalRewardLocal(accountAddress, rewardsTotal))
    }

    private suspend fun syncSubquery(stakingUrl: String, accountAddress: String) = withContext(Dispatchers.IO) {
        val r = stakingApi.getSumReward(
            stakingUrl,
            StakingSumRewardRequest(accountAddress = accountAddress)
        )

        val totalReward = mapSubqueryHistoryToTotalReward(
            r
        )
        stakingTotalRewardDao.insert(TotalRewardLocal(accountAddress, totalReward))
    }
}

internal suspend fun fetchReefRewardsTotal(fetchPage: suspend (String?) -> ReefStakingRewardsResponse): BigInteger =
    withTimeout(REEF_REWARDS_TIMEOUT_MILLIS) {
        var cursor: String? = null
        var total = BigInteger.ZERO
        val seenCursors = mutableSetOf<String>()
        val seenRewards = mutableSetOf<String>()
        repeat(MAX_REEF_REWARDS_PAGES) {
            val page = fetchPage(cursor).stakingsConnection
            check(page.edges.size <= MAX_REEF_REWARDS_PER_PAGE) { "Invalid Reef rewards page" }
            page.edges.forEach { edge ->
                check(edge.node.id.isNotEmpty() && seenRewards.add(edge.node.id)) { "Repeated Reef reward" }
                total += edge.node.amount
            }
            if (!page.pageInfo.hasNextPage) return@withTimeout total
            val next = page.pageInfo.endCursor
            check(page.edges.isNotEmpty() && next.isNotEmpty() && next.length <= MAX_REEF_REWARDS_CURSOR_LENGTH && seenCursors.add(next)) {
                "Invalid Reef rewards pagination"
            }
            cursor = next
        }
        error("Reef rewards pagination limit exceeded")
    }
