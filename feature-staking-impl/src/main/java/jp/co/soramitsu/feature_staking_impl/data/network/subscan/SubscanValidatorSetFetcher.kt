package jp.co.soramitsu.feature_staking_impl.data.network.subscan

import com.google.gson.annotations.SerializedName
import jp.co.soramitsu.common.data.network.subquery.EraValidatorInfoQueryResponse.EraValidatorInfo.Nodes.Node
import jp.co.soramitsu.common.utils.networkType
import jp.co.soramitsu.feature_staking_api.domain.api.StakingRepository
import jp.co.soramitsu.feature_staking_api.domain.api.historicalEras
import jp.co.soramitsu.feature_staking_impl.data.network.subscan.request.StakingEraValidatorInfosRequest
import jp.co.soramitsu.feature_staking_impl.data.repository.subqueryFearlessApiPath

internal open class CallArg<T>(
    val name: String,
    val type: String,
    val value: T,
)

internal class BatchCallArg(
    name: String,
    type: String,
    value: List<CallDescription>,
) : CallArg<List<CallDescription>>(name, type, value)

internal typealias BatchParams = List<BatchCallArg>

internal val BatchParams.calls: List<CallDescription>
    get() = first().value

internal typealias GenericParams = List<CallArg<Any?>>

internal class CallDescription(
    @SerializedName("call_args", alternate = ["params"])
    val callArgs: List<CallArg<Any?>>,
    @SerializedName("call_function", alternate = ["call_name"])
    val callFunction: String,
    @SerializedName("call_index")
    val callIndex: String,
    @SerializedName("call_module")
    val callModule: String,
)

class SubscanValidatorSetFetcher(
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
