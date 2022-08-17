package jp.co.soramitsu.crowdloan.impl.data.network.api.moonbeam

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Url

interface MoonbeamApi {

    @GET
    suspend fun getTerms(@Url url: String): String

    @GET("//{apiUrl}/health")
    suspend fun getHealth(
        @Path("apiUrl") apiUrl: String,
        @Header("x-api-key") apiKey: String
    )

    @GET("//{apiUrl}/check-remark/{address}")
    suspend fun getCheckRemark(
        @Path("apiUrl") apiUrl: String,
        @Header("x-api-key") apiKey: String,
        @Path("address") address: String
    ): VerifyCheckResponse

    // store user agreement
    @POST("//{apiUrl}/agree-remark")
    suspend fun agreeRemark(
        @Path("apiUrl") apiUrl: String,
        @Header("x-api-key") apiKey: String,
        @Body body: RemarkStoreRequest
    ): RemarkStoreResponse

    // store remark transaction confirmation for given address
    @POST("//{apiUrl}/verify-remark")
    suspend fun verifyRemark(
        @Path("apiUrl") apiUrl: String,
        @Header("x-api-key") apiKey: String,
        @Body body: RemarkVerifyRequest
    ): RemarkVerifyResponse

    @POST("//{apiUrl}/make-signature")
    suspend fun makeSignature(
        @Path("apiUrl") apiUrl: String,
        @Header("x-api-key") apiKey: String,
        @Body body: SignatureRequest
    ): SignatureResponse
}
