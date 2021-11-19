package jp.co.soramitsu.feature_staking_impl.data.network.subquery

import jp.co.soramitsu.common.data.network.subquery.EraValidatorInfoQueryResponse.EraValidatorInfo.Nodes.Node
import jp.co.soramitsu.common.utils.networkType
import jp.co.soramitsu.feature_staking_api.domain.api.StakingRepository
import jp.co.soramitsu.feature_staking_api.domain.api.historicalEras
import jp.co.soramitsu.feature_staking_impl.data.network.subquery.request.StakingEraValidatorInfosRequest

class SubQueryValidatorSetFetcher(
    private val stakingApi: StakingApi,
    private val stakingRepository: StakingRepository,
) {
    private val cachedSubqueryStakingUrls = mutableMapOf<String, String?>()

    suspend fun fetchAllValidators(stashAccountAddress: String): List<String> {
        val historicalRange = stakingRepository.historicalEras()
        val subqueryUrl = getSubqueryUrl(stashAccountAddress.networkType())

        val validatorsInfos = stakingApi.getValidatorsInfo(
            subqueryUrl,
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

    private suspend fun getSubqueryUrl(networkType: jp.co.soramitsu.core.model.Node.NetworkType): String {
        if (!cachedSubqueryStakingUrls.containsKey(networkType.readableName)) {
            val chains = stakingApi.getChains()
            cachedSubqueryStakingUrls.clear()
            cachedSubqueryStakingUrls += chains.mapNotNull { chain ->
                chain.name?.let { it to chain.externalApi?.get("staking")?.url }
            }.toMap()
        }
        return cachedSubqueryStakingUrls[networkType.readableName]
            ?: throw Exception("$this is not supported for fetching pending rewards via Subquery")
    }
}
