package jp.co.soramitsu.wallet.impl.data.network.subquery

import jp.co.soramitsu.common.data.network.subquery.GiantsquidResponse
import jp.co.soramitsu.common.data.network.subquery.SubQueryResponse
import jp.co.soramitsu.common.data.network.subquery.SubsquidResponse
import jp.co.soramitsu.wallet.impl.data.network.model.request.GiantsquidHistoryRequest
import jp.co.soramitsu.wallet.impl.data.network.model.request.SubqueryHistoryRequest
import jp.co.soramitsu.wallet.impl.data.network.model.request.SubsquidHistoryRequest
import jp.co.soramitsu.wallet.impl.data.network.model.response.GiantsquidHistoryResponse
import jp.co.soramitsu.wallet.impl.data.network.model.response.SubqueryHistoryElementResponse
import jp.co.soramitsu.wallet.impl.data.network.model.response.SubsquidHistoryResponse
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Url

interface OperationsHistoryApi {

    @POST
    suspend fun getOperationsHistory(
        @Url url: String,
        @Body body: SubqueryHistoryRequest
    ): SubQueryResponse<SubqueryHistoryElementResponse>

    @POST
    suspend fun getSubsquidOperationsHistory(
        @Url url: String,
        @Body body: SubsquidHistoryRequest
    ): SubsquidResponse<SubsquidHistoryResponse>

    @POST
    suspend fun getGiantsquidOperationsHistory(
        @Url url: String,
        @Body body: GiantsquidHistoryRequest
    ): GiantsquidResponse<GiantsquidHistoryResponse>
}
