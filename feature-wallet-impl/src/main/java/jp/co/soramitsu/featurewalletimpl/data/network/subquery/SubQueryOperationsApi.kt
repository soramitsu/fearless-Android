package jp.co.soramitsu.featurewalletimpl.data.network.subquery

import jp.co.soramitsu.common.data.network.subquery.SubQueryResponse
import jp.co.soramitsu.featurewalletimpl.data.network.model.request.SubqueryHistoryRequest
import jp.co.soramitsu.featurewalletimpl.data.network.model.response.SubqueryHistoryElementResponse
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Url

interface SubQueryOperationsApi {

    @POST
    suspend fun getOperationsHistory(
        @Url url: String,
        @Body body: SubqueryHistoryRequest
    ): SubQueryResponse<SubqueryHistoryElementResponse>
}
