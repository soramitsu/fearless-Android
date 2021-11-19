package jp.co.soramitsu.feature_staking_impl.data.repository.datasource

import jp.co.soramitsu.common.utils.networkType
import jp.co.soramitsu.core.model.Node
import jp.co.soramitsu.core_db.dao.StakingTotalRewardDao
import jp.co.soramitsu.core_db.model.TotalRewardLocal
import jp.co.soramitsu.fearless_utils.extensions.requirePrefix
import jp.co.soramitsu.feature_staking_impl.data.mappers.mapSubqueryHistoryToTotalReward
import jp.co.soramitsu.feature_staking_impl.data.mappers.mapTotalRewardLocalToTotalReward
import jp.co.soramitsu.feature_staking_impl.data.network.subquery.StakingApi
import jp.co.soramitsu.feature_staking_impl.data.network.subquery.request.StakingSumRewardRequest
import jp.co.soramitsu.feature_staking_impl.domain.model.TotalReward
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map

class SubqueryStakingRewardsDataSource(
    private val stakingApi: StakingApi,
    private val stakingTotalRewardDao: StakingTotalRewardDao,
) : StakingRewardsDataSource {
    private val cachedSubqueryStakingUrls = mutableMapOf<String, String?>()

    override suspend fun totalRewardsFlow(accountAddress: String): Flow<TotalReward> {
        return stakingTotalRewardDao.observeTotalRewards(accountAddress)
            .filterNotNull()
            .map(::mapTotalRewardLocalToTotalReward)
    }

    override suspend fun sync(accountAddress: String) {
        val subqueryUrl = getSubqueryUrl(accountAddress.networkType())

        val totalReward = mapSubqueryHistoryToTotalReward(
            stakingApi.getSumReward(
                subqueryUrl,
                StakingSumRewardRequest(accountAddress = accountAddress)
            )
        )

        stakingTotalRewardDao.insert(TotalRewardLocal(accountAddress, totalReward))
    }

    private suspend fun getSubqueryUrl(networkType: Node.NetworkType): String {
        if (!cachedSubqueryStakingUrls.containsKey(networkType.readableName)) {
            val chains = stakingApi.getChains()
            cachedSubqueryStakingUrls.clear()
            cachedSubqueryStakingUrls += chains.mapNotNull { chain ->
                chain.name?.let { it to chain.externalApi?.get("staking")?.url?.requirePrefix("https://") }
            }.toMap()
        }
        return cachedSubqueryStakingUrls[networkType.readableName]
            ?: throw Exception("$this is not supported for fetching pending rewards via Subquery")
    }
}
