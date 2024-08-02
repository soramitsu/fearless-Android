package jp.co.soramitsu.common.data.network.nomis

import retrofit2.http.GET
import retrofit2.http.Path

interface NomisApi {
    @GET("wallet/{address}/score/")
    suspend fun getNomisScore(
        @Path("address") address: String
    ): NomisResponse
}