package jp.co.soramitsu.tonconnect.impl.presentation.dappscreen

import android.content.Context
import android.graphics.Bitmap
import android.util.AttributeSet
import android.webkit.JavascriptInterface
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class BridgeWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = android.R.attr.webViewStyle,
) : WebViewFixed(context, attrs, defStyle) {

    var jsBridge: JsBridge? = null

    /** The only origin whose main frame may use the bridge. */
    var trustedOrigin: String? = null

    // The current main-frame page's secret; see DappBridgeAccess.
    @Volatile
    private var pageToken: String? = null

    private val webViewCallback = object : Callback() {
        override fun onPageStarted(url: String, favicon: Bitmap?) {
            super.onPageStarted(url, favicon)
            initBridge(url)
        }
    }

    init {
        // The interface is visible to every frame; calls without the page token are ignored.
        addJavascriptInterface(this, "ReactNativeWebView")
        addCallback(webViewCallback)
    }

    private fun initBridge(pageUrl: String) {
        // Each main-frame page gets a new token, and a page of another origin gets no bridge.
        val token = DappBridgeAccess.newPageToken()
            .takeIf { DappBridgeAccess.isTrustedPage(pageUrl, trustedOrigin) }
        pageToken = token
        val bridge = jsBridge
        if (bridge != null && token != null) {
            executeJS(bridge.jsInjection(token))
        }
    }

    private suspend fun postMessage(message: FunctionResponseBridgeMessage, token: String) =
        withContext(Dispatchers.Main) {
            // A response is only ever delivered to the page that made the call.
            if (!DappBridgeAccess.isAuthorized(token, pageToken, url, trustedOrigin)) {
                return@withContext
            }
            val code = """
                (function() {
                    window.dispatchEvent(new MessageEvent('message', {
                        data: ${message.createJSON().put(JsBridge.TOKEN_KEY, token)}
                    }));
                })();
            """
            executeJS(code)
        }

    @JavascriptInterface
    fun postMessage(message: String) {
        val json = runCatching { JSONObject(message) }.getOrNull() ?: return
        val token = json.optString(JsBridge.TOKEN_KEY)
        // Frames other than the trusted main frame do not know the page token.
        if (!DappBridgeAccess.isTokenValid(token, pageToken)) return
        post { onAuthorizedMessage(json, token) }
    }

    private fun onAuthorizedMessage(json: JSONObject, token: String) {
        // Checked again on the main thread, where the main frame's current page is known.
        val isTrustedCall = DappBridgeAccess.isAuthorized(token, pageToken, url, trustedOrigin)
        if (!isTrustedCall || json.optString("type") != BridgeMessage.Type.InvokeRnFunc.value) return
        val invocation = runCatching { FunctionInvokeBridgeMessage(json) }.getOrNull() ?: return
        findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
            invokeFunction(invocation, token)
        }
    }

    private suspend fun invokeFunction(message: FunctionInvokeBridgeMessage, token: String) {
        val bridge = jsBridge ?: return
        try {
            val data = bridge.invokeFunction(message.name, message.args) ?: return
            postMessage(
                FunctionResponseBridgeMessage(
                    invocationId = message.invocationId,
                    status = "fulfilled",
                    data = data,
                ),
                token
            )
        } catch (e: Throwable) {
            postMessage(
                FunctionResponseBridgeMessage(
                    invocationId = message.invocationId,
                    error = e,
                ),
                token
            )
        }
    }
}
