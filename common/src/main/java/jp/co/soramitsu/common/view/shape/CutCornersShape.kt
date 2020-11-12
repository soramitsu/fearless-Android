package jp.co.soramitsu.common.view.shape

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.shapes.Shape

class CutCornersShape(
    private val cornerSizePx: Float,
    private val strokeSizePx: Float,
    private val fillColor: Int,
    private val strokeColor: Int?
) : Shape() {
    private val drawingPaint = createPaint()

    private val path: Path = Path()

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
        drawingPaint.color = fillColor
        drawingPaint.style = Paint.Style.FILL

        canvas.drawPath(path, drawingPaint)

        strokeColor?.let {
            drawingPaint.color = it
            drawingPaint.style = Paint.Style.STROKE

            canvas.drawPath(path, drawingPaint)
        }
    }

    private fun createPaint() = Paint().apply {
        color = fillColor
        strokeWidth = strokeSizePx
        isAntiAlias = true
        isDither = true
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
}