package jp.co.soramitsu.feature_staking_impl.data.network.subquery

import jp.co.soramitsu.common.data.network.subquery.EraValidatorInfoQueryResponse
import jp.co.soramitsu.common.data.network.subquery.SubQueryResponse
import jp.co.soramitsu.common.data.network.subquery.TransactionHistoryRemote
import jp.co.soramitsu.feature_staking_impl.data.network.subquery.request.StakingEraValidatorInfosRequest
import jp.co.soramitsu.feature_staking_impl.data.network.subquery.request.StakingSumRewardRequest
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

interface StakingApi {

    @POST("//api.subquery.network/sq/ef1rspb/{path}")
    suspend fun getSumReward(
        @Path("path") path: String,
        @Body body: StakingSumRewardRequest
    ): SubQueryResponse<TransactionHistoryRemote>

    @POST("//api.subquery.network/sq/ef1rspb/{path}")
    suspend fun getValidatorsInfo(
        @Path("path") path: String,
        @Body body: StakingEraValidatorInfosRequest
    ): SubQueryResponse<EraValidatorInfoQueryResponse>
}
