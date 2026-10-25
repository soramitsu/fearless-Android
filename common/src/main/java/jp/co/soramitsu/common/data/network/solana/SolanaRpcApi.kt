package jp.co.soramitsu.common.data.network.solana

import com.google.gson.JsonObject
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Url

interface SolanaRpcApi {
    @POST
    suspend fun postJsonRpc(
        @Url url: String,
        @Body body: JsonObject
    ): JsonObject
}
