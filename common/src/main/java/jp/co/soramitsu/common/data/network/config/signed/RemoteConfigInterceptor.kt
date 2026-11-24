package jp.co.soramitsu.common.data.network.config.signed

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException

/**
 * Lets the app read a remote configuration file only as verified by [RemoteConfigVerifier]: the fetched bytes when the
 * signed manifest lists them, else the last verified copy or the bundled snapshot. Requests for anything else, images
 * of `remote-config/` included, pass through unchanged.
 */
class RemoteConfigInterceptor(private val verifier: RemoteConfigVerifier) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val path = RemoteConfigTrust.signedPathOf(request.url)
        if (path == null || request.method != "GET") return chain.proceed(request)

        val fetched = try {
            chain.proceed(request).use { response ->
                if (response.code == HTTP_OK) response.body?.let { body -> readBounded(body.byteStream()) } else null
            }
        } catch (error: IOException) {
            null
        }

        val resolved = verifier.resolve(path, fetched)
            ?: return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(HTTP_UNAVAILABLE)
                .message("Remote configuration could not be verified")
                .body(ByteArray(0).toResponseBody(null))
                .build()

        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(HTTP_OK)
            .message("OK")
            .header(SOURCE_HEADER, resolved.source.name.lowercase())
            .body(resolved.content.toResponseBody(mediaTypeOf(path).toMediaType()))
            .build()
    }

    private fun readBounded(stream: java.io.InputStream): ByteArray? {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER_SIZE)
        while (true) {
            val read = stream.read(buffer)
            if (read < 0) return output.toByteArray()
            if (output.size() + read > MAX_CONFIG_BYTES) return null
            output.write(buffer, 0, read)
        }
    }

    private fun mediaTypeOf(path: String) = when {
        path.endsWith(".csv") -> "text/csv; charset=utf-8"
        path.endsWith(".json") -> "application/json; charset=utf-8"
        else -> "text/plain; charset=utf-8"
    }

    companion object {
        /** Tells where the body came from: network, last_verified or bundled. */
        const val SOURCE_HEADER = "X-Fearless-Remote-Config"
        private const val HTTP_OK = 200
        private const val HTTP_UNAVAILABLE = 503
        private const val BUFFER_SIZE = 64 * 1024
        // The largest file (the phishing list) is about 2 MiB.
        private const val MAX_CONFIG_BYTES = 16 * 1024 * 1024
    }
}
