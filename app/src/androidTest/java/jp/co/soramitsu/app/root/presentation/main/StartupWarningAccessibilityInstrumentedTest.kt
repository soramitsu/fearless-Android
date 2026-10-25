package jp.co.soramitsu.app.root.presentation.main

import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Rect
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.view.ContextThemeWrapper
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import jp.co.soramitsu.app.root.domain.InitializationStep
import jp.co.soramitsu.app.root.presentation.createStartupConnectionAlert
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.view.bottomSheet.AlertBottomSheet
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Isolated real-dialog diagnostic. Does not open a wallet, database, preferences, or PIN screen. */
@RunWith(AndroidJUnit4::class)
class StartupWarningAccessibilityInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<PolkaswapNavigationLayoutTestActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val automation get() = instrumentation.uiAutomation
    private val context get() = instrumentation.targetContext

    @Test
    fun actualContinueDismissalRecordsActiveRootAndInteractiveWindowState() {
        val args = InstrumentationRegistry.getArguments()
        val nonce = checkNotNull(args.getString("fearlessWarningNonce")).also { check(it.matches(Regex("[0-9a-f]{32}"))) }
        val source = checkNotNull(args.getString("fearlessWarningSourceCommit")).also { check(it.matches(Regex("[0-9a-f]{40}"))) }
        check(args.getString("fearlessWarningOwnedUid") == "exclusively-owned-disposable-app-uid")
        check(args.getString("fearlessWarningUid") == Process.myUid().toString())
        check(context.packageName == "jp.co.soramitsu.fearless.debug")
        check(Process.myUid() == context.applicationInfo.uid)
        check(Build.VERSION.SDK_INT == 26)
        val destination = File(context.cacheDir, "startup-warning-accessibility-$nonce.json")
        check(!destination.exists())
        val originalFlags = automation.serviceInfo.flags
        val samples = JSONArray()
        val report = JSONObject().put("formatVersion", 1).put("api", Build.VERSION.SDK_INT)
            .put("uid", Process.myUid()).put("nonce", nonce).put("sourceCommit", source)
            .put("originalServiceFlags", originalFlags).put("samples", samples)
            .put("actualClickAttempts", 0).put("actualClickAccepted", false).put("passed", false)
        var alert: AlertBottomSheet? = null
        var retries = 0
        try {
            compose.setContent { Box(Modifier.fillMaxSize().background(Color.Black)) }
            compose.waitForIdle()
            // Match the existing PIN fixture first; interactive-window retrieval is a later diagnostic.
            automation.serviceInfo = automation.serviceInfo.apply {
                flags = originalFlags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            }
            onMain {
                val themed = ContextThemeWrapper(compose.activity, jp.co.soramitsu.app.R.style.Theme_Soramitsu_Fearless)
                alert = createStartupConnectionAlert(themed, InitializationStep.ChainsConfig) { retries++ }
                alert!!.show()
            }
            val shown = checkNotNull(alert)
            var continueReady = false
            for (attempt in 0..50) {
                val snapshot = sample(shown, "before-click", interactive = false)
                samples.put(snapshot)
                val matches = snapshot.getJSONObject("activeRoot").getJSONArray("continueNodes")
                if (matches.length() == 1 && matches.getJSONObject(0).getBoolean("visible")) {
                    continueReady = true
                    break
                }
                if (attempt < 50) SystemClock.sleep(100)
            }
            assertTrue("One visible real Continue control must be observed before clicking", continueReady)
            for ((id, label) in listOf(
                R.id.alertTitle to R.string.common_warning,
                R.id.alertMessage to R.string.startup_network_refresh_unavailable,
                R.id.alertSecondaryButton to R.string.common_continue
            )) {
                val root = checkNotNull(automation.rootInActiveWindow)
                val matches = try { root.findAccessibilityNodeInfosByViewId(context.resources.getResourceName(id)) }
                    finally { root.recycle() }
                try {
                    assertEquals(1, matches.size)
                    val node = matches.single()
                    assertEquals(context.packageName, node.packageName.toString())
                    assertEquals(context.getString(label), node.text.toString())
                    assertTrue(node.isVisibleToUser)
                    if (id == R.id.alertSecondaryButton) {
                        assertTrue(node.isEnabled && node.isClickable)
                        report.put("actualClickAttempts", 1)
                        val accepted = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        report.put("actualClickAccepted", accepted)
                        assertTrue(accepted)
                    }
                } finally { matches.forEach { it.recycle() } }
            }
            // Retain every observation for the original five-second predicate, including null roots.
            var originalPredicateObserved = false
            val start = SystemClock.elapsedRealtime()
            repeat(51) {
                val snapshot = sample(shown, "report-view-ids-only", interactive = false)
                snapshot.put("millisecondsAfterClickPollStart", SystemClock.elapsedRealtime() - start)
                samples.put(snapshot)
                val active = snapshot.getJSONObject("activeRoot")
                if (active.getBoolean("present") && active.getJSONArray("continueNodes").length() == 0) {
                    originalPredicateObserved = true
                }
                if (it < 50) SystemClock.sleep(100)
            }
            report.put("originalNonNullActiveRootWithoutContinueObserved", originalPredicateObserved)
            automation.serviceInfo = automation.serviceInfo.apply {
                flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            }
            var actualGoneObserved = false
            var appWindowWithoutContinueObserved = false
            repeat(21) {
                val snapshot = sample(shown, "retrieve-interactive-windows", interactive = true)
                samples.put(snapshot)
                val dialog = snapshot.getJSONObject("dialog")
                if (!dialog.getBoolean("showing") && !dialog.getBoolean("decorAttached") &&
                    !dialog.getBoolean("continueAttached")) actualGoneObserved = true
                val windows = snapshot.getJSONArray("windows")
                var appWindow = false
                var continuePresent = false
                for (index in 0 until windows.length()) {
                    val root = windows.getJSONObject(index).getJSONObject("root")
                    if (root.optString("package") == context.packageName) appWindow = true
                    if (root.getJSONArray("continueNodes").length() != 0) continuePresent = true
                }
                if (appWindow && !continuePresent) appWindowWithoutContinueObserved = true
                if (it < 20) SystemClock.sleep(100)
            }
            report.put("actualDialogGoneObserved", actualGoneObserved)
                .put("appInteractiveWindowWithoutContinueObserved", appWindowWithoutContinueObserved)
            onMain { assertFalse(shown.isShowing); assertEquals(0, retries) }
            assertTrue("The actual dialog and views must detach after the real click", actualGoneObserved)
            assertTrue("A real app window must be observed without the dismissed Continue view", appWindowWithoutContinueObserved)
            report.put("passed", true)
        } catch (failure: Throwable) {
            report.put("failureClass", failure.javaClass.name)
            throw failure
        } finally {
            // Persist before cleanup can dismiss anything. A null active root never proves disappearance.
            try {
                try {
                    alert?.let { samples.put(sample(it, "finally-before-cleanup",
                        interactive = automation.serviceInfo.flags and AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS != 0)) }
                } catch (diagnosticFailure: Throwable) {
                    report.put("finalSampleFailureClass", diagnosticFailure.javaClass.name)
                }
                report.put("retryCallbacks", onMain { retries })
                    .put("finalServiceFlagsBeforeRestore", automation.serviceInfo.flags)
                check(destination.createNewFile())
                destination.writeText(report.toString(2))
            } finally {
                automation.serviceInfo = automation.serviceInfo.apply { flags = originalFlags }
                onMain { alert?.takeIf { it.isShowing }?.dismiss() }
            }
        }
    }

    private fun sample(alert: AlertBottomSheet, phase: String, interactive: Boolean): JSONObject {
        val dialog = onMain {
            val decor = alert.window?.decorView
            val button = alert.findViewById<View>(R.id.alertSecondaryButton)
            JSONObject().put("showing", alert.isShowing).put("decorAttached", decor?.isAttachedToWindow == true)
                .put("decorShown", decor?.isShown == true).put("decorWindowFocus", decor?.hasWindowFocus() == true)
                .put("continueAttached", button?.isAttachedToWindow == true)
                .put("continueShown", button?.isShown == true).put("continueWindowFocus", button?.hasWindowFocus() == true)
                .put("hostWindowFocus", compose.activity.hasWindowFocus())
        }
        val active = automation.rootInActiveWindow
        val activeState = try { rootState(active) } finally { active?.recycle() }
        val windows = JSONArray()
        if (interactive) {
            val observed = automation.windows
            try {
                check(observed.size <= 16)
                observed.forEach { window ->
                    val root = window.root
                    try {
                        windows.put(JSONObject().put("id", window.id).put("type", window.type)
                            .put("active", window.isActive).put("focused", window.isFocused)
                            .put("accessibilityFocused", window.isAccessibilityFocused)
                            .put("root", rootState(root)))
                    } finally { root?.recycle() }
                }
            } finally { observed.forEach { it.recycle() } }
        }
        return JSONObject().put("phase", phase).put("elapsedRealtime", SystemClock.elapsedRealtime())
            .put("serviceFlags", automation.serviceInfo.flags).put("dialog", dialog)
            .put("activeRoot", activeState).put("windows", windows)
    }

    private fun rootState(root: AccessibilityNodeInfo?): JSONObject {
        val result = JSONObject().put("present", root != null).put("continueNodes", JSONArray())
        if (root == null) return result
        result.put("objectIdentity", System.identityHashCode(root)).put("nodeHash", root.hashCode())
            .put("windowId", root.windowId).put("package", root.packageName?.toString() ?: JSONObject.NULL)
            .put("class", root.className?.toString() ?: JSONObject.NULL).put("visible", root.isVisibleToUser)
        val found = root.findAccessibilityNodeInfosByViewId(context.resources.getResourceName(R.id.alertSecondaryButton))
        try {
            check(found.size <= 8)
            found.forEach { node ->
                val bounds = Rect().also(node::getBoundsInScreen)
                result.getJSONArray("continueNodes").put(JSONObject().put("nodeHash", node.hashCode())
                    .put("windowId", node.windowId).put("package", node.packageName?.toString() ?: JSONObject.NULL)
                    .put("resourceId", node.viewIdResourceName ?: JSONObject.NULL)
                    .put("text", node.text?.toString() ?: JSONObject.NULL).put("visible", node.isVisibleToUser)
                    .put("enabled", node.isEnabled).put("clickable", node.isClickable)
                    .put("bounds", bounds.flattenToString()))
            }
        } finally { found.forEach { it.recycle() } }
        return result
    }

    private fun <T> onMain(action: () -> T): T {
        var result: Result<T>? = null
        instrumentation.runOnMainSync { result = runCatching(action) }
        return checkNotNull(result).getOrThrow()
    }
}
