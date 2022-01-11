package jp.co.soramitsu.feature_staking_impl.data.network.subquery

import jp.co.soramitsu.common.data.network.subquery.EraValidatorInfoQueryResponse.EraValidatorInfo.Nodes.Node
import jp.co.soramitsu.feature_staking_api.domain.api.StakingRepository
import jp.co.soramitsu.feature_staking_api.domain.api.historicalEras
import jp.co.soramitsu.feature_staking_impl.data.network.subquery.request.StakingEraValidatorInfosRequest
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId

class SubQueryValidatorSetFetcher(
    private val stakingApi: StakingApi,
    private val stakingRepository: StakingRepository,
    private val chainRegistry: ChainRegistry
) {

    suspend fun fetchAllValidators(chainId: ChainId, stashAccountAddress: String): List<String> {
        val historicalRange = stakingRepository.historicalEras(chainId)

        val chain = chainRegistry.getChain(chainId)
        val stakingUrl = chain.externalApi?.staking?.url
        if (stakingUrl == null || chain.externalApi?.staking?.type != Chain.ExternalApi.Section.Type.SUBQUERY) {
            throw Exception("Pending rewards for this network is not supported yet")
        }

        val validatorsInfos = stakingApi.getValidatorsInfo(
            stakingUrl,
            StakingEraValidatorInfosRequest(
                eraFrom = historicalRange.first(),
                eraTo = historicalRange.last(),
                accountAddress = stashAccountAddress
            )
        )

        return validatorsInfos.data.query?.eraValidatorInfos?.nodes?.map(
            Node::address
        )?.distinct().orEmpty()
    }
}
