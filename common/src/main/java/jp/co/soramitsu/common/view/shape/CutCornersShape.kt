package jp.co.soramitsu.common.view.shape

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.shapes.Shape

class CutCornersShape(
    private val cornerSizePx: Float,
    private val strokeSizePx: Float,
    private val drawingColor: Int,
    val type: Type
) : Shape() {
    private val drawingPaint = createPaint()

    private val path: Path = Path()

    enum class Type(val paintStyle: Paint.Style) {
        OUTLINE(Paint.Style.STROKE), FILL(Paint.Style.FILL)
    }

    override fun onResize(width: Float, height: Float) {
        super.onResize(width, height)

        val dx = strokeSizePx / 2.0f
        val dy = strokeSizePx / 2.0f
        val w = width - dx
        val h = height - dy

        path.reset()
        path.moveTo(dx + cornerSizePx, dy)
        path.lineTo(w, dy)
        path.lineTo(w, h - cornerSizePx)
        path.lineTo(w - cornerSizePx, h)
        path.lineTo(dx, h)
        path.lineTo(dx, dy + cornerSizePx)

        path.close()
    }

    override fun draw(canvas: Canvas, paint: Paint) {
        canvas.drawPath(path, drawingPaint)
    }

    private fun createPaint() = Paint().apply {
        color = drawingColor
        style = type.paintStyle
        strokeWidth = strokeSizePx
        isAntiAlias = true
        isDither = true
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
}