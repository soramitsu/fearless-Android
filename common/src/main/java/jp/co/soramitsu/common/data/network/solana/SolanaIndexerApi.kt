package jp.co.soramitsu.common.data.network.solana

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Url

interface SolanaIndexerApi {
    @GET
    suspend fun getServiceInfo(@Url url: String): SolanaIndexerServiceInfo

    @GET
    suspend fun getBalances(@Url url: String): SolanaWalletBalancesResponse

    @GET
    suspend fun getAssets(@Url url: String): SolanaWalletAssetsResponse

    @GET
    suspend fun getState(@Url url: String): SolanaWalletStateResponse

    @GET
    suspend fun getTransactions(@Url url: String): SolanaWalletTransactionsResponse

    @GET
    suspend fun getTokenMetadata(@Url url: String): SolanaTokenMetadata

    @POST
    suspend fun getTokenMetadataBatch(
        @Url url: String,
        @Body body: SolanaTokenMetadataBatchRequest
    ): SolanaTokenMetadataBatchResponse
}
