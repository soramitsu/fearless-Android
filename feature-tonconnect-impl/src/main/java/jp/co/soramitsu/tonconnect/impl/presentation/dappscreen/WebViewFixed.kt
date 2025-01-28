package jp.co.soramitsu.tonconnect.impl.presentation.dappscreen

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.util.AttributeSet
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import co.jp.soramitsu.feature_tonconnect_impl.R
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import java.util.LinkedList
import kotlin.coroutines.resume

@Suppress("MagicNumber")
open class WebViewFixed @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = android.R.attr.webViewStyle,
) : WebView(context, attrs, defStyle) {

    open class Callback {
        open fun onScroll(y: Int, x: Int) {}
        open fun onElementBlurred() {}
        open fun onElementFocused(rect: RectF) {}
        open fun onPageStarted(url: String, favicon: Bitmap?) {}
        open fun shouldOverrideUrlLoading(request: WebResourceRequest): Boolean {
            return false
        }

        open fun onPageFinished(url: String) {}
        open fun onReceivedTitle(title: String) {}
        open fun onProgressChanged(newProgress: Int) {}
        open fun onLoadResource(url: String): Boolean {
            return true
        }
    }

    private var isPageLoaded = false
        set(value) {
            field = value
            if (value) {
                executeJsQueue()
            }
        }

    private val androidWebViewBridgeJS: String by lazy {
        context.resources.openRawResource(R.raw.webview_ext).readBytes().decodeToString()
    }

    private val callbacks = mutableListOf<Callback>()

    private val jsExecuteQueue = LinkedList<String>()

    private var onScrollRunnable: Runnable? = null
    private var onElementFocusRunnable: Runnable? = null

    init {
        super.setLayerType(LAYER_TYPE_HARDWARE, null)
        isNestedScrollingEnabled = true
        overScrollMode = OVER_SCROLL_NEVER

        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        settings.allowFileAccess = false
        settings.cacheMode = WebSettings.LOAD_NO_CACHE

        super.setRendererPriorityPolicy(RENDERER_PRIORITY_IMPORTANT, false)
        super.setBackgroundColor(Color.TRANSPARENT)
        if (0 != context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) {
            setWebContentsDebuggingEnabled(true)
        }
        webViewClient = object : WebViewClient() {
            override fun onPageStarted(
                view: WebView?,
                url: String,
                favicon: Bitmap?
            ) {
                super.onPageStarted(view, url, favicon)
                isPageLoaded = true
                callbacks.forEach { it.onPageStarted(url, favicon) }
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                var isTrue: Boolean? = null
                for (callback in callbacks) {
                    if (callback.shouldOverrideUrlLoading(request)) {
                        isTrue = true
                    }
                }
                return isTrue ?: super.shouldOverrideUrlLoading(view, request)
            }

            override fun onPageFinished(view: WebView?, url: String) {
                super.onPageFinished(view, url)
                callbacks.forEach { it.onPageFinished(url) }
            }

            override fun onLoadResource(view: WebView?, url: String) {
                var isStop = false
                for (callback in callbacks) {
                    if (!callback.onLoadResource(url)) {
                        isStop = true
                    }
                }
                if (!isStop) {
                    super.onLoadResource(view, url)
                }
            }
        }

        webChromeClient = object : WebChromeClient() {
            override fun onReceivedTitle(view: WebView?, title: String) {
                super.onReceivedTitle(view, title)
                callbacks.forEach { it.onReceivedTitle(title) }
            }

            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                callbacks.forEach { it.onProgressChanged(newProgress) }
            }
        }

        applyAndroidWebViewBridge()
    }

    private fun applyAndroidWebViewBridge() {
        addJavascriptInterface(AndroidWebViewBridge(), "AndroidWebViewBridge")
        executeJS(androidWebViewBridgeJS)
    }

    fun addCallback(callback: Callback) {
        callbacks.add(callback)
    }

    fun removeCallback(callback: Callback) {
        callbacks.remove(callback)
    }

    fun loadUrl(uri: Uri) {
        loadUrl(uri.toString())
    }

    override fun hasOverlappingRendering(): Boolean {
        return false
    }

    fun executeJS(code: String) {
        if (isPageLoaded) {
            evaluateJavascript(code)
        } else {
            jsExecuteQueue.add(code)
        }
    }

    private fun executeJsQueue() {
        while (jsExecuteQueue.isNotEmpty()) {
            jsExecuteQueue.poll()?.let { evaluateJavascript(it) }
        }
    }

    private fun evaluateJavascript(code: String) {
        evaluateJavascript(code, null)
    }

    suspend fun getInputBottom(): Float = suspendCancellableCoroutine { continuation ->
        val jsCode = "(function() {" +
                "var focusedElement = document.activeElement;" +
                "if (focusedElement && (focusedElement.tagName === 'INPUT' || focusedElement.tagName === 'TEXTAREA')) {" +
                "   var rect = focusedElement.getBoundingClientRect();" +
                "   return rect.bottom;" +
                "} else {" +
                "   return -1;" +
                "}" +
                "})()"
        evaluateJavascript(jsCode) { value ->
            val elementBottom = value.toFloatOrNull() ?: -1f
            continuation.resume(elementBottom)
        }
    }

    fun reset() {
        super.loadDataWithBaseURL(null, "", "text/html", "utf-8", null)
        super.clearHistory()
        try {
            (this.parent as ViewGroup).removeView(this)
        } catch (ignored: Throwable) {
        }
    }

    override fun destroy() {
        reset()
        try {
            removeAllViews()
        } catch (ignored: Throwable) {
        }
        super.destroy()
    }

    private fun onScroll(x: Int, y: Int) {
        removeCallbacks(onScrollRunnable)
        onScrollRunnable = Runnable {
            callbacks.forEach { it.onScroll(y, x) }
        }
        postOnAnimationDelayed(onScrollRunnable, 16)
    }

    private fun onElementBlurred() {
        removeCallbacks(onElementFocusRunnable)
        onElementFocusRunnable = Runnable {
            callbacks.forEach { it.onElementBlurred() }
        }
        postOnAnimationDelayed(onElementFocusRunnable, 16)
    }

    private fun onElementFocused(rect: RectF) {
        removeCallbacks(onElementFocusRunnable)
        onElementFocusRunnable = Runnable {
            callbacks.forEach { it.onElementFocused(rect) }
        }
        postOnAnimationDelayed(onElementFocusRunnable, 16)
    }

    inner class AndroidWebViewBridge {

        @JavascriptInterface
        fun onScroll(x: Int, y: Int) {
            this@WebViewFixed.onScroll(x, y)
        }

        @JavascriptInterface
        fun onElementBlurred() {
            this@WebViewFixed.onElementBlurred()
        }

        @JavascriptInterface
        fun onElementFocused(value: String) {
            val json = JSONObject(value)
            val rect = RectF(json.getDouble("left").toFloat(), json.getDouble("top").toFloat(), json.getDouble("right").toFloat(), json.getDouble("bottom").toFloat())
            this@WebViewFixed.onElementFocused(rect)
        }
    }
}
