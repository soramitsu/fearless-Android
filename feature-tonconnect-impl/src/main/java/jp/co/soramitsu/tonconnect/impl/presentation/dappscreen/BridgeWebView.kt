package jp.co.soramitsu.tonconnect.impl.presentation.dappscreen

import android.content.Context
import android.graphics.Bitmap
import android.util.AttributeSet
import android.webkit.JavascriptInterface
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
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

    private val scope: CoroutineScope
        get() = findViewTreeLifecycleOwner()?.lifecycleScope ?: throw IllegalStateException("No lifecycle owner")

    private val webViewCallback = object : Callback() {
        override fun onPageStarted(url: String, favicon: Bitmap?) {
            super.onPageStarted(url, favicon)
            initBridge()
        }
    }

    init {
        addJavascriptInterface(this, "ReactNativeWebView")
        addCallback(webViewCallback)
        initBridge()
    }

    private fun initBridge() {
        val value = jsBridge ?: return
        executeJS(value.jsInjection())
    }

    private suspend fun postMessage(
        message: FunctionResponseBridgeMessage
    ) = withContext(Dispatchers.Main) {
        val code = """
            (function() {
                window.dispatchEvent(new MessageEvent('message', {
                    data: ${message.createJSON()}
                }));
            })();
        """
        executeJS(code)
    }

    @JavascriptInterface
    fun postMessage(message: String) {
        val json = JSONObject(message)
        val type = json.getString("type")
        if (type == BridgeMessage.Type.InvokeRnFunc.value) {
            scope.launch {
                invokeFunction(FunctionInvokeBridgeMessage(json))
            }
        }
    }

    private suspend fun invokeFunction(message: FunctionInvokeBridgeMessage) {
        val bridge = jsBridge ?: return
        try {
            val data = bridge.invokeFunction(message.name, message.args) ?: return
            postMessage(
                FunctionResponseBridgeMessage(
                    invocationId = message.invocationId,
                    status = "fulfilled",
                    data = data,
                )
            )
        } catch (e: Throwable) {
            postMessage(
                FunctionResponseBridgeMessage(
                    invocationId = message.invocationId,
                    error = e,
                )
            )
        }
    }
}