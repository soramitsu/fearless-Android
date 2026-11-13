package jp.co.soramitsu.common.view

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout

/**
 * Holds content that stops above an overlaid bar. With a fade set, the content melts into whatever is behind it
 * along its padded bottom edge instead of ending in a straight line.
 */
class BottomFadeFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    /** Fades the last [length] pixels above the bottom padding. */
    fun enableBottomFade(length: Int) {
        setWillNotDraw(false)
        isVerticalFadingEdgeEnabled = true
        setFadingEdgeLength(length)
    }

    override fun getTopFadingEdgeStrength(): Float = 0f

    override fun getBottomFadingEdgeStrength(): Float = 1f
}
