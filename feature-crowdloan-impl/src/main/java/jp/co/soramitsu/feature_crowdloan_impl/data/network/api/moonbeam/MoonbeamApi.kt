package jp.co.soramitsu.feature_crowdloan_impl.data.network.api.moonbeam

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface MoonbeamApi {

    companion object {
        const val BASE_URL = "https://virtserver.swaggerhub.com/PureStake/Wallets-3.0.0/1.0.0"
        const val BASE_URL_TEST = "https://wallet-test.api.purestake.xyz"
    }

    @GET("//raw.githubusercontent.com/moonbeam-foundation/crowdloan-self-attestation/main/moonbeam/README.md")
    suspend fun getTerms(): String

    @GET("/health")
    suspend fun getHealth(): Any?

    @GET("/check-remark/{address}")
    suspend fun getCheckRemark(
        @Path("address") address: String
    ): VerifyCheckResponse

    // store user agreement
    @POST("/agree-remark")
    suspend fun agreeRemark(
        @Body body: RemarkStoreRequest
    ): RemarkStoreResponse

    // store remark transaction confirmation for given address
    @POST("/verify-remark")
    suspend fun verifyRemark(
        @Body body: RemarkVerifyRequest
    ): RemarkVerifyResponse

    // record the emails of people who submit contributions via the Liquid Crowdloan DOT
    @POST("/make-signature")
    suspend fun makeSignature(
        @Body body: SignatureRequest
    ): SignatureResponse
}
