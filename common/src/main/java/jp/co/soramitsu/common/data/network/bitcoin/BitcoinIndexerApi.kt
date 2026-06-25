package jp.co.soramitsu.common.data.network.bitcoin

import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Url

interface BitcoinIndexerApi {
    @GET
    suspend fun getAddress(@Url url: String): BitcoinEsploraAddress

    @GET
    suspend fun getUtxos(@Url url: String): List<BitcoinEsploraUtxo>

    @GET
    suspend fun getTransactions(@Url url: String): List<BitcoinEsploraTransaction>

    @GET
    suspend fun getFeeEstimates(@Url url: String): Map<String, Double>

    @Headers("Content-Type: text/plain")
    @POST
    suspend fun broadcastTransaction(
        @Url url: String,
        @Body body: RequestBody
    ): String
}
