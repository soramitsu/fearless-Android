package jp.co.soramitsu.common.view

import android.graphics.RectF
import com.google.android.material.shape.EdgeTreatment
import com.google.android.material.shape.ShapePath
import kotlin.math.abs
import kotlin.math.atan2

private typealias StartAngle = Float
private typealias SweepAngle = Float

class BottomNavigationWithFABTopEdgeTreatment(
    var fabDiameter: Float,
    var fabMargin: Float,
    var cradleVerticalOffset: Float
): EdgeTreatment() {

    private val cradlePlacement = RectF()

    private val cradleDiameter: Float
        get() = fabDiameter + fabMargin.times(2)

    private val cradleRadius: Float
        get() = cradleDiameter / 2

    private val offset: Float
        get() = cradleVerticalOffset.coerceIn(-cradleRadius, cradleRadius)

    private val angle: Float
        get() = Math.toDegrees(
            atan2(
                y = abs(offset),
                x = cradleRadius
            ).toDouble()
        ).toFloat()

    override fun getEdgePath(
        length: Float,
        center: Float,
        interpolation: Float,
        shapePath: ShapePath
    ) {
        if (fabDiameter == 0f) {
            // There is no cutout to draw.
            shapePath.lineTo(
                /* x= */ length,
                /* y= */ 0f
            )
            return
        }

        setFABPlacement(cradlePlacement, center)

        val (startAngle, sweepAngle) = calculateArcStartAndSweepAngles()

        // Draw the cutout circle.
        shapePath.addArc(
            /* left= */ cradlePlacement.left,
            /* top= */ cradlePlacement.top,
            /* right= */ cradlePlacement.right,
            /* bottom= */ cradlePlacement.bottom,
            /* startAngle= */ startAngle,
            /* sweepAngle= */ sweepAngle
        )

        // Draw the ending line after the right rounded corner.
        shapePath.lineTo(
            /* x= */ length,
            /* y= */ 0f
        )
    }

    private fun setFABPlacement(rectF: RectF, center: Float) {
        rectF.top = -cradleRadius - offset
        rectF.bottom = cradleRadius - offset

        rectF.left = center - cradleRadius
        rectF.right = center + cradleRadius
    }

    private fun calculateArcStartAndSweepAngles(): Pair<StartAngle, SweepAngle> {
        /*
            While painting an arc, keep in mind that it resembles a clock that start from 3 o'clock;
            meaning, 3 o'clock on an analog clock is actually 0 degrees in an arc

            But, [0,0] coordinates on canvas is actually [top, left] corner, hence, to start
            an arc from left side (remember, that 3 o'clock is actually stands on the right side),
            we need to calculate start angle from 180 degrees
          */
        val startAngle = -DEGREES_180 + angle

        /*
            By deducing 180, it is implied that mirroring is going to be used,
            and tacking twice the [verticalOffsetDerivedAngle] extends startAngle all the way through
            arc till startAngle is mirrored
          */
        val sweepAngle = -DEGREES_180 - angle.times(2)

        return startAngle to sweepAngle
    }

    private companion object {
        const val DEGREES_180 = 180f
    }

}