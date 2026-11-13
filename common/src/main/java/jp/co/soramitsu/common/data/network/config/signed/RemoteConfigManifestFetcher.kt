package jp.co.soramitsu.common.data.network.config.signed

import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request

/** Fetches the published signed manifest with its own client, outside [RemoteConfigInterceptor]. */
class RemoteConfigManifestFetcher(
    private val client: OkHttpClient,
    private val url: String = RemoteConfigTrust.MANIFEST_URL
) {
    /** The manifest bytes, or null when they cannot be fetched. They are verified by the caller. */
    fun fetch(): ByteArray? = runCatching {
        val request = Request.Builder().url(url).cacheControl(CacheControl.FORCE_NETWORK).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val bytes = response.body?.bytes() ?: return@use null
            bytes.takeIf { it.size <= MAX_MANIFEST_BYTES }
        }
    }.getOrNull()

    private companion object {
        const val MAX_MANIFEST_BYTES = 1024 * 1024
    }
}
