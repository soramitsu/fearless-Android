package jp.co.soramitsu.tonconnect.impl.presentation.dappscreen

import android.os.Looper
import androidx.activity.ComponentActivity
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController

@RunWith(RobolectricTestRunner::class)
class BridgeWebViewTest {
    private lateinit var controller: ActivityController<ComponentActivity>
    private lateinit var view: BridgeWebView
    private val bridge = RecordingBridge()

    @Before
    fun attachBridge() {
        controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        view = BridgeWebView(controller.get())
        controller.get().setContentView(view)
        view.jsBridge = bridge
        view.trustedOrigin = "https://dapp.example"
        loadPage("https://dapp.example/swap")
    }

    @After
    fun destroyBridge() {
        view.destroy()
        controller.pause().stop().destroy()
    }

    @Test
    fun trustedInvocationReturnsResponseWithRequestIdAndPageToken() {
        val token = bridge.tokens.last()
        view.postMessage(invocation("send", token).toString())
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf("send" to """["argument"]"""), bridge.calls)
        val script = shadowOf(view).lastEvaluatedJavascript
        assertTrue(script.contains("fulfilled"))
        assertTrue(script.contains("request-1"))
        assertTrue(script.contains(token))
    }

    @Test
    fun malformedWrongTokenAndNonInvocationMessagesAreIgnored() {
        view.postMessage("not-json")
        view.postMessage(invocation("send", "wrong-token").toString())
        view.postMessage(invocation("send", bridge.tokens.last()).put("type", "event").toString())
        view.postMessage(JSONObject().put("token", bridge.tokens.last()).put("type", "invokeRnFunc").toString())
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(bridge.calls.isEmpty())
    }

    @Test
    fun navigationInvalidatesTokensAndForeignPagesReceiveNoBridge() {
        val original = bridge.tokens.last()
        loadPage("https://evil.example")
        view.postMessage(invocation("send", original).toString())
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, bridge.tokens.size)
        assertTrue(bridge.calls.isEmpty())
        loadPage("https://dapp.example/next")
        val fresh = bridge.tokens.last()
        assertNotEquals(original, fresh)
        view.postMessage(invocation("send", original).toString())
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(bridge.calls.isEmpty())
        view.postMessage(invocation("send", fresh).toString())
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, bridge.calls.size)
    }

    @Test
    fun bridgeFailureReturnsRejectionAndNullResultProducesNoResponse() {
        view.postMessage(invocation("fail", bridge.tokens.last()).toString())
        shadowOf(Looper.getMainLooper()).idle()
        val rejection = shadowOf(view).lastEvaluatedJavascript
        assertTrue(rejection.contains("rejected"))
        assertTrue(rejection.contains("denied"))
        view.postMessage(invocation("unknown", bridge.tokens.last()).toString())
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(rejection, shadowOf(view).lastEvaluatedJavascript)
        view.jsBridge = null
        view.postMessage(invocation("send", bridge.tokens.last()).toString())
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf("fail", "unknown"), bridge.calls.map { it.first })
    }

    private fun loadPage(url: String) {
        view.loadUrl(url)
        shadowOf(view).webViewClient.onPageStarted(view, url, null)
    }

    private fun invocation(name: String, token: String) =
        FunctionInvokeBridgeMessage(name, JSONArray().put("argument"), "request-1").createJSON()
            .put("type", BridgeMessage.Type.InvokeRnFunc.value)
            .put(JsBridge.TOKEN_KEY, token)

    private class RecordingBridge : JsBridge("fixture") {
        override val availableFunctions = arrayOf("send", "fail")
        val tokens = mutableListOf<String>()
        val calls = mutableListOf<Pair<String, String>>()

        override suspend fun invokeFunction(name: String, args: JSONArray): Any? {
            calls += name to args.toString()
            return when (name) {
                "send" -> """{"ok":true}"""
                "fail" -> error("denied")
                else -> null
            }
        }

        override fun jsInjection(token: String): String {
            tokens += token
            return "installBridge('" + token + "')"
        }
    }
}
