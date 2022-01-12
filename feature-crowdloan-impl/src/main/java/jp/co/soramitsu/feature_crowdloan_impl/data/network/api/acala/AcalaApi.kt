package jp.co.soramitsu.feature_crowdloan_impl.data.network.api.acala

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface AcalaApi {

    @GET("//{apiUrl}/statement")
    suspend fun getStatement(
        @Path("apiUrl") apiUrl: String,
    ): AcalaStatement

    @GET("//{apiUrl}/referral/{referral}")
    suspend fun isReferralValid(
        @Path("apiUrl") apiUrl: String,
        @Path("referral") referral: String
    ): AcalaReferralCheck

    // submit the user’s referral code and the signature of the terms
    @POST("//{apiUrl}/contribute")
    suspend fun contribute(
        @Path("apiUrl") apiUrl: String,
        @Header("Authorization") bearerToken: String,
        @Body body: AcalaContributeRequest
    ): AcalaContributeResponse

    // record the emails of people who submit contributions via the Liquid Crowdloan DOT
    @POST("//{apiUrl}/transfer")
    suspend fun transfer(
        @Path("apiUrl") apiUrl: String,
        @Header("Authorization") bearerToken: String,
        @Body body: AcalaTransferRequest
    ): AcalaContributeResponse
}
