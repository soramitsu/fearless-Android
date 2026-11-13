package jp.co.soramitsu.common.data.network.config.signed

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException
import java.nio.file.Files

class RemoteConfigInterceptorTest {

    private val base = "https://raw.githubusercontent.com/soramitsu/fearless-Android/master/remote-config/"
    private val path = "production/appConfigs/onboarding/mobile v2.json"
    private val content = "{\"x\":1}".toByteArray()
    private val directory = Files.createTempDirectory("remote-config-interceptor").toFile()

    @After
    fun cleanUp() {
        directory.deleteRecursively()
    }

    private fun interceptor(published: ByteArray?) = RemoteConfigInterceptor(
        RemoteConfigVerifier(
            fetchManifest = { published },
            store = RemoteConfigStore(directory),
            snapshot = RemoteConfigSnapshot { null },
            verify = TestManifests::verify
        )
    )

    // A client whose "network" is a terminal interceptor: nothing leaves the process.
    private class FakeNetwork(private val answer: (Request) -> Response) {
        var requests = 0
        fun call(interceptor: Interceptor, request: Request): Response = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .addInterceptor { chain ->
                requests++
                answer(chain.request())
            }
            .build()
            .newCall(request)
            .execute()
    }

    private fun ok(request: Request, body: ByteArray) = Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200)
        .message("OK").body(body.toResponseBody(null)).build()

    @Test
    fun `a signed file passes through and an encoded path is matched`() {
        val request = Request.Builder().url(base + "production/appConfigs/onboarding/mobile%20v2.json").build()
        val response = FakeNetwork { ok(it, content) }.call(interceptor(TestManifests.signed(mapOf(path to content), 1)), request)
        assertEquals(200, response.code)
        assertEquals("network", response.header(RemoteConfigInterceptor.SOURCE_HEADER))
        assertArrayEquals(content, response.body!!.bytes())
    }

    @Test
    fun `a tampered file without any verified copy is refused`() {
        val request = Request.Builder().url(base + "production/appConfigs/onboarding/mobile%20v2.json").build()
        val response = FakeNetwork { ok(it, "tampered".toByteArray()) }.call(interceptor(TestManifests.signed(mapOf(path to content), 1)), request)
        assertEquals(503, response.code)
    }

    @Test
    fun `a network failure serves the last verified copy`() {
        val request = Request.Builder().url(base + "production/appConfigs/onboarding/mobile%20v2.json").build()
        val published = TestManifests.signed(mapOf(path to content), 1)
        FakeNetwork { ok(it, content) }.call(interceptor(published), request).close()
        val response = FakeNetwork { throw IOException("offline") }.call(interceptor(null), request)
        assertEquals("last_verified", response.header(RemoteConfigInterceptor.SOURCE_HEADER))
        assertArrayEquals(content, response.body!!.bytes())
    }

    @Test
    fun `images and other hosts pass through untouched`() {
        listOf(
            base + "assets/icons/chains/white/Polkadot.svg",
            "https://example.com/remote-config/production/chains/v13/chains.json",
            "https://raw.githubusercontent.com/soramitsu/fearless-iOS/master/remote-config/chains.json"
        ).forEach { url ->
            val request = Request.Builder().url(url).build()
            val network = FakeNetwork { ok(it, "raw".toByteArray()) }
            val response = network.call(interceptor(null), request)
            assertEquals(url, 1, network.requests)
            assertNull(response.header(RemoteConfigInterceptor.SOURCE_HEADER))
            assertEquals("raw", response.body!!.string())
        }
    }
}
