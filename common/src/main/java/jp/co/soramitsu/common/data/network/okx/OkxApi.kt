package jp.co.soramitsu.common.data.network.okx

import retrofit2.http.GET
import retrofit2.http.Query

interface OkxApi {

    @GET("supported/chain")
    suspend fun getSupportedChains(
        @Query("chainId") chainId: String? = null
    ): OkxResponse<OkxChainItem>

    @GET("all-tokens")
    suspend fun getAllTokens(
        @Query("chainId") chainId: String? = null
    ): OkxResponse<OkxToken>
}