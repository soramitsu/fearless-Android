package jp.co.soramitsu.wallet.impl.data.network.subquery

import jp.co.soramitsu.common.BuildConfig
import jp.co.soramitsu.common.data.network.subquery.GiantsquidResponse
import jp.co.soramitsu.common.data.network.subquery.SubQueryResponse
import jp.co.soramitsu.common.data.network.subquery.SubsquidResponse
import jp.co.soramitsu.wallet.impl.data.network.model.request.GiantsquidHistoryRequest
import jp.co.soramitsu.wallet.impl.data.network.model.request.SubqueryHistoryRequest
import jp.co.soramitsu.wallet.impl.data.network.model.request.SubsquidHistoryRequest
import jp.co.soramitsu.wallet.impl.data.network.model.response.EtherscanHistoryResponse
import jp.co.soramitsu.wallet.impl.data.network.model.response.GiantsquidHistoryResponse
import jp.co.soramitsu.wallet.impl.data.network.model.response.OkLinkHistoryResponse
import jp.co.soramitsu.wallet.impl.data.network.model.response.SubqueryHistoryElementResponse
import jp.co.soramitsu.wallet.impl.data.network.model.response.SubsquidHistoryElementsConnectionResponse
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

    @GET("//api.coingecko.com/api/v3/simple/supported_vs_currencies")
    suspend fun getSupportedCurrencies(): List<String>

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

//    https://www.oklink.com/api/v5/explorer/address/transaction-list?chainShortName=X1_TEST&address=0xb107c568224F4CD0619b40D5d91237A512B6f2F5
//    queryItems?.append(URLQueryItem(name: "symbol", value: chainAsset.asset.symbol.lowercased()))

    @GET
    @Headers("Ok-Access-Key: ${BuildConfig.OKLINK_API_KEY}")
    suspend fun getOkLinkOperationsHistory(
        @Url url: String,
        @Query("address") address: String,
        @Query("symbol") symbol: String,
    ): OkLinkHistoryResponse
}