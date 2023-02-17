package jp.co.soramitsu.staking.impl.data.network.subquery

import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.staking.impl.data.network.subquery.request.StakingDelegatorHistoryRequest
import jp.co.soramitsu.staking.impl.data.network.subquery.request.SubsquidDelegatorHistoryRequest
import jp.co.soramitsu.staking.impl.domain.model.Unbonding
import jp.co.soramitsu.staking.impl.domain.model.toUnbonding

class SubQueryDelegationHistoryFetcher(
    private val stakingApi: StakingApi,
    private val chainRegistry: ChainRegistry
) {

    suspend fun fetchDelegationHistory(chainId: ChainId, delegatorAddress: String, collatorAddress: String): List<Unbonding> {
        val chain = chainRegistry.getChain(chainId)
        val stakingUrl = chain.externalApi?.staking?.url
        val stakingType = chain.externalApi?.staking?.type

        return when {
            stakingUrl == null -> throw Exception("Staking for this network is not supported yet")
            stakingType == Chain.ExternalApi.Section.Type.SUBQUERY -> {
                getSubqueryUnbondings(stakingUrl, delegatorAddress, collatorAddress)
            }
            stakingType == Chain.ExternalApi.Section.Type.SUBSQUID -> {
                getSubsquidUnbondings(stakingUrl, delegatorAddress, collatorAddress)
            }
            else -> throw Exception("Staking for this network is not supported yet")
        }
    }

    private suspend fun getSubsquidUnbondings(
        stakingUrl: String,
        delegatorAddress: String,
        collatorAddress: String
    ): List<Unbonding> {
        val delegatorHistory = kotlin.runCatching {
            stakingApi.getDelegatorHistory(
                stakingUrl,
                SubsquidDelegatorHistoryRequest(delegatorAddress, collatorAddress)
            )
        }.getOrNull()

        return delegatorHistory?.data?.rewards?.map {
            it.toUnbonding()
        }?.distinct() ?: emptyList()
    }

    private suspend fun getSubqueryUnbondings(
        stakingUrl: String,
        delegatorAddress: String,
        collatorAddress: String
    ): List<Unbonding> {
        val delegatorHistory = kotlin.runCatching {
            stakingApi.getDelegatorHistory(
                stakingUrl,
                StakingDelegatorHistoryRequest(delegatorAddress, collatorAddress)
            )
        }.getOrNull()

        return delegatorHistory?.data?.delegatorHistoryElements?.nodes?.map {
            it.toUnbonding()
        }?.distinct() ?: emptyList()
    }
}
