package jp.co.soramitsu.common.compose.theme.fw

import androidx.compose.runtime.Immutable
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot

/**
 * One world, one light (sun-lit neumorphism, DESIGN-SPEC 2026-11-08 section 2).
 *
 * The key light shines from the top-left along [KEY_X], [KEY_Y]; neumorphic highlights fall toward
 * it and shadows away from it. The brand sun (the wallet hero's crest badge) is an emissive light
 * whose warm bounce reaches the top rims of nearby surfaces. Everything here is pure arithmetic so
 * the model can be tested without a renderer.
 */
object FwLight {
    /** Unit vector pointing toward the key light (top-left). */
    const val KEY_X = -0.6f
    const val KEY_Y = -0.8f

    /** Strongest sun rim, on a surface that touches the sun. */
    const val SUN_RIM_MAX = 0.35f

    /** Distance, in page widths, at which the sun rim has fallen to 1/e of its maximum. */
    const val SUN_RIM_REACH = 0.75f

    /** Rims weaker than this are not drawn at all. */
    const val SUN_RIM_FLOOR = 0.01f

    /** Default sun position as fractions of the window, where the hero badge sits on the wallet screen. */
    const val SUN_X = 0.5f
    const val SUN_Y = 0.21f

    /** Sun bounce fades out at this many page widths from the sun. */
    const val SUN_BOUNCE_REACH = 1.1f

    /** Ambient sky (bottom-right) fades out at this many page widths. */
    const val SKY_REACH = 1.3f

    /** Pressed controls shrink to this scale while they sink into an inset well. */
    const val PRESS_SCALE = 0.97f

    /** A failed sync or a dead node dims the sun to this opacity. */
    const val SUN_DIM = 0.6f
}

enum class FwElevation { Raised1, Raised2, Inset, Emissive }

/** One shadow in dp: offset along x/y and the blur radius. */
@Immutable
data class FwShadow(val dx: Float, val dy: Float, val blur: Float)

/** The highlight (toward the light) and the shadow (away from it) of one elevation level. */
@Immutable
data class FwShadowPair(val highlight: FwShadow, val shadow: FwShadow)

private const val RAISED1_SCALE = 0.6f

// Magnitudes from the spec table: hiShadow (-5, -5) blur 12, loShadow (6, 6) blur 16, laid along the key light.
private val HIGHLIGHT_DISTANCE = hypot(5f, 5f)
private val SHADOW_DISTANCE = hypot(6f, 6f)

/**
 * Shadow geometry for [level]. Raised levels cast outer shadows; Inset mirrors them inside the well
 * (dark toward the light, light away from it); Emissive casts no key-light shadow, only its own glow.
 */
fun fwShadowPair(level: FwElevation): FwShadowPair? {
    val raised2 = FwShadowPair(
        highlight = FwShadow(FwLight.KEY_X * HIGHLIGHT_DISTANCE, FwLight.KEY_Y * HIGHLIGHT_DISTANCE, 12f),
        shadow = FwShadow(-FwLight.KEY_X * SHADOW_DISTANCE, -FwLight.KEY_Y * SHADOW_DISTANCE, 16f)
    )
    return when (level) {
        FwElevation.Raised2 -> raised2
        FwElevation.Raised1 -> FwShadowPair(raised2.highlight.scaled(RAISED1_SCALE), raised2.shadow.scaled(RAISED1_SCALE))
        // Inner shadows: the dark one is cast by the rim nearest the light, so it sits at the top-left.
        FwElevation.Inset -> FwShadowPair(
            highlight = FwShadow(FwLight.KEY_X * 5f, FwLight.KEY_Y * 5f, 9f),
            shadow = FwShadow(-FwLight.KEY_X * 6f, -FwLight.KEY_Y * 6f, 10f)
        )
        FwElevation.Emissive -> null
    }
}

private fun FwShadow.scaled(factor: Float) = FwShadow(dx * factor, dy * factor, blur * factor)

/**
 * Sun-rim strength for a surface edge [distance] away from the sun on a page [pageWidth] wide (same
 * units). Gaussian falloff: 0.35 touching the sun, about 0.12 one card further down, none near the
 * bottom navigation.
 */
fun fwSunRimAlpha(distance: Float, pageWidth: Float): Float {
    if (!(pageWidth > 0f) || distance.isNaN()) return 0f
    val reach = abs(distance) / (FwLight.SUN_RIM_REACH * pageWidth)
    val alpha = FwLight.SUN_RIM_MAX * exp(-(reach * reach))
    return if (alpha < FwLight.SUN_RIM_FLOOR) 0f else alpha
}

/**
 * Distance from the sun at ([sunX], [sunY]) to the top edge of a surface spanning [left]..[right]
 * whose top is at [top]: the rim is lit from its nearest point.
 */
fun fwSunDistance(sunX: Float, sunY: Float, left: Float, top: Float, right: Float): Float {
    val dx = when {
        sunX < left -> left - sunX
        sunX > right -> sunX - right
        else -> 0f
    }
    return hypot(dx, top - sunY)
}

/** Rim alpha quantised to 1/64 steps so moving surfaces redraw only when the light visibly changes. */
fun fwQuantizedRim(alpha: Float): Float = (alpha.coerceIn(0f, 1f) * RIM_STEPS).toInt() / RIM_STEPS

private const val RIM_STEPS = 64f
