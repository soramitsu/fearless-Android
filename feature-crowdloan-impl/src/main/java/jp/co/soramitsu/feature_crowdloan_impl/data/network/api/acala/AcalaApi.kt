package jp.co.soramitsu.feature_crowdloan_impl.data.network.api.acala

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface AcalaApi {

    companion object {
        const val BASE_URL = "https://crowdloan.aca-dev.network"
    }

    @GET("/statement")
    suspend fun getStatement(): AcalaStatement

    @GET("/referral/{referral}")
    suspend fun isReferralValid(
        @Path("referral") referral: String
    ): AcalaReferralCheck

    // submit the user’s referral code and the signature of the terms
    @POST("/contribute")
    suspend fun contribute(
        @Body body: AcalaContributeRequest
    ): AcalaContributeResponse

    // record the emails of people who submit contributions via the Liquid Crowdloan DOT
    @POST("/transfer")
    suspend fun transfer(
        @Body body: AcalaTransferRequest
    ): AcalaContributeResponse
}
