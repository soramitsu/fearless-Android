package jp.co.soramitsu.common.data.network.ton

import jp.co.soramitsu.common.BuildConfig
import okhttp3.RequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Url

interface TonApi {
    @GET()//"/v2/accounts/{account_id}")
    suspend fun getAccountData(@Url url: String): TonAccountData

    @GET()//"//v2/accounts/{account_id}/jettons")
    suspend fun getJettonBalances(@Url url: String, @Query("currencies") currencies: List<String>?): JettonsBalances

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
    suspend fun getManifest(
        @Url url: String,
//        @Header("Connection") value: String = "close"
    ): TonAppManifest

    @POST
    suspend fun tonconnectSend(
        @Url url: String,
        @Body body: RequestBody
    ): ResponseBody

    @GET(BuildConfig.DAPPS_URL)
    suspend fun getDappsConfig(): List<DappConfigRemote>
}