package jp.co.soramitsu.common.compose.theme.fw

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Static page decoration. Drawing adds no semantics and preserves every child action. */
fun Modifier.fwBackdrop(fx: FwEffects, c: FwColors): Modifier = drawWithCache {
    val blobs = listOf(
        Triple(c.brand.copy(alpha = 0.18f), Offset(size.width * 0.15f, size.height * 0.12f), size.width * 0.55f),
        Triple(c.info.copy(alpha = 0.12f), Offset(size.width * 0.95f, size.height * 0.08f), size.width * 0.45f),
        Triple(c.violet.copy(alpha = 0.16f), Offset(size.width * 0.50f, size.height), size.width * 0.60f)
    ).filter { it.third > 0f }.map { (color, center, radius) ->
        Triple(Brush.radialGradient(listOf(color, color.copy(alpha = 0f)), center, radius), center, radius)
    }
    onDrawBehind {
        drawRect(c.bgBase)
        if (fx.aurora) blobs.forEach { (brush, center, radius) -> drawCircle(brush, radius, center) }
    }
}

/** Card on a static backdrop; interactive controls still need their own control boundary. */
fun Modifier.fwGlass(
    fx: FwEffects,
    c: FwColors,
    shape: Shape = RoundedCornerShape(16.dp),
    accent: Boolean = false
): Modifier = if (!fx.glass) {
    clip(shape).background(c.surfaceRaised).fwInsideBorder(1.dp, SolidColor(if (fx.highContrast) c.strokeControl else c.strokeStrong), shape)
} else {
    clip(shape).background(c.glass).background(c.glassSheen).drawBehind {
        val inset = 16.dp.toPx()
        if (size.width > inset * 2f) {
            drawLine(Color.White.copy(alpha = 0.10f), Offset(inset, 1.dp.toPx()),
                Offset(size.width - inset, 1.dp.toPx()), 1.dp.toPx())
        }
    }.fwInsideBorder(1.dp, if (accent) FwBrush.Accent else FwBrush.Hairline, shape)
}

/** Phase 0 chrome uses the qualified no-blur tint; Calm/Reduced/HC is fully opaque. */
fun Modifier.fwChrome(fx: FwEffects, c: FwColors, shape: Shape = RoundedCornerShape(0.dp)): Modifier =
    clip(shape).background(if (fx.paintReduced) c.surfaceRaised else c.chromeSolid)

/** Place before the control's clip; both caller state and inherited effects must allow glow. */
fun Modifier.fwGlow(
    fx: FwEffects,
    shape: Shape,
    enabled: Boolean = true,
    color: Color = FwStandard.brand,
    dy: Dp = 6.dp
): Modifier = if (!enabled || !fx.glow) this else drawWithCache {
    val path = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
    val rings = listOf(3.dp to 0.22f, 7.dp to 0.12f, 12.dp to 0.06f, 18.dp to 0.03f).map { (width, alpha) ->
        width.toPx() to alpha
    }
    val center = Offset(size.width / 2f, size.height / 2f + dy.toPx())
    val radius = size.minDimension / 2f + 14.dp.toPx()
    val halo = Brush.radialGradient(listOf(color.copy(alpha = 0.40f), color.copy(alpha = 0f)), center, radius)
    onDrawBehind {
        if (shape == CircleShape && size.width == size.height) drawCircle(halo, radius, center) else translate(top = dy.toPx()) {
            rings.forEach { (width, alpha) -> drawPath(path, color.copy(alpha = alpha), style = Stroke(width * 2f)) }
        }
    }
}

/** Opaque control with an inside rim. Set elevated=false for repeated keys/list controls. */
fun Modifier.fwNeuRaised(
    fx: FwEffects,
    c: FwColors,
    shape: Shape,
    pressed: Boolean = false,
    elevated: Boolean = true
): Modifier {
    val shadowed = fx.neuShadows && elevated && !pressed
    val outer = if (shadowed && Build.VERSION.SDK_INT < 28) shadow(6.dp, shape, clip = false) else this
    return outer.drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val caster = Paint().apply { color = c.surfaceRaised }
        val paint = caster.asFrameworkPaint().apply { isAntiAlias = true }
        val bevel = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.06f), Color.Transparent, Color.Black.copy(alpha = 0.20f)))
        val inset = Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent), endY = 6.dp.toPx())
        onDrawBehind {
            if (shadowed && Build.VERSION.SDK_INT >= 28) drawIntoCanvas { canvas ->
                try {
                    paint.setShadowLayer(7.dp.toPx(), 6.dp.toPx(), 6.dp.toPx(), Color.Black.copy(alpha = 0.65f).toArgb())
                    canvas.drawOutline(outline, caster)
                    paint.setShadowLayer(5.dp.toPx(), -4.dp.toPx(), -4.dp.toPx(), Color.White.copy(alpha = 0.035f).toArgb())
                    canvas.drawOutline(outline, caster)
                } finally {
                    paint.clearShadowLayer()
                }
            }
            drawOutline(outline, if (pressed) c.surfacePressed else c.surfaceRaised)
            if (fx.neuShadows) drawOutline(outline, if (pressed) inset else bevel)
        }
    }.clip(shape).fwInsideBorder(1.dp, SolidColor(c.strokeControl), shape)
}

/** Input well: decoration is below text; error wins over focus and neither depends on effects. */
fun Modifier.fwNeuInset(
    fx: FwEffects,
    c: FwColors,
    shape: Shape = RoundedCornerShape(12.dp),
    focused: Boolean = false,
    error: Boolean = false
): Modifier = clip(shape).background(c.surfaceSunken).drawWithCache {
    val top = Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent), endY = 6.dp.toPx())
    val bottom = Brush.verticalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.05f)),
        startY = (size.height - 2.dp.toPx()).coerceAtLeast(0f), endY = size.height.coerceAtLeast(1f))
    onDrawBehind {
        if (fx.neuShadows) {
            drawRect(top)
            drawRect(bottom)
        }
    }
}.fwInsideBorder(if (focused || error) 2.dp else 1.dp, SolidColor(when {
    error -> c.danger
    focused -> c.focus
    else -> c.strokeControl
}), shape)

// Callers clip the whole control to the same outline. A centered double-width stroke
// leaves the requested width inside that clip. Drawing directly preserves translucent
// colors on generic paths; the current Compose generic border renders these too faintly.
private fun Modifier.fwInsideBorder(width: Dp, brush: Brush, shape: Shape): Modifier = drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val stroke = Stroke(width.toPx() * 2f)
    onDrawWithContent {
        drawContent()
        drawOutline(outline, brush, style = stroke)
    }
}
