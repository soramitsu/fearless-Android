package jp.co.soramitsu.staking.impl.data.repository.datasource

import java.math.BigInteger
import jp.co.soramitsu.common.base.errors.RewardsNotSupportedWarning
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.common.utils.sumByBigDecimal
import jp.co.soramitsu.common.utils.sumByBigInteger
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.utils.utilityAsset
import jp.co.soramitsu.coredb.dao.StakingTotalRewardDao
import jp.co.soramitsu.coredb.model.TotalRewardLocal
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.shared_utils.extensions.toHexString
import jp.co.soramitsu.shared_utils.ss58.SS58Encoder.toAccountId
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
import jp.co.soramitsu.staking.impl.data.network.subquery.request.SubsquidSoraStakingRewardsRequest
import jp.co.soramitsu.staking.impl.domain.model.TotalReward
import jp.co.soramitsu.wallet.impl.domain.model.planksFromAmount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class SubqueryStakingRewardsDataSource(
    private val stakingApi: StakingApi,
    private val stakingTotalRewardDao: StakingTotalRewardDao,
    private val chainRegistry: ChainRegistry
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
                syncSoraRewards(stakingUrl,accountAddress, requireNotNull(chain.utilityAsset))
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

    private suspend fun syncSoraRewards(
        stakingUrl: String,
        accountAddress: String,
        chainAsset: Asset
    ) = withContext(Dispatchers.IO) {
        val rewards = stakingApi.getSoraRewards(stakingUrl, SubsquidSoraStakingRewardsRequest(accountAddress))
        val totalReward = rewards.data.stakingRewards.sumByBigDecimal { it.amount }
        val totalInPlanks = chainAsset.planksFromAmount(totalReward)
        stakingTotalRewardDao.insert(TotalRewardLocal(accountAddress, totalInPlanks))
    }

    private suspend fun syncReefRewards(stakingUrl: String, accountAddress: String) {
        var hasNextPage = true
        var nextCursor: String? = null
        var rewardsTotal = BigInteger.ZERO

        while (hasNextPage) {
            val response = stakingApi.getReefRewards(stakingUrl, ReefStakingRewardsRequest(accountAddress, offset = nextCursor?.let { "\"$it\"" }))
            hasNextPage = response.data.stakingsConnection.pageInfo.hasNextPage
            nextCursor = response.data.stakingsConnection.pageInfo.endCursor
            rewardsTotal += response.data.stakingsConnection.edges.map { it.node }.sumByBigInteger { it.amount }
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
