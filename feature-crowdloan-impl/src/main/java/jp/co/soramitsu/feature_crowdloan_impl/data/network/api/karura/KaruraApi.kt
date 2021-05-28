package jp.co.soramitsu.feature_crowdloan_impl.data.network.api.karura

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path


interface KaruraApi {

    companion object {
        const val BASE_URL = "https://crowdloan-api.laminar.codes"
    }

    @GET("/referral/{referral}")
    suspend fun isReferralValid(@Path("referral") referral: String) : ReferralCheck

    @GET("/statement")
    suspend fun getStatement() : KaruraStatement

    @POST("/verify")
    suspend fun applyForBonus(@Body body: VerifyKaruraParticipationRequest) : Any?
}
