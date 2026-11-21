package jp.co.soramitsu.common.compose.theme.fw

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.AbstractComposeView
import jp.co.soramitsu.common.compose.theme.FearlessTheme

/**
 * One overlay over an activity's whole content for the seal drawn over the screen (brand kit SPEC v4 part 3, "Where":
 * a screen whose own progress is a spinner in its button shows the seal above it on ink at 72 %). It draws
 * [FwTransferSealOverlay] from the shared [jp.co.soramitsu.common.feedback.TransferSeal] state, so a success screen
 * continues the figure. It never takes a touch or a key, is hidden from accessibility (the screen's own words stay its
 * text), and changes no layout: it is a sibling laid over the content. It draws nothing while no operation asks for
 * it, and leaves with the operation. Dialog windows (an error, a validation sheet) are above it, never under its ink.
 */
@SuppressLint("ViewConstructor")
class TransferSealOverlayHost(context: Context) : AbstractComposeView(context) {

    init {
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        isClickable = false
        isFocusable = false
        tag = TAG
    }

    @Composable
    override fun Content() {
        FearlessTheme { FwTransferSealOverlay() }
    }

    // Touches always reach the screen underneath, exactly as without the overlay.
    @SuppressLint("ClickableViewAccessibility")
    override fun dispatchTouchEvent(event: MotionEvent?): Boolean = false
    override fun dispatchGenericMotionEvent(event: MotionEvent?): Boolean = false
    override fun onInterceptTouchEvent(event: MotionEvent?): Boolean = false

    companion object {
        const val TAG = "fw_transfer_seal_overlay"

        /** Lays the overlay over [activity]'s content once; later calls return the same host. */
        fun install(activity: Activity): TransferSealOverlayHost {
            val content = activity.findViewById<ViewGroup>(android.R.id.content)
            (content.findViewWithTag<View>(TAG) as? TransferSealOverlayHost)?.let { return it }
            val host = TransferSealOverlayHost(activity)
            content.addView(host, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            return host
        }
    }
}
