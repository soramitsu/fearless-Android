package jp.co.soramitsu.common.view

import android.os.Build
import android.view.View

/**
 * Marks a view tree that shows or takes wallet secrets (PIN, recovery phrase, seed, backup password):
 * - touches that arrive while another app's window covers it are dropped (tap-jacking);
 * - on Android 14+ only accessibility services that declare themselves accessibility tools, such as
 *   screen readers, can read it, not apps that ask for accessibility access to scrape screens.
 *
 * Children inherit both properties, so marking a screen's root view is enough.
 */
fun View.protectSensitiveContent() {
    filterTouchesWhenObscured = true
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        setAccessibilityDataSensitive(View.ACCESSIBILITY_DATA_SENSITIVE_YES)
    }
}

/**
 * On Android 12+, hides other apps' overlay windows while this window is visible, so no app can
 * draw a fake PIN pad or approve button over the wallet. Needs the normal (install-time)
 * `android.permission.HIDE_OVERLAY_WINDOWS`, which the app manifest declares.
 */
fun android.view.Window.hideOtherAppsOverlays() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setHideOverlayWindows(true)
}
