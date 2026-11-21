package jp.co.soramitsu.app.root.presentation.stories

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View

/** Segmented story timer driven by uptime, independent of the system animator scale. */
class StoryProgressView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private val paint = Paint()
    private val advance = Runnable { skip() }
    private var count = 0
    private var index = 0
    private var duration = 1L
    private var remaining = 0L
    private var deadline: Long? = null
    internal var onNext: () -> Unit = {}
    internal var onPrevious: () -> Unit = {}
    internal var onFinished: () -> Unit = {}

    internal fun start(stories: Int, current: Int, durationMillis: Long) {
        pause()
        count = stories
        index = current.coerceIn(0, count.coerceAtLeast(1) - 1)
        duration = durationMillis
        remaining = duration
        resume()
        invalidate()
    }

    internal fun pause() {
        deadline?.let { remaining = (it - SystemClock.uptimeMillis()).coerceAtLeast(0) }
        deadline = null
        removeCallbacks(advance)
    }

    internal fun resume() {
        if (index >= count || deadline != null) return
        deadline = SystemClock.uptimeMillis() + remaining
        postDelayed(advance, remaining)
        invalidate()
    }

    internal fun skip() {
        if (index >= count) return
        pause()
        index++
        if (index == count) {
            invalidate()
            onFinished()
        } else {
            remaining = duration
            resume()
            onNext()
        }
    }

    internal fun reverse() {
        if (index >= count) return
        pause()
        remaining = duration
        if (index > 0) {
            index--
            onPrevious()
        }
        resume()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        resume()
    }

    override fun onDetachedFromWindow() {
        pause()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (count == 0) return
        val gap = 5f.coerceAtMost(width.toFloat() / count)
        val segment = (width - gap * (count - 1)) / count
        val barHeight = (2 * resources.displayMetrics.density).coerceAtMost(height.toFloat())
        val leftMillis = deadline?.minus(SystemClock.uptimeMillis()) ?: remaining
        val fraction = (1f - leftMillis.toFloat() / duration).coerceIn(0f, 1f)
        repeat(count) { position ->
            val slot = if (layoutDirection == LAYOUT_DIRECTION_RTL) count - 1 - position else position
            val left = slot * (segment + gap)
            paint.color = if (position < index) Color.WHITE else 0x8affffff.toInt()
            canvas.drawRect(left, 0f, left + segment, barHeight, paint)
            if (position == index) {
                paint.color = Color.WHITE
                canvas.drawRect(left, 0f, left + segment * fraction, barHeight, paint)
            }
        }
        if (deadline != null) postInvalidateOnAnimation()
    }
}
