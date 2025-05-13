package jp.co.soramitsu.soracard.impl.domain

import jp.co.soramitsu.oauth.network.SoraCardNetworkClient
import jp.co.soramitsu.oauth.network.SoraCardNetworkResponse
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url

class SoraCardNetworkClientImpl(
    private val retrofitClient: SoraCardRetrofitClient,
    private val json: Json,
) : SoraCardNetworkClient {

    override suspend fun <T> get(
        header: String?,
        bearerToken: String?,
        url: String,
        deserializer: DeserializationStrategy<T>,
    ): SoraCardNetworkResponse<T> = try {
        val responseAsString = retrofitClient.getAsString(
            url = url,
            userAgent = header.orEmpty(),
            bearerToken = bearerToken?.let { "Bearer $it" },
        )
        if (responseAsString.isSuccessful) {
            val responseBody = responseAsString.body()
            if (responseBody != null) {
                val parsed = json.decodeFromString(deserializer, responseBody)
                SoraCardNetworkResponse(
                    value = parsed,
                    statusCode = 200,
                )
            } else {
                SoraCardNetworkResponse(
                    value = null,
                    statusCode = 0,
                    message = "Body in null (${responseAsString.message()})",
                )
            }
        } else {
            SoraCardNetworkResponse(
                value = null,
                statusCode = responseAsString.code(),
                message = responseAsString.message(),
            )
        }
    } catch (t: Throwable) {
        if (t is CancellationException) throw t
        SoraCardNetworkResponse(
            value = null,
            statusCode = 0,
            message = t.localizedMessage,
        )
    }

    override suspend fun <T> post(
        header: String?,
        bearerToken: String?,
        url: String,
        body: String,
        deserializer: DeserializationStrategy<T>,
    ): SoraCardNetworkResponse<T> = try {
        val responseAsString = retrofitClient.postAsString(
            url = url,
            userAgent = header.orEmpty(),
            bearerToken = bearerToken.orEmpty().let { "Bearer $it" },
            body = body.toRequestBody("application/json".toMediaType()),
        )
        if (responseAsString.isSuccessful) {
            val responseBody = responseAsString.body()
            if (responseBody != null) {
                val parsed = json.decodeFromString(deserializer, responseBody)
                SoraCardNetworkResponse(
                    value = parsed,
                    statusCode = 200,
                )
            } else {
                SoraCardNetworkResponse(
                    value = null,
                    statusCode = 0,
                    message = "Body in null (${responseAsString.message()})",
                )
            }
        } else {
            SoraCardNetworkResponse(
                value = null,
                statusCode = responseAsString.code(),
                message = responseAsString.message(),
            )
        }
    } catch (t: Throwable) {
        if (t is CancellationException) throw t
        SoraCardNetworkResponse(
            value = null,
            statusCode = 0,
            message = t.localizedMessage,
        )
    }
}

interface SoraCardRetrofitClient {

    @GET
    suspend fun getAsString(
        @Url url: String,
        @Header("User-Agent") userAgent: String,
        @Header("Authorization") bearerToken: String?,
    ): Response<String>

    @POST
    suspend fun postAsString(
        @Url url: String,
        @Header("User-Agent") userAgent: String,
        @Header("Authorization") bearerToken: String,
        @Body body: RequestBody,
    ): Response<String>
}
