package jp.co.soramitsu.common.data.network.nomis

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adds the Nomis API credentials to every Nomis request.
 *
 * The values come from build-time secrets (`FL_NOMIS_API_KEY` and `FL_NOMIS_CLIENT_ID`, read into
 * `BuildConfig` by common/build.gradle). A build without one of them leaves that header out
 * instead of sending it empty.
 */
class NomisHeadersInterceptor(
    apiKey: String,
    clientId: String
) : Interceptor {

    private val apiKey = apiKey.trim()
    private val clientId = clientId.trim()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder().apply {
            if (apiKey.isNotEmpty()) header(API_KEY_HEADER, apiKey)
            if (clientId.isNotEmpty()) header(CLIENT_ID_HEADER, clientId)
        }.build()
        return chain.proceed(request)
    }

    companion object {
        const val API_KEY_HEADER = "X-API-Key"
        const val CLIENT_ID_HEADER = "X-ClientId"
    }
}
