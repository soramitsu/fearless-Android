package jp.co.soramitsu.common.compose.theme.fw

import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.max

/**
 * The page under every sun-lit screen: one base colour, the warm bounce of the brand sun and the
 * violet sky from the bottom-right (DESIGN-SPEC 2). Drawing adds no semantics and preserves every
 * child action. Gradients are dithered so the faint light does not band.
 */
fun Modifier.fwBackdrop(fx: FwEffects, c: FwColors, sun: Offset? = null): Modifier = drawWithCache {
    val light = if (fx.aurora && size.width > 0f && size.height > 0f) {
        FwPageLight(size, c, sun ?: Offset(size.width * FwLight.SUN_X, size.height * FwLight.SUN_Y))
    } else null
    onDrawBehind {
        drawRect(c.bgBase)
        light?.let { drawIntoCanvas { canvas -> it.draw(canvas.nativeCanvas, size) } }
    }
}

/** Framework paints for the page light; shared by Compose and the XML backdrop drawable. */
internal class FwPageLight(size: Size, c: FwColors, sun: Offset) {
    private val bounce = android.graphics.Paint().apply {
        isDither = true
        shader = RadialGradient(sun.x, sun.y, max(1f, size.width * FwLight.SUN_BOUNCE_REACH),
            intArrayOf(c.sunBounce.toArgb(), c.sunBounceMid.toArgb(), c.sunBounce.copy(alpha = 0f).toArgb()),
            floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
    }
    private val sky = android.graphics.Paint().apply {
        isDither = true
        shader = RadialGradient(size.width, size.height, max(1f, size.width * FwLight.SKY_REACH),
            intArrayOf(c.skyAmbient.toArgb(), c.skyAmbient.copy(alpha = 0f).toArgb()), null, Shader.TileMode.CLAMP)
    }

    fun draw(canvas: android.graphics.Canvas, size: Size) {
        canvas.drawRect(0f, 0f, size.width, size.height, bounce)
        canvas.drawRect(0f, 0f, size.width, size.height, sky)
    }
}

/**
 * A neumorphic surface lit by the key light (Raised-1/Raised-2), sunk into a well (Inset, or any
 * pressed surface) or glowing in its own light (Emissive). [sunRim] fixes the sun-rim strength;
 * null derives it from the surface's distance to the sun on screen. Interactive controls keep a
 * visible boundary when effects are reduced or contrast is increased.
 */
fun Modifier.fwSurface(
    fx: FwEffects,
    c: FwColors,
    shape: Shape,
    level: FwElevation = FwElevation.Raised2,
    pressed: Boolean = false,
    sunRim: Float? = null,
    fill: Color = Color.Unspecified,
    interactive: Boolean = false
): Modifier = this then FwSurfaceElement(fx, c, shape, level, pressed, sunRim, fill, interactive)

private data class FwSurfaceElement(
    val fx: FwEffects,
    val c: FwColors,
    val shape: Shape,
    val level: FwElevation,
    val pressed: Boolean,
    val sunRim: Float?,
    val fill: Color,
    val interactive: Boolean
) : ModifierNodeElement<FwSurfaceNode>() {
    override fun create() = FwSurfaceNode(this)
    override fun update(node: FwSurfaceNode) = node.update(this)
    override fun InspectorInfo.inspectableProperties() {
        name = "fwSurface"
        properties["level"] = level
        properties["pressed"] = pressed
    }
}

private class FwSurfaceNode(private var spec: FwSurfaceElement) :
    Modifier.Node(), DrawModifierNode, GlobalPositionAwareModifierNode, CompositionLocalConsumerModifierNode {

    private var autoRim = 0f
    private var cache: FwSurfacePaint? = null

    fun update(next: FwSurfaceElement) {
        if (next != spec) {
            spec = next
            cache = null
            invalidateDraw()
        }
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        if (spec.sunRim != null || !coordinates.isAttached) return
        val root = currentValueOf(LocalView).rootView
        val width = root.width.toFloat()
        val position = coordinates.positionInWindow()
        val distance = fwSunDistance(width * FwLight.SUN_X, root.height * FwLight.SUN_Y,
            position.x, position.y, position.x + coordinates.size.width)
        val rim = fwQuantizedRim(fwSunRimAlpha(distance, width))
        if (rim != autoRim) {
            autoRim = rim
            invalidateDraw()
        }
    }

    override fun ContentDrawScope.draw() {
        val paint = cache?.takeIf { it.size == size && it.layoutDirection == layoutDirection }
            ?: FwSurfacePaint(spec, size, layoutDirection, this).also { cache = it }
        paint.drawBehind(this, spec.sunRim ?: autoRim)
        drawContent()
        paint.drawEdge(this, spec.sunRim ?: autoRim)
    }
}

/** Everything that depends only on size: outline, paths and framework paints. */
private class FwSurfacePaint(
    private val spec: FwSurfaceElement,
    val size: Size,
    val layoutDirection: LayoutDirection,
    density: DrawScope
) {
    private val c = spec.c
    private val fx = spec.fx
    private val outline: Outline = spec.shape.createOutline(size, layoutDirection, density)
    private val outlinePath = Path().apply { addOutline(outline) }
    private val sunk = spec.pressed || spec.level == FwElevation.Inset
    private val emissive = spec.level == FwElevation.Emissive
    private val fill: Color = when {
        spec.fill.isSpecified -> spec.fill
        sunk && !emissive -> c.surfaceSunken
        else -> c.surfaceRaised
    }
    private val px = with(density) { 1.dp.toPx() }
    private val rimStroke = Stroke(px * 2f)

    private val outer: List<Pair<FwShadow, Color>> = if (!fx.neuShadows || sunk || emissive) emptyList() else {
        val pair = requireNotNull(fwShadowPair(spec.level))
        // The shadow first, the highlight on top of it (CSS box-shadow order of the reference).
        listOf(pair.shadow to c.keyShadow, pair.highlight to c.keyHighlight)
    }
    private val inner: List<Pair<FwShadow, Color>> = if (!fx.neuShadows || !sunk) emptyList() else {
        val pair = requireNotNull(fwShadowPair(FwElevation.Inset))
        listOf(pair.shadow to c.insetShadow, pair.highlight to c.insetHighlight)
    }
    private val caster = Paint().apply { color = fill.copy(alpha = 1f) }
    private val frame: Path? = if (inner.isEmpty()) null else Path().apply {
        fillType = PathFillType.EvenOdd
        val margin = 64f * px
        addRect(Rect(-margin, -margin, size.width + margin, size.height + margin))
        addOutline(outline)
    }
    private val glowRings = if (emissive && fx.glow && Build.VERSION.SDK_INT < 28) {
        listOf(3.dp to 0.20f, 7.dp to 0.11f, 12.dp to 0.05f).map { (w, a) -> with(density) { w.toPx() } to a }
    } else emptyList()

    fun drawBehind(scope: DrawScope, rim: Float) = with(scope) {
        if (emissive) {
            drawGlow()
            when {
                spec.fill.isSpecified -> {
                    drawOutline(outline, SolidColor(spec.fill))
                    if (spec.pressed) drawOutline(outline, c.insetShadow.copy(alpha = 0.22f))
                }
                // The brand's pressed pink (#D6006A) replaces the gradient while a primary action is held.
                spec.pressed -> drawOutline(outline, c.brandPressed)
                else -> drawOutline(outline, FwBrush.PrimaryFill)
            }
            return@with
        }
        drawOuterShadows()
        drawOutline(outline, fill)
        drawInnerShadows()
        if (rim > 0f && !sunk) drawSunWash(rim)
    }

    fun drawEdge(scope: DrawScope, rim: Float) = with(scope) {
        when {
            fx.highContrast -> clipPath(outlinePath) { drawOutline(outline, c.strokeControl, style = rimStroke) }
            !fx.neuShadows && (spec.interactive || sunk) -> clipPath(outlinePath) { drawOutline(outline, c.strokeControl, style = rimStroke) }
            !fx.neuShadows -> clipPath(outlinePath) { drawOutline(outline, c.strokeStrong, style = rimStroke) }
            emissive -> clipPath(outlinePath) {
                drawOutline(outline, Brush.verticalGradient(0f to c.textOnAccent.copy(alpha = 0.35f),
                    0.25f to c.textOnAccent.copy(alpha = 0f), endY = size.height), style = rimStroke)
            }
            sunk -> clipPath(outlinePath) {
                drawOutline(outline, Brush.verticalGradient(0f to Color.Transparent, 0.6f to Color.Transparent,
                    1f to c.insetHighlight, endY = size.height), style = rimStroke)
            }
            else -> clipPath(outlinePath) { drawOutline(outline, rimBrush(c, rim, size.height), style = rimStroke) }
        }
    }

    private fun DrawScope.drawGlow() {
        if (!fx.glow || spec.pressed) return
        if (Build.VERSION.SDK_INT >= 28) {
            drawIntoCanvas { canvas ->
                val framework = caster.asFrameworkPaint()
                try {
                    framework.color = c.fillBottom.toArgb()
                    framework.setShadowLayer(22.dp.toPx() * BLUR_TO_RADIUS, 0f, 8.dp.toPx(), c.emissiveGlow.toArgb())
                    canvas.drawOutline(outline, caster)
                } finally {
                    framework.clearShadowLayer()
                    framework.color = fill.copy(alpha = 1f).toArgb()
                }
            }
        } else {
            translate(top = 6.dp.toPx()) {
                glowRings.forEach { (width, alpha) -> drawOutline(outline, c.brand.copy(alpha = alpha), style = Stroke(width * 2f)) }
            }
        }
    }

    private fun DrawScope.drawOuterShadows() {
        if (outer.isEmpty()) return
        if (Build.VERSION.SDK_INT >= 28) {
            drawIntoCanvas { canvas ->
                val framework = caster.asFrameworkPaint().apply { isAntiAlias = true }
                try {
                    outer.forEach { (shadow, color) ->
                        framework.setShadowLayer(shadow.blur.dp.toPx() * BLUR_TO_RADIUS, shadow.dx.dp.toPx(), shadow.dy.dp.toPx(), color.toArgb())
                        canvas.drawOutline(outline, caster)
                    }
                } finally {
                    framework.clearShadowLayer()
                }
            }
        } else {
            // API 26-27 hardware canvases cannot blur arbitrary shapes: a hard, small offset stands in.
            outer.forEach { (shadow, color) ->
                translate(shadow.dx.dp.toPx() * 0.4f, shadow.dy.dp.toPx() * 0.4f) { drawOutline(outline, color.copy(alpha = color.alpha * 0.6f)) }
            }
        }
    }

    private fun DrawScope.drawInnerShadows() {
        val path = frame ?: return
        clipPath(outlinePath) {
            if (Build.VERSION.SDK_INT >= 28) {
                drawIntoCanvas { canvas ->
                    val framework = caster.asFrameworkPaint().apply { isAntiAlias = true }
                    try {
                        inner.forEach { (shadow, color) ->
                            framework.setShadowLayer(shadow.blur.dp.toPx() * BLUR_TO_RADIUS, shadow.dx.dp.toPx(), shadow.dy.dp.toPx(), color.toArgb())
                            canvas.drawPath(path, caster)
                        }
                    } finally {
                        framework.clearShadowLayer()
                    }
                }
            } else {
                val depth = 6.dp.toPx()
                drawRect(Brush.verticalGradient(listOf(c.insetShadow, Color.Transparent), endY = depth))
                drawRect(Brush.horizontalGradient(listOf(c.insetShadow.copy(alpha = c.insetShadow.alpha * 0.6f), Color.Transparent), endX = depth))
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, c.insetHighlight),
                    startY = (size.height - 3.dp.toPx()).coerceAtLeast(0f), endY = size.height.coerceAtLeast(1f)))
            }
        }
    }

    /** The sun bounce lightens the top of surfaces near the sun a little (it is a light, not a stroke). */
    private fun DrawScope.drawSunWash(rim: Float) {
        clipPath(outlinePath) {
            drawRect(Brush.verticalGradient(0f to c.sunRim.copy(alpha = rim * 0.10f), 1f to Color.Transparent,
                endY = (size.height * 0.5f).coerceAtLeast(1f)))
        }
    }
}

/**
 * CSS blur B is a Gaussian of sigma B/2; Skia turns a shadow-layer radius r into sigma 0.577 r + 0.5.
 * 0.8 B keeps the reference softness.
 */
internal const val BLUR_TO_RADIUS = 0.8f

/** Rim: the sun-rim pink at the very top (distance weighted), white 8 % at 30 % height, gone by 70 %. */
internal fun rimBrush(c: FwColors, sunRim: Float, height: Float): Brush {
    val weight = (sunRim / FwLight.SUN_RIM_MAX).coerceIn(0f, 1f)
    val top = lerp(c.rimLight, c.sunRim.copy(alpha = max(sunRim, c.rimLight.alpha)), weight)
    return Brush.verticalGradient(0f to top, 0.3f to c.rimLight.copy(alpha = 0.08f), 0.7f to Color.Transparent,
        endY = height.coerceAtLeast(1f))
}

/** Raised-2 card on the page. Kept for v1 surfaces; interactive controls still need their own boundary. */
fun Modifier.fwGlass(
    fx: FwEffects,
    c: FwColors,
    shape: Shape = RoundedCornerShape(FwShape.CARD),
    accent: Boolean = false
): Modifier = fwSurface(fx, c, shape, FwElevation.Raised2, sunRim = if (accent) FwLight.SUN_RIM_MAX else null)

/** Bars (toolbars, sheets' headers): opaque base without shadows; Calm/Reduced/HC stay opaque too. */
fun Modifier.fwChrome(fx: FwEffects, c: FwColors, shape: Shape = RoundedCornerShape(0.dp)): Modifier =
    drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        onDrawBehind { drawOutline(outline, if (fx.paintReduced) c.surfaceRaised else c.chromeSolid) }
    }

/** Emissive glow in [color] around [shape]. Place before the control's clip. */
fun Modifier.fwGlow(
    fx: FwEffects,
    shape: Shape,
    enabled: Boolean = true,
    color: Color = FwStandard.emissiveGlow,
    dy: Dp = 8.dp
): Modifier = if (!enabled || !fx.glow) this else drawWithCache {
    val path = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
    val rings = listOf(3.dp to 0.30f, 7.dp to 0.16f, 12.dp to 0.08f, 18.dp to 0.04f).map { (width, alpha) -> width.toPx() to alpha }
    val center = Offset(size.width / 2f, size.height / 2f + dy.toPx())
    val radius = size.minDimension / 2f + 18.dp.toPx()
    val halo = Brush.radialGradient(listOf(color.copy(alpha = color.alpha * 0.9f), color.copy(alpha = 0f)), center, radius)
    onDrawBehind {
        if (shape == CircleShape && size.width == size.height) drawCircle(halo, radius, center) else translate(top = dy.toPx()) {
            rings.forEach { (width, alpha) -> drawPath(path, color.copy(alpha = color.alpha * alpha / 0.45f), style = Stroke(width * 2f)) }
        }
    }
}

/** Opaque control lit by the key light. Set elevated=false for repeated keys and list controls (Raised-1). */
fun Modifier.fwNeuRaised(
    fx: FwEffects,
    c: FwColors,
    shape: Shape,
    pressed: Boolean = false,
    elevated: Boolean = true
): Modifier = fwSurface(fx, c, shape, if (elevated) FwElevation.Raised2 else FwElevation.Raised1, pressed,
    fill = c.surfaceRaised, interactive = true)

/** Input well: bgDeep with mirrored inner shadows; error wins over focus and neither depends on effects. */
fun Modifier.fwNeuInset(
    fx: FwEffects,
    c: FwColors,
    shape: Shape = RoundedCornerShape(12.dp),
    focused: Boolean = false,
    error: Boolean = false
): Modifier {
    // Draw state feedback after the surface rim so reduced/high-contrast strokes cannot tint it.
    // Without a state border the surface itself supplies the neutral rim exactly once.
    val border = if (focused || error) fwInsideBorder(2.dp, SolidColor(if (error) c.danger else c.brand), shape) else this
    return border.fwSurface(fx, c, shape, FwElevation.Inset, interactive = true)
}

/** Shared radii (DESIGN-SPEC 3): cards 22, buttons 18, pills full, icon wells circular. */
object FwShape {
    val CARD = 22.dp
    val BUTTON = 18.dp
    val HERO = 26.dp
    val WELL = 16.dp
    val SHEET_TOP = 24.dp
    val Card = RoundedCornerShape(CARD)
    val Button = RoundedCornerShape(BUTTON)
    val Hero = RoundedCornerShape(HERO)
    val Well = RoundedCornerShape(WELL)
    val Pill = RoundedCornerShape(percent = 50)
}

// Callers clip the whole control to the same outline. A centered double-width stroke
// leaves the requested width inside that clip. Drawing directly preserves translucent
// colors on generic paths; the current Compose generic border renders these too faintly.
private fun Modifier.fwInsideBorder(width: Dp, brush: Brush, shape: Shape): Modifier = drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val path = Path().apply { addOutline(outline) }
    val stroke = Stroke(width.toPx() * 2f)
    onDrawWithContent {
        drawContent()
        clipPath(path) { drawOutline(outline, brush, style = stroke) }
    }
}
