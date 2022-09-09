package jp.co.soramitsu.common.compose.component

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

class FearlessCorneredShape(
    private val cornerRadius: Dp = 8.dp,
    private val cornerCutLength: Dp = 12.dp
) : Shape {

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        return Outline.Generic(
            // Draw your custom path here
            path = drawPath(
                size = size,
                cornerRadius = cornerRadius.value * density.density,
                cornerCutLength = cornerCutLength.value * density.density
            )
        )
    }

    private fun drawPath(size: Size, cornerRadius: Float, cornerCutLength: Float): Path {
        return Path().apply {
            reset()
            // Top left corner
            relativeMoveTo(0f, cornerCutLength)
            relativeLineTo(cornerCutLength, -cornerCutLength)
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
            lineTo(x = size.width, y = size.height - cornerCutLength)
            // Bottom right corner
            relativeLineTo(-cornerCutLength, cornerCutLength)
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
            lineTo(x = 0f, y = cornerCutLength)
            close()
        }
    }
}
