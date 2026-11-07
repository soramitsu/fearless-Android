package jp.co.soramitsu.common.data.network.config.signed

import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException

class RemoteConfigManifestFetcherTest {

    private var requested: Request? = null

    // The "network" is a terminal interceptor: nothing leaves the process.
    private fun fetcher(answer: (Request) -> Response) = RemoteConfigManifestFetcher(
        OkHttpClient.Builder().addInterceptor { chain ->
            requested = chain.request()
            answer(chain.request())
        }.build()
    )

    private fun response(request: Request, code: Int, body: ByteArray) = Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
        .code(code).message("status").body(body.toResponseBody(null)).build()

    @Test
    fun `fetches the published manifest without a cache`() {
        val manifest = "{\"algorithm\":\"ed25519\"}".toByteArray()
        assertArrayEquals(manifest, fetcher { response(it, 200, manifest) }.fetch())
        assertEquals(RemoteConfigTrust.MANIFEST_URL, requested!!.url.toString())
        assertEquals("no-cache", requested!!.header("Cache-Control"))
    }

    @Test
    fun `a missing, failed or oversized manifest reads as none`() {
        assertNull(fetcher { response(it, 404, ByteArray(0)) }.fetch())
        assertNull(fetcher { throw IOException("offline") }.fetch())
        assertNull(fetcher { response(it, 200, ByteArray(1024 * 1024 + 1)) }.fetch())
    }
}
