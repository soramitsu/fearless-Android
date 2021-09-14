package jp.co.soramitsu.feature_staking_impl.data.network.subquery

import jp.co.soramitsu.common.data.network.subquery.EraValidatorInfoQueryResponse.EraValidatorInfo.Nodes.Node
import jp.co.soramitsu.common.utils.networkType
import jp.co.soramitsu.feature_staking_api.domain.api.StakingRepository
import jp.co.soramitsu.feature_staking_api.domain.api.historicalEras
import jp.co.soramitsu.feature_staking_impl.data.network.subquery.request.StakingEraValidatorInfosRequest
import jp.co.soramitsu.feature_staking_impl.data.repository.subqueryFearlessApiPath

class SubQueryValidatorSetFetcher(
    private val stakingApi: StakingApi,
    private val stakingRepository: StakingRepository,
) {

    suspend fun fetchAllValidators(stashAccountAddress: String): List<String> {
        val historicalRange = stakingRepository.historicalEras()
        val subqueryPath = stashAccountAddress.networkType().subqueryFearlessApiPath()

        val validatorsInfos = stakingApi.getValidatorsInfo(
            subqueryPath,
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
