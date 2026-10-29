package jp.co.soramitsu.common.data.network.nomis

import jp.co.soramitsu.common.data.network.nomis.NomisHeadersInterceptor.Companion.API_KEY_HEADER
import jp.co.soramitsu.common.data.network.nomis.NomisHeadersInterceptor.Companion.CLIENT_ID_HEADER
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NomisHeadersInterceptorTest {

    @Test
    fun `adds both headers from the injected values`() {
        val headers = headersSentWith(NomisHeadersInterceptor("test-api-key", "test-client-id"))

        assertEquals(listOf("test-api-key"), headers.values(API_KEY_HEADER))
        assertEquals(listOf("test-client-id"), headers.values(CLIENT_ID_HEADER))
    }

    @Test
    fun `omits both headers when the values are blank`() {
        val headers = headersSentWith(NomisHeadersInterceptor("", "  "))

        assertNull(headers[API_KEY_HEADER])
        assertNull(headers[CLIENT_ID_HEADER])
    }

    @Test
    fun `omits only the header whose value is blank`() {
        val withoutClientId = headersSentWith(NomisHeadersInterceptor("test-api-key", ""))
        assertEquals(listOf("test-api-key"), withoutClientId.values(API_KEY_HEADER))
        assertNull(withoutClientId[CLIENT_ID_HEADER])

        val withoutApiKey = headersSentWith(NomisHeadersInterceptor(" ", "test-client-id"))
        assertNull(withoutApiKey[API_KEY_HEADER])
        assertEquals(listOf("test-client-id"), withoutApiKey.values(CLIENT_ID_HEADER))
    }

    /**
     * Runs one call through a real OkHttp chain whose last application interceptor answers locally,
     * so nothing leaves the machine, and returns the headers that would have been sent.
     */
    private fun headersSentWith(interceptor: NomisHeadersInterceptor): Headers {
        var sent: Request? = null
        val client = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .addInterceptor { chain ->
                sent = chain.request()
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("{}".toResponseBody(null))
                    .build()
            }
            .build()

        client.newCall(Request.Builder().url("https://nomis.invalid/score").build()).execute().close()

        return requireNotNull(sent) { "the request never reached the end of the chain" }.headers
    }
}
