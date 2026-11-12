package jp.co.soramitsu.common.compose.theme.fw

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlinx.coroutines.delay

/**
 * The sky behind the wallet's balance (brand kit SPEC part 3, "Sky card"): the kit's sunset sky,
 * placed so its sun sits behind the crest badge, under an ink scrim that leaves the sky bright
 * around the badge and reaches [FwSkyScrim.TEXT] where the text starts, so every text on the card
 * keeps at least 4.5:1 (FwSkyScrimContrastTest measures the worst pixel of the sky).
 */
object FwSkyScrim {
    /** The sun's centre in the kit's `png/sky/sky-card.webp`, as fractions of the image (measured). */
    const val SUN_X = 0.517f
    const val SUN_Y = 0.385f
    const val TOP = 0.30f
    const val AT_BADGE = 0.48f
    const val TEXT = 0.90f
    const val BOTTOM = 0.94f

    /** The largest zoom used to bring the sun to the badge; beyond it the sun sits as near as it can. */
    const val MAX_ZOOM = 2.2f
}

/**
 * Where the sky image goes so that it covers the view and its sun ([sunX], [sunY] fractions of the
 * image) lands on [anchorX], [anchorY] as far as covering allows. Returns left, top, width, height.
 */
fun fwSkyPlacement(
    viewWidth: Float,
    viewHeight: Float,
    imageWidth: Float,
    imageHeight: Float,
    anchorX: Float,
    anchorY: Float,
    sunX: Float = FwSkyScrim.SUN_X,
    sunY: Float = FwSkyScrim.SUN_Y
): FloatArray {
    if (minOf(viewWidth, viewHeight, imageWidth, imageHeight) <= 0f) return floatArrayOf(0f, 0f, viewWidth, viewHeight)
    val cover = max(viewWidth / imageWidth, viewHeight / imageHeight)
    val needed = maxOf(
        cover,
        anchorX / (sunX * imageWidth),
        (viewWidth - anchorX) / ((1f - sunX) * imageWidth),
        anchorY / (sunY * imageHeight),
        (viewHeight - anchorY) / ((1f - sunY) * imageHeight)
    )
    val scale = needed.coerceAtMost(cover * FwSkyScrim.MAX_ZOOM)
    val width = imageWidth * scale
    val height = imageHeight * scale
    val left = (anchorX - sunX * width).coerceIn(viewWidth - width, 0f)
    val top = (anchorY - sunY * height).coerceIn(viewHeight - height, 0f)
    return floatArrayOf(left, top, width, height)
}

/**
 * The scrim's stops (position as a fraction of the height, ink alpha): light above the badge, at
 * least [FwSkyScrim.TEXT] from [textTop] down. Positions only ever increase.
 */
fun fwSkyScrimStops(height: Float, badgeY: Float, textTop: Float): List<Pair<Float, Float>> {
    if (height <= 0f) return listOf(0f to FwSkyScrim.TEXT, 1f to FwSkyScrim.BOTTOM)
    val badge = (badgeY / height).coerceIn(0f, 1f)
    val text = (textTop / height).coerceIn(badge, 1f)
    return listOf(0f to FwSkyScrim.TOP, badge to FwSkyScrim.AT_BADGE, text to FwSkyScrim.TEXT, 1f to FwSkyScrim.BOTTOM)
        .fold(mutableListOf<Pair<Float, Float>>()) { list, stop ->
            if (list.isEmpty() || stop.first > list.last().first) list += stop else list[list.lastIndex] = stop.first to max(list.last().second, stop.second)
            list
        }
}

/**
 * Draws the sky card behind a hero: [sky] placed on the badge, the scrim, the solar geometry's
 * rings and ticks turning slowly round the badge with the lattice faint behind it, and the gold
 * arrival [pulse] (0 = none, rising to 1 and back). [badge] is the badge's centre and radius in
 * this layout, [textTop] where text begins. Nothing is drawn with reduced effects or high contrast.
 */
fun Modifier.fwSkyHero(
    fx: FwEffects,
    c: FwColors,
    sky: ImageBitmap?,
    geometry: FwSolarGeometry?,
    badge: () -> Pair<Offset, Float>?,
    textTop: () -> Float?,
    tickTurn: State<Float>,
    pulse: State<Float>
): Modifier = if (fx.paintReduced) this else drawBehind {
    val (center, badgeRadius) = badge() ?: return@drawBehind
    val textStart = textTop() ?: (center.y + badgeRadius)
    if (sky != null) {
        val place = fwSkyPlacement(size.width, size.height, sky.width.toFloat(), sky.height.toFloat(), center.x, center.y)
        drawImage(
            sky,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(sky.width, sky.height),
            dstOffset = IntOffset(place[0].toInt(), place[1].toInt()),
            dstSize = IntSize(place[2].toInt(), place[3].toInt())
        )
        val stops = fwSkyScrimStops(size.height, center.y, textStart)
        drawRect(Brush.verticalGradient(*stops.map { (at, alpha) -> at to c.brandInk.copy(alpha = alpha) }.toTypedArray()))
    }
    if (geometry != null) {
        val radius = badgeRadius * GEOMETRY_TO_BADGE
        drawFwSolarGeometry(geometry, center, radius, progress = 1f, alpha = 0.9f, tickTurn = tickTurn.value) { group ->
            when (group) {
                "ring" -> 0.85f
                "hexagram" -> 0.32f
                else -> 0.18f
            }
        }
    }
    val p = pulse.value
    if (p > 0f) {
        val reach = badgeRadius * (1.2f + 1.6f * p)
        drawCircle(Brush.radialGradient(listOf(c.brandGold.copy(alpha = 0.55f * (1f - p * 0.6f)), c.brandGold.copy(alpha = 0f)), center, reach), reach, center)
    }
}

/** The solar figure's outer ring around the badge, as a multiple of the badge's radius. */
const val GEOMETRY_TO_BADGE = 1.44f

/**
 * The tick ring's slow turn (kit `ticks.turnsPerSecond`), stepped ten times a second so the page
 * is not redrawn every frame for a movement of a degree a second. Still under reduced motion and
 * while the screen is not resumed.
 */
@Composable
fun rememberFwTickTurn(geometry: FwSolarGeometry?): State<Float> {
    val turn = remember { mutableFloatStateOf(0f) }
    val moving = fwMotion.mode(FwMotionKind.SyncSweep) == FwMotionMode.Full
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(geometry, moving, lifecycle) {
        val rate = geometry?.ticks?.turnsPerSecond ?: return@LaunchedEffect
        if (!moving) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val start = System.nanoTime()
            val base = turn.floatValue
            while (true) {
                turn.floatValue = base + rate * ((System.nanoTime() - start) / 1e9f)
                delay(TICK_STEP_MS)
            }
        }
    }
    return turn
}

private const val TICK_STEP_MS = 100L

/** The gold arrival pulse: rises and fades once each time [trigger] changes (not for its first value). */
@Composable
fun rememberFwArrivalPulse(trigger: Int): State<Float> {
    val pulse = remember { Animatable(0f) }
    val still = fwMotion.mode(FwMotionKind.SyncBloom) == FwMotionMode.Still
    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        if (still) {
            // Information outlives motion: the card still flashes gold, without growing.
            pulse.snapTo(0.35f)
            delay(FwMotionDurations.COPIED.toLong())
            pulse.snapTo(0f)
            return@LaunchedEffect
        }
        pulse.snapTo(0f)
        pulse.animateTo(1f, tween(ARRIVAL_PULSE_MS, easing = FastOutSlowInEasing))
        pulse.snapTo(0f)
    }
    return pulse.asState()
}

private const val ARRIVAL_PULSE_MS = 900

/**
 * Lit rim (brand kit SPEC part 3): a one-pixel edge, gold `brandGold` at 55 % on the upper left,
 * through pink `sunRim` at 30 %, to nothing on the lower right. High contrast keeps the surface's
 * own boundary instead. [strength] scales both (the arrival pulse lifts it).
 */
fun Modifier.fwLitRim(fx: FwEffects, c: FwColors, shape: Shape, width: Dp = 1.dp, strength: () -> Float = { 1f }): Modifier =
    if (fx.highContrast) this else drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val path = Path().apply { addOutline(outline) }
        val stroke = Stroke(width.toPx() * 2f)
        onDrawWithContent {
            drawContent()
            val k = strength().coerceIn(0f, 2f)
            val brush = Brush.linearGradient(
                0f to c.brandGold.copy(alpha = (FwLitRimTokens.GOLD_ALPHA * k).coerceAtMost(1f)),
                0.5f to c.sunRim.copy(alpha = (FwLitRimTokens.PINK_ALPHA * k).coerceAtMost(1f)),
                1f to Color.Transparent,
                start = Offset.Zero,
                end = Offset(size.width, size.height)
            )
            clipPath(path) { drawOutline(outline, brush, style = stroke) }
        }
    }

object FwLitRimTokens {
    const val GOLD_ALPHA = 0.55f
    const val PINK_ALPHA = 0.30f
}

/**
 * Aurora behind the wallet home (brand kit SPEC part 3): two very soft glows, pink and warm gold,
 * each under 18 % alpha, drifting through one cycle a minute. Stepped ten times a second; still
 * under reduced motion; absent with reduced effects.
 */
fun Modifier.fwAurora(fx: FwEffects, c: FwColors): Modifier = if (!fx.aurora) this else composed {
    val phase = remember { mutableFloatStateOf(0f) }
    val moving = fwMotion.mode(FwMotionKind.SyncSweep) == FwMotionMode.Full
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(moving, lifecycle) {
        if (!moving) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val start = System.nanoTime()
            val base = phase.floatValue
            while (true) {
                phase.floatValue = base + ((System.nanoTime() - start) / 1e9f) / AURORA_CYCLE_S
                delay(TICK_STEP_MS)
            }
        }
    }
    drawBehind {
        val t = phase.floatValue * 2f * PI.toFloat()
        val reach = size.width * 0.9f
        val pink = Offset(size.width * (0.22f + 0.12f * sin(t)), size.height * (0.18f + 0.06f * cos(t * 2f)))
        val gold = Offset(size.width * (0.80f + 0.10f * cos(t)), size.height * (0.42f + 0.08f * sin(t * 2f + 1f)))
        drawCircle(Brush.radialGradient(listOf(c.brand.copy(alpha = AURORA_PINK), c.brand.copy(alpha = 0f)), pink, reach), reach, pink)
        drawCircle(Brush.radialGradient(listOf(c.brandGold.copy(alpha = AURORA_GOLD), c.brandGold.copy(alpha = 0f)), gold, reach * 0.8f), reach * 0.8f, gold)
    }
}

private const val AURORA_CYCLE_S = 60f
internal const val AURORA_PINK = 0.16f
internal const val AURORA_GOLD = 0.10f

/**
 * A slow sheen across the primary action: a soft light band crosses it once every 6 s. Absent
 * with reduced effects and still under reduced motion.
 */
fun Modifier.fwSheen(fx: FwEffects, shape: Shape, enabled: Boolean = true): Modifier = if (!enabled || !fx.glow) this else composed {
    // Signing and recovery-phrase hosts allow press feedback only.
    if (LocalFwSecureSurface.current) return@composed this
    val band = remember { Animatable(-1f) }
    val moving = fwMotion.moves(FwMotionKind.Press)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(moving, lifecycle) {
        if (!moving) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(SHEEN_EVERY_MS)
                band.snapTo(0f)
                band.animateTo(1f, tween(SHEEN_MS))
                band.snapTo(-1f)
            }
        }
    }
    drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val path = Path().apply { addOutline(outline) }
        onDrawWithContent {
            drawContent()
            val k = band.value
            if (k < 0f) return@onDrawWithContent
            val x = -size.width + k * size.width * 3f
            clipPath(path) {
                drawRect(
                    Brush.linearGradient(
                        0f to Color.White.copy(alpha = 0f),
                        0.5f to Color.White.copy(alpha = SHEEN_ALPHA),
                        1f to Color.White.copy(alpha = 0f),
                        start = Offset(x, 0f),
                        end = Offset(x + size.width * 0.8f, size.height)
                    )
                )
            }
        }
    }
}

private const val SHEEN_EVERY_MS = 6_000L
private const val SHEEN_MS = 700
private const val SHEEN_ALPHA = 0.22f
