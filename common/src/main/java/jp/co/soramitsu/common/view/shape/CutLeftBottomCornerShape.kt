package jp.co.soramitsu.common.view.shape

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.shapes.Shape

class CutLeftBottomCornerShape(
    private val cornerSizeXPx: Float,
    private val cornerSizeYPx: Float,
    private val fillColor: Int
) : Shape() {
    private val drawingPaint = createPaint()

    private val path: Path = Path()

    override fun onResize(width: Float, height: Float) {
        super.onResize(width, height)

        path.reset()
        path.moveTo(0f, 0f)
        path.lineTo(width, 0f)
        path.lineTo(width, height)
        path.lineTo(cornerSizeXPx, height)
        path.lineTo(0f, cornerSizeYPx)

        path.close()
    }

    override fun draw(canvas: Canvas, paint: Paint) {
        drawingPaint.color = fillColor
        drawingPaint.style = Paint.Style.FILL

        canvas.drawPath(path, drawingPaint)
    }

    private fun createPaint() = Paint().apply {
        color = fillColor
        isAntiAlias = true
        isDither = true
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
}
