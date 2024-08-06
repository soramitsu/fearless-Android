package jp.co.soramitsu.wallet.impl.data.network.subquery

import jp.co.soramitsu.common.BuildConfig
import jp.co.soramitsu.common.data.network.subquery.GiantsquidResponse
import jp.co.soramitsu.common.data.network.subquery.SubQueryResponse
import jp.co.soramitsu.common.data.network.subquery.SubsquidResponse
import jp.co.soramitsu.wallet.impl.data.network.model.request.GiantsquidHistoryRequest
import jp.co.soramitsu.wallet.impl.data.network.model.request.ReefHistoryRequest
import jp.co.soramitsu.wallet.impl.data.network.model.request.SubqueryHistoryRequest
import jp.co.soramitsu.wallet.impl.data.network.model.request.SubsquidHistoryRequest
import jp.co.soramitsu.wallet.impl.data.network.model.response.AtletaHistoryResponse
import jp.co.soramitsu.wallet.impl.data.network.model.response.EtherscanHistoryResponse
import jp.co.soramitsu.wallet.impl.data.network.model.response.FiveireHistoryResponse
import jp.co.soramitsu.wallet.impl.data.network.model.response.GiantsquidHistoryResponse
import jp.co.soramitsu.wallet.impl.data.network.model.response.KlaytnHistoryResponse
import jp.co.soramitsu.wallet.impl.data.network.model.response.OkLinkHistoryResponse
import jp.co.soramitsu.wallet.impl.data.network.model.response.ReefHistoryResponse
import jp.co.soramitsu.wallet.impl.data.network.model.response.SubqueryHistoryElementResponse
import jp.co.soramitsu.wallet.impl.data.network.model.response.SubsquidHistoryElementsConnectionResponse
import jp.co.soramitsu.wallet.impl.data.network.model.response.VicscanHistoryResponse
import jp.co.soramitsu.wallet.impl.data.network.model.response.ZchainHistoryResponse
import jp.co.soramitsu.wallet.impl.data.network.model.response.ZetaHistoryResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query
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
    ): SubsquidResponse<SubsquidHistoryElementsConnectionResponse>

    @POST
    suspend fun getGiantsquidOperationsHistory(
        @Url url: String,
        @Body body: GiantsquidHistoryRequest
    ): GiantsquidResponse<GiantsquidHistoryResponse>

    @GET
    suspend fun getEtherscanOperationsHistory(
        @Url url: String,
        @Query("module") module: String = "account",
        @Query("action") action: String = "txlist",
        @Query("contractAddress") contractAddress: String? = null,
        @Query("address") address: String,
        @Query("page") page: Int = 1,
        @Query("offset") offset: Int = 1000,
        @Query("sort") sort: String = "desc",
        @Query("apikey") apiKey: String? = null
    ): EtherscanHistoryResponse

    @GET
    @Headers("Ok-Access-Key: ${BuildConfig.OKLINK_API_KEY}")
    suspend fun getOkLinkOperationsHistory(
        @Url url: String,
        @Query("address") address: String,
        @Query("symbol") symbol: String,
    ): OkLinkHistoryResponse

    @GET
    suspend fun getZetaOperationsHistory(
        @Url url: String
    ): ZetaHistoryResponse

    @GET
    suspend fun getAtletaOperationsHistory(
        @Url url: String
    ): AtletaHistoryResponse

    @GET
    suspend fun getKlaytnOperationsHistory(
        @Url url: String,
        @Query("page") page: Int
    ): KlaytnHistoryResponse

    @GET
    suspend fun getFiveireOperationsHistory(
        @Url url: String,
        @Query("page") page: Int,
        @Query("limit") limit: Int
    ): FiveireHistoryResponse

    @GET
    suspend fun getZchainOperationsHistory(
        @Url url: String,
        @Query("a") address: String,
        @Query("page") page: Int,
        @Query("page_size") pageSize: Int,
    ): ZchainHistoryResponse

    @GET
    suspend fun getVicscanOperationsHistory(
        @Url url: String,
        @Query("account") account: String,
        @Query("limit") limit: Int,
        @Query("offset") offset: Int
    ): VicscanHistoryResponse

    @POST
    suspend fun getReefOperationsHistory(
        @Url url: String,
        @Body body: ReefHistoryRequest
    ): SubsquidResponse<ReefHistoryResponse>
}