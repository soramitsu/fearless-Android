package jp.co.soramitsu.common.data.network.ton

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Url

interface TonIndexerApi {
    @GET
    suspend fun getHealth(@Url url: String): TonHealthStatus

    @GET
    suspend fun getContracts(@Url url: String): TonContractsResponse

    @GET
    suspend fun getServiceInfo(@Url url: String): TonIndexerServiceInfo

    @GET
    suspend fun getBalance(@Url url: String): TonBalanceResponse

    @GET
    suspend fun getBalances(@Url url: String): TonBalancesResponse

    @GET
    suspend fun getAssets(@Url url: String): TonBalancesResponse

    @GET
    suspend fun getState(@Url url: String): TonAccountStateResponse

    @GET
    suspend fun getTransactions(@Url url: String): TonTransactionsResponse

    @GET
    suspend fun getSwaps(@Url url: String): TonSwapsResponse

    @GET
    suspend fun getJettonTransferPayload(@Url url: String): TonJettonTransferPayloadResponse

    @POST
    suspend fun runGetMethod(
        @Url url: String,
        @Body body: TonRunGetMethodRequest
    ): TonRunGetMethodResponse

    @POST
    suspend fun runGetMethods(
        @Url url: String,
        @Body body: TonRunGetMethodsRequest
    ): TonRunGetMethodsResponse
}
