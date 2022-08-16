package jp.co.soramitsu.common.compose.component

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

class FearlessCorneredShape(private val cornerRadius: Float = 8f) : Shape {

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        return Outline.Generic(
            // Draw your custom path here
            path = drawPath(size = size, cornerRadius = cornerRadius)
        )
    }

    private fun drawPath(size: Size, cornerRadius: Float): Path {
        return Path().apply {
            reset()
            // Top left corner
            relativeMoveTo(0f, 12f)
            relativeLineTo(12f, -12f)
            lineTo(x = size.width - cornerRadius, y = 0f)
            // Top right corner
            arcTo(
                rect = Rect(
                    left = size.width - 2 * cornerRadius,
                    top = 0f,
                    right = size.width,
                    bottom = 2 * cornerRadius
                ),
                startAngleDegrees = -90.0f,
                sweepAngleDegrees = 90.0f,
                forceMoveTo = false
            )
            lineTo(x = size.width, y = size.height - 12f)
            // Bottom right corner
            relativeLineTo(-12f, 12f)
            lineTo(x = cornerRadius, y = size.height)
            // Bottom left corner
            arcTo(
                rect = Rect(
                    left = 0f,
                    top = size.height - 2 * cornerRadius,
                    right = 2 * cornerRadius,
                    bottom = size.height
                ),
                startAngleDegrees = 90.0f,
                sweepAngleDegrees = 90.0f,
                forceMoveTo = false
            )
            lineTo(x = 0f, y = 12f)
            close()
        }
    }
}
