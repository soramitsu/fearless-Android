package jp.co.soramitsu.tonconnect.impl.presentation.dappscreen

import android.app.Activity
import android.content.Context
import android.graphics.RectF
import android.net.Uri
import android.os.Looper
import android.webkit.ValueCallback
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.widget.FrameLayout
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
class WebViewFixedTest {
    private lateinit var controller: ActivityController<Activity>
    private lateinit var view: RecordingWebView

    @Before
    fun attachWebView() {
        controller = Robolectric.buildActivity(Activity::class.java).setup()
        view = RecordingWebView(controller.get())
        val parent = FrameLayout(controller.get())
        parent.addView(view)
        controller.get().setContentView(parent)
    }

    @After
    fun destroyWebView() {
        if (!shadowOf(view).wasDestroyCalled()) view.destroy()
        controller.pause().stop().destroy()
    }

    @Test
    fun settingsDisableFileAccessMixedContentAndAutomaticWindows() {
        assertTrue(view.settings.javaScriptEnabled)
        assertTrue(view.settings.domStorageEnabled)
        assertFalse(view.settings.javaScriptCanOpenWindowsAutomatically)
        assertFalse(view.settings.allowFileAccess)
        assertFalse(view.settings.allowContentAccess)
        assertFalse(view.settings.allowFileAccessFromFileURLs)
        assertFalse(view.settings.allowUniversalAccessFromFileURLs)
        assertEquals(WebSettings.MIXED_CONTENT_NEVER_ALLOW, view.settings.mixedContentMode)
        assertEquals(WebSettings.LOAD_NO_CACHE, view.settings.cacheMode)
        assertFalse(view.hasOverlappingRendering())
        assertNotNull(shadowOf(view).getJavascriptInterface("AndroidWebViewBridge"))
    }

    @Test
    fun queuedScriptsRunWhenPageStartsAndLaterScriptsRunImmediately() {
        val callback = mockk<WebViewFixed.Callback>(relaxed = true)
        view.addCallback(callback)
        view.executeJS("queued()")
        assertTrue(view.scripts.isEmpty())
        shadowOf(view).webViewClient.onPageStarted(view, "https://dapp.example", null)
        assertEquals("queued()", view.scripts.last())
        assertTrue(view.scripts.size >= 2)
        verify { callback.onPageStarted("https://dapp.example", null) }
        view.executeJS("immediate()")
        assertEquals("immediate()", view.scripts.last())
    }

    @Test
    fun pageAndChromeCallbacksAreForwardedAndCanBeRemoved() {
        val callback = mockk<WebViewFixed.Callback>(relaxed = true)
        every { callback.onLoadResource(any()) } returns true
        val blocker = mockk<WebViewFixed.Callback>(relaxed = true)
        every { blocker.shouldOverrideUrlLoading(any()) } returns true
        view.addCallback(callback)
        view.addCallback(blocker)
        val request = mockk<WebResourceRequest> {
            every { url } returns android.net.Uri.parse("https://dapp.example")
        }
        assertTrue(shadowOf(view).webViewClient.shouldOverrideUrlLoading(view, request))
        shadowOf(view).webViewClient.onPageFinished(view, "https://dapp.example")
        shadowOf(view).webViewClient.onLoadResource(view, "https://dapp.example/image")
        shadowOf(view).webChromeClient.onReceivedTitle(view, "Dapp title")
        shadowOf(view).webChromeClient.onProgressChanged(view, 75)
        verify { callback.onPageFinished("https://dapp.example") }
        verify { callback.onLoadResource("https://dapp.example/image") }
        verify { callback.onReceivedTitle("Dapp title") }
        verify { callback.onProgressChanged(75) }
        view.removeCallback(blocker)
        assertFalse(shadowOf(view).webViewClient.shouldOverrideUrlLoading(view, request))
        view.removeCallback(callback)
        shadowOf(view).webChromeClient.onReceivedTitle(view, "Other title")
        verify(exactly = 0) { callback.onReceivedTitle("Other title") }
    }

    @Test
    fun javascriptScrollAndFocusNotificationsAreDebounced() {
        val callback = mockk<WebViewFixed.Callback>(relaxed = true)
        view.addCallback(callback)
        val bridge = view.AndroidWebViewBridge()
        bridge.onScroll(4, 9)
        bridge.onScroll(6, 12)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
        verify(exactly = 1) { callback.onScroll(12, 6) }
        verify(exactly = 0) { callback.onScroll(9, 4) }
        bridge.onElementFocused("""{"left":1,"top":2,"right":3,"bottom":4}""")
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
        val rect = slot<RectF>()
        verify { callback.onElementFocused(capture(rect)) }
        assertEquals(RectF(1f, 2f, 3f, 4f), rect.captured)
        bridge.onElementBlurred()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
        verify { callback.onElementBlurred() }
    }

    @Test
    fun inputBottomParsesJavascriptNumbersAndDefaultsForMissingInput(): Unit = runBlocking {
        view.scriptResult = "42.5"
        assertEquals(42.5f, view.getInputBottom())
        view.scriptResult = "null"
        assertEquals(-1f, view.getInputBottom())
        assertTrue(view.scripts.last().contains("document.activeElement"))
    }

    @Test
    fun resetClearsHistoryAndDetachesViewBeforeDestroy() {
        view.loadUrl(Uri.parse("https://dapp.example"))
        assertEquals("https://dapp.example", shadowOf(view).lastLoadedUrl)
        view.reset()
        assertNull(view.parent)
        assertTrue(shadowOf(view).wasClearHistoryCalled())
        view.destroy()
        assertTrue(shadowOf(view).wasDestroyCalled())
        assertEquals(0, view.childCount)
    }

    private class RecordingWebView(context: Context) : WebViewFixed(context) {
        val scripts = mutableListOf<String>()
        var scriptResult = "null"

        override fun evaluateJavascript(script: String, resultCallback: ValueCallback<String>?) {
            scripts += script
            resultCallback?.onReceiveValue(scriptResult)
        }
    }
}
