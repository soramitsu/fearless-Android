package jp.co.soramitsu.common.compose.theme.fw

import android.animation.ValueAnimator
import android.app.Dialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toArgb
import kotlin.math.max
import kotlin.math.min

/**
 * View-system counterparts of the sun-lit modifiers, for screens that are still XML (PIN, bottom
 * navigation, legacy fragments). Same tokens, same light; framework drawing only.
 */
object FwViews {
    /** Effects for views outside Compose: system animator scale and the saved display preferences. */
    fun effects(context: Context): FwEffects = readFwEffectsSnapshot(context)
}

/** A sheet continues behind the navigation bar instead of ending in a dark strip above it. */
fun Dialog.fwSheetNavigationBar() {
    if (!FwRestyle.paletteEnabled) return
    window?.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
    window?.navigationBarColor = FwStandard.surfaceRaised2.toArgb()
}

/** The palette views paint with: high-contrast tokens when contrast is increased. */
@Suppress("FunctionName")
fun FwStandardOrHighContrast(context: Context): FwColors =
    if (FwViews.effects(context).highContrast) FwHighContrast else FwStandard

/** The sun-lit page: base, sun bounce at the hero position and the violet sky. */
class FwPageBackdropDrawable(private val c: FwColors, private val lit: Boolean) : Drawable() {
    private var light: FwPageLight? = null
    private var area = Size.Zero

    override fun onBoundsChange(bounds: Rect) {
        area = Size(bounds.width().toFloat(), bounds.height().toFloat())
        light = if (lit && bounds.width() > 0 && bounds.height() > 0) {
            FwPageLight(area, c, Offset(area.width * FwLight.SUN_X, area.height * FwLight.SUN_Y))
        } else null
    }

    override fun draw(canvas: Canvas) {
        canvas.save()
        canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        canvas.drawColor(c.bgBase.toArgb())
        light?.draw(canvas, area)
        canvas.restore()
    }

    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.OPAQUE
}

/**
 * A neumorphic shape for views: Raised (key-light shadows and rim), Inset (inner shadows on bgDeep)
 * or Emissive (brand gradient with its own glow). Pressed raised shapes sink into a well. The shape
 * is inset by [shadowRoom] px inside the bounds so the light has room without clipping.
 */
class FwNeuDrawable(
    private val c: FwColors,
    private val fx: FwEffects,
    private val density: Float,
    private val level: FwElevation,
    private val cornerRadius: Float? = null,
    private val shadowRoom: Float = 0f,
    private val focusRing: Int? = null
) : Drawable() {
    private var pressed = false
    private var focused = false
    private var enabled = true
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val shape = RectF()
    private val path = Path()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val caster = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val boundary = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val overlay = Paint(Paint.ANTI_ALIAS_FLAG)
    private val frame = Path().apply { fillType = Path.FillType.EVEN_ODD }

    override fun isStateful(): Boolean = true

    override fun onStateChange(state: IntArray): Boolean {
        val nowPressed = state.contains(android.R.attr.state_pressed)
        val nowFocused = state.contains(android.R.attr.state_focused)
        // A state set without state_enabled means disabled (StateListDrawable semantics).
        val nowEnabled = state.isEmpty() || state.contains(android.R.attr.state_enabled)
        if (nowPressed == pressed && nowFocused == focused && nowEnabled == enabled) return false
        pressed = nowPressed
        focused = nowFocused
        enabled = nowEnabled
        invalidateSelf()
        return true
    }

    override fun onBoundsChange(bounds: Rect) {
        shape.set(bounds)
        shape.inset(shadowRoom, shadowRoom)
        path.reset()
        val radius = radius()
        path.addRoundRect(shape, radius, radius, Path.Direction.CW)
        frame.reset()
        frame.fillType = Path.FillType.EVEN_ODD
        frame.addRect(RectF(bounds).apply { inset(-64f * density, -64f * density) }, Path.Direction.CW)
        frame.addRoundRect(shape, radius, radius, Path.Direction.CW)
        rim.strokeWidth = 2f * density
        ring.strokeWidth = 3f * density
        boundary.strokeWidth = 2f * density
        boundary.color = c.strokeControl.toArgb()
        overlay.color = c.insetShadow.copy(alpha = 0.22f).toArgb()
        rim.shader = if (level == FwElevation.Emissive) {
            LinearGradient(0f, shape.top, 0f, shape.top + shape.height() * 0.25f,
                c.textOnAccent.copy(alpha = 0.35f).toArgb(), 0, Shader.TileMode.CLAMP)
        } else {
            LinearGradient(0f, shape.top, 0f, shape.bottom, intArrayOf(c.rimLight.toArgb(), c.rimLight.copy(alpha = 0.08f).toArgb(), 0),
                floatArrayOf(0f, 0.3f, 0.7f), Shader.TileMode.CLAMP)
        }
        fill.shader = if (level == FwElevation.Emissive) {
            LinearGradient(shape.left, shape.top, shape.right, shape.bottom, c.fillTop.toArgb(), c.fillBottom.toArgb(), Shader.TileMode.CLAMP)
        } else null
    }

    private fun radius(): Float = cornerRadius?.let { min(it, min(shape.width(), shape.height()) / 2f) }
        ?: (min(shape.width(), shape.height()) / 2f)

    override fun draw(canvas: Canvas) {
        if (shape.isEmpty) return
        val sunk = pressed || level == FwElevation.Inset
        // A disabled emissive control loses its light and rests as a plain raised surface.
        if (!enabled && level == FwElevation.Emissive) {
            disabledFallback.bounds = bounds
            disabledFallback.draw(canvas)
            return
        }
        val emissive = level == FwElevation.Emissive
        val shadows = fx.neuShadows && Build.VERSION.SDK_INT >= 28
        if (emissive && fx.glow && !pressed && Build.VERSION.SDK_INT >= 28) {
            caster.color = c.fillBottom.toArgb()
            caster.setShadowLayer(22f * density * BLUR_TO_RADIUS, 0f, 8f * density, c.emissiveGlow.toArgb())
            canvas.drawPath(path, caster)
            caster.clearShadowLayer()
        } else if (!sunk && !emissive && shadows) {
            val pair = requireNotNull(fwShadowPair(level))
            caster.color = c.surfaceRaised.toArgb()
            for ((shadow, color) in listOf(pair.shadow to c.keyShadow, pair.highlight to c.keyHighlight)) {
                caster.setShadowLayer(shadow.blur * density * BLUR_TO_RADIUS, shadow.dx * density, shadow.dy * density, color.toArgb())
                canvas.drawPath(path, caster)
            }
            caster.clearShadowLayer()
        }
        if (!emissive) fill.color = (if (sunk) c.surfaceSunken else c.surfaceRaised).toArgb()
        canvas.drawPath(path, fill)
        if (emissive && pressed) canvas.drawPath(path, overlay)
        if (sunk && !emissive && shadows) {
            canvas.save()
            canvas.clipPath(path)
            val pair = requireNotNull(fwShadowPair(FwElevation.Inset))
            caster.color = c.surfaceSunken.toArgb()
            for ((shadow, color) in listOf(pair.shadow to c.insetShadow, pair.highlight to c.insetHighlight)) {
                caster.setShadowLayer(shadow.blur * density * BLUR_TO_RADIUS, shadow.dx * density, shadow.dy * density, color.toArgb())
                canvas.drawPath(frame, caster)
            }
            caster.clearShadowLayer()
            canvas.restore()
        }
        canvas.save()
        canvas.clipPath(path)
        when {
            fx.highContrast || !fx.neuShadows -> canvas.drawPath(path, boundary)
            !sunk || emissive -> canvas.drawPath(path, rim)
        }
        if (focused && focusRing != null) {
            ring.color = focusRing
            canvas.drawPath(path, ring)
        }
        canvas.restore()
    }

    private val disabledFallback: FwNeuDrawable by lazy {
        FwNeuDrawable(c, fx, density, FwElevation.Raised1, cornerRadius, shadowRoom)
    }

    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/**
 * Soft emissive glow behind the brand sun (PIN): the disc red into brand pink into the page. It fills
 * the ellipse inscribed in its bounds, so behind the wide mark the light runs along the wings.
 */
class FwSunGlowDrawable(private val c: FwColors, private val lit: Boolean) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isDither = true }
    private val oval = RectF()

    override fun onBoundsChange(bounds: Rect) {
        oval.set(bounds)
        val radius = bounds.width() / 2f
        paint.shader = if (radius > 0f && bounds.height() > 0) RadialGradient(bounds.exactCenterX(), bounds.exactCenterY(), radius,
            intArrayOf(c.sunCore.copy(alpha = 0.50f).toArgb(), c.brand.copy(alpha = 0.18f).toArgb(), 0),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP).apply {
            setLocalMatrix(Matrix().apply { setScale(1f, bounds.height() / bounds.width().toFloat(), bounds.exactCenterX(), bounds.exactCenterY()) })
        } else null
    }

    override fun draw(canvas: Canvas) {
        if (lit) canvas.drawOval(oval, paint)
    }

    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/**
 * PIN dot: an inset well when empty; entered digits fill it with brand light that grows from the
 * key-light side (top-left) in 120 ms. [error] tints a wrong PIN briefly before the reset.
 */
class FwPinDotDrawable(private val c: FwColors, private val fx: FwEffects, private val density: Float) : Drawable() {
    private val well = FwNeuDrawable(c, fx, density, FwElevation.Inset)
    private val light = Paint(Paint.ANTI_ALIAS_FLAG)
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG)
    private var level01 = 0f
    private var animator: ValueAnimator? = null
    var error: Boolean = false
        set(value) {
            field = value
            updateShader()
            invalidateSelf()
        }

    fun setFilled(filled: Boolean, animate: Boolean) {
        val target = if (filled) 1f else 0f
        animator?.cancel()
        if (!animate || !filled) {
            level01 = target
            invalidateSelf()
            return
        }
        animator = ValueAnimator.ofFloat(level01, target).apply {
            duration = FwMotionDurations.PIN_DOT.toLong()
            addUpdateListener {
                level01 = it.animatedValue as Float
                invalidateSelf()
            }
            start()
        }
    }

    override fun onBoundsChange(bounds: Rect) {
        well.bounds = bounds
        updateShader()
    }

    private fun updateShader() {
        val r = min(bounds.width(), bounds.height()) / 2f
        if (r <= 0f) return
        val cx = bounds.exactCenterX()
        val cy = bounds.exactCenterY()
        val core = if (error) c.danger else c.textBrand
        val edge = if (error) c.danger else c.brand
        light.shader = RadialGradient(cx - r * 0.3f, cy - r * 0.3f, r * 1.4f,
            intArrayOf(core.toArgb(), edge.toArgb(), c.fillBottom.toArgb()), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        halo.shader = RadialGradient(cx, cy, r * 2f, intArrayOf(edge.copy(alpha = 0.55f).toArgb(), 0), null, Shader.TileMode.CLAMP)
    }

    override fun draw(canvas: Canvas) {
        well.draw(canvas)
        if (level01 <= 0f) return
        val r = min(bounds.width(), bounds.height()) / 2f
        val cx = bounds.exactCenterX()
        val cy = bounds.exactCenterY()
        if (fx.glow && level01 >= 1f) canvas.drawCircle(cx, cy, r * 2f, halo)
        // The fill grows from the point of the rim facing the light toward the centre.
        val k = level01
        val ox = cx + FwLight.KEY_X * r * (1f - k)
        val oy = cy + FwLight.KEY_Y * r * (1f - k)
        canvas.save()
        canvas.clipPath(Path().apply { addCircle(cx, cy, r, Path.Direction.CW) })
        canvas.drawCircle(ox, oy, max(0.5f, r * (0.35f + 0.65f * k) + r * (1f - k)), light)
        canvas.restore()
    }

    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/**
 * Bottom navigation bar: a raised floating pill with a light well that slides under the selected
 * tab (260 ms spring; it jumps under reduced motion or animator scale 0).
 */
class FwNavBarDrawable(
    private val c: FwColors,
    private val fx: FwEffects,
    private val density: Float,
    private val sideMargin: Float,
    private val verticalMargin: Float,
    corner: Float
) : Drawable() {
    private val bar = FwNeuDrawable(c, fx, density, FwElevation.Raised2, cornerRadius = corner)
    private val well = FwNeuDrawable(c, fx, density, FwElevation.Inset, cornerRadius = 16f * density)
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    private var wellCenter = Float.NaN
    private var wellWidth = 0f
    private var animator: ValueAnimator? = null
    var bottomInset: Int = 0
        set(value) {
            field = value
            onBoundsChange(bounds)
            invalidateSelf()
        }

    /** Moves the well under the tab centred at [center] (px, in this drawable's coordinates). */
    fun select(center: Float, width: Float, animate: Boolean) {
        wellWidth = width
        animator?.cancel()
        if (!animate || wellCenter.isNaN() || fx.motionPolicy().mode(FwMotionKind.TabWell) != FwMotionMode.Full) {
            wellCenter = center
            invalidateSelf()
            return
        }
        animator = ValueAnimator.ofFloat(wellCenter, center).apply {
            duration = FwMotionDurations.TAB_WELL.toLong()
            interpolator = OvershootInterpolator(1.1f)
            addUpdateListener {
                wellCenter = it.animatedValue as Float
                invalidateSelf()
            }
            start()
        }
    }

    override fun onBoundsChange(bounds: Rect) {
        bar.bounds = Rect((bounds.left + sideMargin).toInt(), (bounds.top + verticalMargin).toInt(),
            (bounds.right - sideMargin).toInt(), (bounds.bottom - bottomInset - verticalMargin).toInt())
    }

    override fun draw(canvas: Canvas) {
        bar.draw(canvas)
        if (wellCenter.isNaN() || wellWidth <= 0f) return
        val top = bar.bounds.top + 6f * density
        val bottom = bar.bounds.bottom - 6f * density
        if (bottom <= top) return
        well.bounds = Rect((wellCenter - wellWidth / 2f).toInt(), top.toInt(), (wellCenter + wellWidth / 2f).toInt(), bottom.toInt())
        well.draw(canvas)
        if (fx.glow) {
            dot.color = c.brand.toArgb()
            dot.setShadowLayer(4f * density, 0f, 0f, c.brand.toArgb())
        } else {
            dot.color = c.brand.toArgb()
            dot.clearShadowLayer()
        }
        canvas.drawCircle(wellCenter, bottom - 3.5f * density, 2.5f * density, dot)
    }

    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
