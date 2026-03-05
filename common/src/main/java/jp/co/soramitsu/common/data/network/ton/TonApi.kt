package jp.co.soramitsu.common.data.network.ton

import jp.co.soramitsu.common.BuildConfig
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Url

interface TonApi {
    @GET
    suspend fun getAccountData(@Url url: String): TonAccountData

    @GET
    suspend fun getJettonBalances(@Url url: String, @Query("currencies") currencies: List<String>?): JettonsBalances

    @GET
    suspend fun getIndexerBalances(@Url url: String): TonIndexerBalancesResponse

    @GET
    suspend fun getIndexerState(@Url url: String): TonIndexerStateResponse

    @POST
    suspend fun runIndexerGetMethod(
        @Url url: String,
        @Body body: TonIndexerRunGetMethodRequest
    ): TonIndexerRunGetMethodResponse

    @GET
    suspend fun getIndexerTransactions(
        @Url url: String,
        @Query("page") page: Int? = null,
        @Query("cursor_lt") cursorLt: String? = null,
        @Query("cursor_hash") cursorHash: String? = null
    ): TonIndexerTransactionsResponse

    @GET
    suspend fun getIndexerJettonTransferPayload(@Url url: String): JettonTransferPayloadRemote

    @POST
    suspend fun callIndexerJsonRpc(
        @Url url: String,
        @Body body: TonIndexerJsonRpcRequest
    ): TonIndexerJsonRpcResponse

    @GET
    suspend fun getRequest(@Url url: String): String

    @POST
    suspend fun sendBlockchainMessage(@Url url: String, @Body body: SendBlockchainMessageRequest): String

    @POST
    suspend fun emulateBlockchainMessage(@Url url: String, @Body body: EmulateMessageToWalletRequest): MessageConsequences

    @GET
    suspend fun getAccountEvents(
        @Url url: String,
        @Query("limit") limit: Int,
        @Query("initiator") initiator: Boolean? = null,
        @Query("subject_only") subjectOnly: Boolean? = null,
        @Query("before_lt") beforeLt: Long? = null,
        @Query("start_date") startFate: Long? = null,
        @Query("end_date") endDate: Long? = null
    ): AccountEvents

    @GET
    suspend fun getManifest(@Url url: String): TonAppManifest

    @POST
    suspend fun tonconnectSend(
        @Url url: String,
        @Body body: RequestBody
    ): ResponseBody

    @GET(BuildConfig.DAPPS_URL)
    suspend fun getDappsConfig(): List<DappConfigRemote>

    @GET("https://tonapi.io/v2/rates?tokens=TON")
    fun getTonCoinPrice(@Query("currencies") currencies: List<String>?): RatesResponse
}
