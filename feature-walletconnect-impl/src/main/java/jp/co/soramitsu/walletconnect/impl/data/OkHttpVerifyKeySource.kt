package jp.co.soramitsu.walletconnect.impl.data

import jp.co.soramitsu.walletconnect.impl.protocol.WcVerifier
import jp.co.soramitsu.walletconnect.impl.protocol.WcVerifyKeySource
import jp.co.soramitsu.walletconnect.impl.protocol.WcVerifyPublicKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Fetches the Verify server's attestation key over HTTPS, without interceptors or cache. */
internal class OkHttpVerifyKeySource(baseClient: OkHttpClient) : WcVerifyKeySource {
    private val client = baseClient.newBuilder()
        .apply {
            interceptors().clear()
            networkInterceptors().clear()
        }
        .cache(null)
        .callTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    override suspend fun fetch(): WcVerifyPublicKey = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url(WcVerifier.PUBLIC_KEY_URL).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Verify key request failed: ${response.code}")
            WcVerifyPublicKey.parse(response.peekBody(MAX_BODY_BYTES).string())
                ?: throw IOException("Malformed Verify key")
        }
    }

    private companion object {
        const val TIMEOUT_SECONDS = 10L
        const val MAX_BODY_BYTES = 64L * 1024
    }
}
