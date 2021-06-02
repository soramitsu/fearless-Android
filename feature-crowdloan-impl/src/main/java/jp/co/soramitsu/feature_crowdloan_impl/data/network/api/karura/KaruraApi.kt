package jp.co.soramitsu.feature_crowdloan_impl.data.network.api.karura

import jp.co.soramitsu.core.model.Node
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface KaruraApi {

    companion object {
        private val URL_BY_NETWORK_TYPE = mapOf(
            Node.NetworkType.ROCOCO to "crowdloan-api.laminar.codes",
            Node.NetworkType.KUSAMA to "api.aca-staging.network"
        )

        fun getBaseUrl(networkType: Node.NetworkType) = URL_BY_NETWORK_TYPE[networkType]
            ?: throw UnsupportedOperationException("Network ${networkType.readableName} is not supported for Karura")
    }

    @GET("//{baseUrl}/referral/{referral}")
    suspend fun isReferralValid(
        @Path("baseUrl") baseUrl: String,
        @Path("referral") referral: String
    ): ReferralCheck

    @GET("//{baseUrl}/statement")
    suspend fun getStatement(
        @Path("baseUrl") baseUrl: String
    ): KaruraStatement

    @POST("//{baseUrl}/verify")
    suspend fun applyForBonus(
        @Path("baseUrl") baseUrl: String,
        @Body body: VerifyKaruraParticipationRequest
    ): Any?
}
