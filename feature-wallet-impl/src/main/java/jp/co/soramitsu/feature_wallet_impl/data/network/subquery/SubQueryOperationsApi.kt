package jp.co.soramitsu.feature_wallet_impl.data.network.subquery

import jp.co.soramitsu.common.data.network.subquery.SubQueryResponse
import jp.co.soramitsu.feature_wallet_impl.data.network.model.request.SubqueryHistoryRequest
import jp.co.soramitsu.feature_wallet_impl.data.network.model.response.SubqueryHistoryElementResponse
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

interface SubQueryOperationsApi {

    @POST("//api.subquery.network/sq/ef1rspb/{path}")
    suspend fun getOperationsHistory(
        @Path("path") path: String,
        @Body body: SubqueryHistoryRequest
    ): SubQueryResponse<SubqueryHistoryElementResponse>
}
