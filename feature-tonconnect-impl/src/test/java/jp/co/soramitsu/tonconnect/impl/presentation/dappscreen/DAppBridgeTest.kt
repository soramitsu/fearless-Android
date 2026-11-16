package jp.co.soramitsu.tonconnect.impl.presentation.dappscreen

import kotlinx.coroutines.runBlocking
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DAppBridgeTest {
    private var disconnects = 0
    private var sent: JSONArray? = null

    @Test
    fun supportedFunctionsForwardArgumentsAndSerializeResponses(): Unit = runBlocking {
        val bridge = bridge()
        val args = JSONArray().put("transaction")
        assertEquals("sent", JSONObject(bridge.invokeFunction("send", args).toString()).getString("result"))
        assertSame(args, sent)
        val connect = JSONArray().put(2).put(JSONObject(
            """{"manifestUrl":"https://dapp.example/manifest.json","items":[{"name":"ton_proof","payload":"challenge"}]}"""
        ))
        assertEquals("challenge", JSONObject(bridge.invokeFunction("connect", connect).toString()).getString("proof"))
        assertEquals("restored", JSONObject(bridge.invokeFunction("restoreConnection", JSONArray()).toString()).getString("result"))
        assertSame(Unit, bridge.invokeFunction("disconnect", JSONArray()))
        assertEquals(1, disconnects)
        assertNull(bridge.invokeFunction("unknown", JSONArray()))
    }

    @Test
    fun fetchResponseIncludesStatusHeadersBodyAndRequestUrl(): Unit = runBlocking {
        var requestedUrl = ""
        var requestedOptions = ""
        val bridge = bridge { url, options ->
            requestedUrl = url
            requestedOptions = options
            Response.Builder().request(Request.Builder().url(url).build())
                .protocol(Protocol.HTTP_1_1).code(200).message("OK").header("X-Test", "value")
                .body("response-body".toResponseBody()).build()
        }
        val response = JSONObject(bridge.invokeFunction(
            "tonapi.fetch", JSONArray().put("https://TONAPI.io/v2/accounts/test").put("""{"method":"GET"}""")
        ).toString())
        assertEquals("https://tonapi.io/v2/accounts/test", requestedUrl)
        assertEquals("""{"method":"GET"}""", requestedOptions)
        assertEquals("response-body", response.getString("body"))
        assertTrue(response.getBoolean("ok"))
        assertEquals(200, response.getInt("status"))
        assertEquals("OK", response.getString("statusText"))
        assertEquals("cors", response.getString("type"))
        assertEquals("value", response.getJSONObject("headers").getString("X-Test"))
        assertFalse(response.getBoolean("redirected"))
        assertEquals(requestedUrl, response.getString("url"))
    }

    @Test
    fun fetchRejectsUnsupportedOriginBeforeCallingNetwork(): Unit = runBlocking {
        var fetched = false
        val bridge = bridge { _, _ -> fetched = true; error("must not fetch") }
        val failure = runCatching {
            bridge.invokeFunction("tonapi.fetch", JSONArray().put("https://evil.example/v2/accounts"))
        }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        assertFalse(fetched)
    }

    @Test
    fun bodylessRedirectIsRepresentedAsRedirectedFailure(): Unit = runBlocking {
        val bridge = bridge { url, _ ->
            Response.Builder().request(Request.Builder().url(url).build()).protocol(Protocol.HTTP_1_1)
                .code(302).message("Found").header("Location", "/other").build()
        }
        val result = JSONObject(bridge.invokeFunction("tonapi.fetch", JSONArray().put("https://tonapi.io/test")).toString())
        assertEquals("", result.getString("body"))
        assertTrue(result.getBoolean("redirected"))
        assertFalse(result.getBoolean("ok"))
        assertEquals(302, result.getInt("status"))
    }

    @Test
    fun injectionBindsFunctionsAndEventsToAWellFormedPageToken() {
        val bridge = bridge()
        val token = "a".repeat(64)
        val script = bridge.jsInjection(token)
        assertEquals(listOf("send", "connect", "restoreConnection", "disconnect"), bridge.availableFunctions.toList())
        assertEquals("fixture-device", bridge.keys["deviceInfo"])
        assertEquals(2, bridge.keys["protocolVersion"])
        assertEquals(true, bridge.keys["isWalletBrowser"])
        assertTrue(script.contains("const token = '" + token + "'"))
        assertTrue(script.contains("message.token !== token"))
        assertTrue(script.contains("window.fearless"))
        assertTrue(script.contains("tonapi.fetch"))
        assertThrows(IllegalArgumentException::class.java) { bridge.jsInjection("bad' token") }
    }

    @Test
    fun bridgeMessageRoundTripsPreserveInvocationAndFallbackError() {
        val original = FunctionInvokeBridgeMessage("send", JSONArray().put(1), "request")
        val parsed = FunctionInvokeBridgeMessage(original.createJSON())
        assertEquals(original.name, parsed.name)
        assertEquals(original.invocationId, parsed.invocationId)
        assertEquals(original.args.toString(), parsed.args.toString())
        val error = FunctionResponseBridgeMessage("request", Throwable())
        val json = error.createJSON()
        assertEquals("functionResponse", json.getString("type"))
        assertEquals("rejected", json.getString("status"))
        assertEquals("unknown client error", json.getString("data"))
    }

    private fun bridge(fetch: suspend (String, String) -> Response = { _, _ -> error("unexpected fetch") }) = DAppBridge(
        deviceInfo = "fixture-device",
        send = { args -> sent = args; JSONObject().put("result", "sent") },
        connect = { protocol, request ->
            assertEquals(2, protocol)
            JSONObject().put("proof", request.proofPayload)
        },
        restoreConnection = { JSONObject().put("result", "restored") },
        disconnect = { disconnects++ },
        tonapiFetch = fetch
    )
}
