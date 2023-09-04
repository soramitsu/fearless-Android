package jp.co.soramitsu.wallet.impl.data.network.subquery

import com.google.gson.JsonObject
import jp.co.soramitsu.common.data.network.subquery.GiantsquidResponse
import jp.co.soramitsu.common.data.network.subquery.SubQueryResponse
import jp.co.soramitsu.common.data.network.subquery.SubsquidResponse
import jp.co.soramitsu.wallet.impl.data.network.model.request.GiantsquidHistoryRequest
import jp.co.soramitsu.wallet.impl.data.network.model.request.SubqueryHistoryRequest
import jp.co.soramitsu.wallet.impl.data.network.model.request.SubsquidHistoryRequest
import jp.co.soramitsu.wallet.impl.data.network.model.response.EtherscanHistoryResponse
import jp.co.soramitsu.wallet.impl.data.network.model.response.GiantsquidHistoryResponse
import jp.co.soramitsu.wallet.impl.data.network.model.response.SubqueryHistoryElementResponse
import jp.co.soramitsu.wallet.impl.data.network.model.response.SubsquidHistoryResponse
import retrofit2.http.Body
import retrofit2.http.GET
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
    ): SubsquidResponse<SubsquidHistoryResponse>

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
        @Query("address") address: String,
        @Query("page") page: Int = 1,
        @Query("offset") offset: Int = 1000,
        @Query("sort") sort: String = "desc",
    ): EtherscanHistoryResponse
}
//https://api.etherscan.io/api?module=account&address=0x66f0fda0681f33fa2730b93abc5a93bfb479bb18&page=1&offset=100&sort=desc
//&action=txlistinternal
//   &address=0x2c1ba59d6f58433fb1eaee7d20b26ed83bda51a3
//   &startblock=0
//   &endblock=2702578
//   &page=1
//   &offset=10
//   &sort=asc
//   &apikey=YourApiKeyToken