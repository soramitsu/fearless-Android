package jp.co.soramitsu.staking.impl.data.network.subquery

import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.staking.impl.data.network.subquery.request.StakingDelegatorHistoryRequest
import jp.co.soramitsu.staking.impl.domain.model.Unbonding
import jp.co.soramitsu.staking.impl.domain.model.toUnbonding

class SubQueryDelegationHistoryFetcher(
    private val stakingApi: StakingApi,
    private val chainRegistry: ChainRegistry
) {

    suspend fun fetchDelegationHistory(chainId: ChainId, delegatorAddress: String, collatorAddress: String): List<Unbonding> {
        val chain = chainRegistry.getChain(chainId)
        val stakingUrl = chain.externalApi?.staking?.url
        if (stakingUrl == null || chain.externalApi?.staking?.type != Chain.ExternalApi.Section.Type.SUBQUERY) {
            throw Exception("Staking for this network is not supported yet")
        }

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
