package jp.co.soramitsu.account.impl.presentation.pincode.view

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import jp.co.soramitsu.common.compose.theme.fw.FwColors
import jp.co.soramitsu.common.compose.theme.fw.FwEffects
import jp.co.soramitsu.common.compose.theme.fw.FwMotionKind
import jp.co.soramitsu.common.compose.theme.fw.FwMotionMode
import jp.co.soramitsu.common.compose.theme.fw.FwPinDotDrawable
import jp.co.soramitsu.common.compose.theme.fw.motionPolicy
import jp.co.soramitsu.feature_account_impl.R

class DotsProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : LinearLayout(context, attrs, defStyle) {

    companion object {
        const val MAX_PROGRESS = 6
        private const val SUNLIT_DOT_DP = 16
        private const val SUNLIT_DOT_MARGIN_DP = 9
    }

    private var circles: Array<View?>

    private var emptyDrawable: Drawable
    private var filledDrawable: Drawable

    private val completeListener: () -> Unit = {}

    init {
        val itemSize = context.resources.getDimensionPixelSize(R.dimen.dot_progress_view_dot_size_default)
        val itemMargin = context.resources.getDimensionPixelOffset(R.dimen.dot_progress_view_dot_margin_default)

        emptyDrawable = ContextCompat.getDrawable(context, R.drawable.ic_pincode_indicator_empty)!!
        filledDrawable = ContextCompat.getDrawable(context, R.drawable.ic_pincode_indicator_filled)!!

        circles = arrayOfNulls(MAX_PROGRESS)

        for (i in 0 until MAX_PROGRESS) {
            val circle = View(context)
            val params = LayoutParams(itemSize, itemSize)
            params.setMargins(itemMargin, 0, itemMargin, 0)
            circle.layoutParams = params
            addView(circle)
            circles[i] = circle
        }

        setProgress(0)
    }

    private var sunlitDots: List<FwPinDotDrawable>? = null
    private var sunlitFx: FwEffects? = null
    private var shown = 0

    /** Sun-lit dots: inset wells that fill with brand light from the key-light side (120 ms). */
    fun applySunlitStyle(fx: FwEffects, colors: FwColors) {
        val density = resources.displayMetrics.density
        sunlitFx = fx
        clipChildren = false
        clipToPadding = false
        val size = (SUNLIT_DOT_DP * density).toInt()
        val margin = (SUNLIT_DOT_MARGIN_DP * density).toInt()
        sunlitDots = circles.map { circle ->
            FwPinDotDrawable(colors, fx, density).also { dot ->
                circle?.background = dot
                circle?.layoutParams = LayoutParams(size, size).apply { setMargins(margin, 0, margin, 0) }
            }
        }
        shown = 0
    }

    /** A wrong PIN tints the entered dots danger until they clear. */
    fun showError(error: Boolean) {
        sunlitDots?.forEach { it.error = error }
    }

    fun setProgress(currentProgress: Int) {
        sunlitDots?.let { dots ->
            val animate = sunlitFx?.motionPolicy()?.mode(FwMotionKind.PinDot) != FwMotionMode.Still
            dots.forEachIndexed { index, dot -> dot.setFilled(index < currentProgress, animate && index >= shown) }
            shown = currentProgress.coerceIn(0, MAX_PROGRESS)
            if (currentProgress >= MAX_PROGRESS) completeListener()
            return
        }
        for (circle in circles) {
            circle?.background = emptyDrawable
        }
        if (currentProgress == 0 || currentProgress > MAX_PROGRESS) {
            return
        }
        for (i in 0 until currentProgress) {
            circles[i]?.background = filledDrawable
        }
        if (currentProgress >= MAX_PROGRESS) {
            completeListener()
        }
    }
}
