package jp.co.soramitsu.common.compose.theme.fw

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import jp.co.soramitsu.common.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlinx.coroutines.delay

/**
 * The solar geometry behind the crow (brand kit `tokens/solar-geometry.json`): rings, hexagram and
 * lattice as strokes, each with the window of the ignite progress over which it draws itself.
 * Units: outer ring radius 1, origin at the centre, y down.
 */
@Immutable
data class FwSolarGeometry(
    val coreWidth: Float,
    val glowWidth: Float,
    val core: Color,
    val glow: Color,
    val star: Color,
    val strokes: List<Stroke2>,
    val ticks: Ticks,
    val stars: List<Star>
) {
    sealed interface Stroke2 {
        val group: String
        val weight: Float
        val revealStart: Float
        val revealEnd: Float
    }

    data class Line(
        override val group: String,
        val x1: Float,
        val y1: Float,
        val x2: Float,
        val y2: Float,
        override val weight: Float,
        override val revealStart: Float,
        override val revealEnd: Float
    ) : Stroke2

    data class Circle(
        override val group: String,
        val cx: Float,
        val cy: Float,
        val r: Float,
        override val weight: Float,
        override val revealStart: Float,
        override val revealEnd: Float
    ) : Stroke2

    data class Ticks(val count: Int, val r: Float, val size: Float, val appearsAt: Float, val turnsPerSecond: Float)

    data class Star(val x: Float, val y: Float, val size: Float, val reveal: Float)

    companion object {
        /** Parses the kit file; throws on anything malformed (the figure is then not drawn). */
        fun parse(json: String): FwSolarGeometry {
            val root = JsonParser.parseString(json).asJsonObject
            require(root.get("formatVersion").asInt == 1) { "Unknown solar geometry format" }
            val stroke = root.getAsJsonObject("stroke")
            val colors = root.getAsJsonObject("colors")
            val strokes = root.getAsJsonArray("strokes").map { element ->
                val s = element.asJsonObject
                val reveal = s.getAsJsonArray("reveal")
                val start = reveal[0].asFloat
                val end = reveal[1].asFloat
                require(end > start && start >= 0f && end <= 1f) { "Stroke reveal out of order" }
                when (s.get("type").asString) {
                    "line" -> Line(s.get("group").asString, s.f("x1"), s.f("y1"), s.f("x2"), s.f("y2"), s.f("weight"), start, end)
                    "circle" -> Circle(s.get("group").asString, s.f("cx"), s.f("cy"), s.f("r"), s.f("weight"), start, end)
                    else -> error("Unknown stroke type")
                }
            }
            val ticks = root.getAsJsonObject("ticks").let {
                Ticks(it.get("count").asInt, it.f("r"), it.f("size"), it.f("appearsAt"), it.f("turnsPerSecond"))
            }
            val stars = root.getAsJsonArray("stars").map { element ->
                val s = element.asJsonObject
                Star(s.f("x"), s.f("y"), s.f("size"), s.f("reveal"))
            }
            return FwSolarGeometry(
                coreWidth = stroke.f("core"),
                glowWidth = stroke.f("glow"),
                core = parseHex(colors.get("core").asString),
                glow = parseHex(colors.get("glow").asString),
                star = parseHex(colors.get("star").asString),
                strokes = strokes,
                ticks = ticks,
                stars = stars
            )
        }

        private fun JsonObject.f(name: String): Float = get(name).asFloat

        /** `#RRGGBB` from the kit file. */
        internal fun parseHex(value: String): Color {
            require(Regex("#[0-9A-Fa-f]{6}").matches(value)) { "Colour is not #RRGGBB: $value" }
            val rgb = value.substring(1).toLong(16)
            return Color(
                red = ((rgb shr 16) and 0xFF) / 255f,
                green = ((rgb shr 8) and 0xFF) / 255f,
                blue = (rgb and 0xFF) / 255f
            )
        }

        @Volatile
        private var cached: FwSolarGeometry? = null

        /** The bundled figure, read once per process. */
        fun load(context: Context): FwSolarGeometry? = cached ?: runCatching {
            context.resources.openRawResource(R.raw.fw_solar_geometry).bufferedReader().use { parse(it.readText()) }
        }.getOrNull()?.also { cached = it }
    }
}

/** How far a stroke revealed over [start]..[end] has drawn at ignite [progress], 0..1. */
internal fun fwRevealFraction(progress: Float, start: Float, end: Float): Float =
    ((progress - start) / (end - start)).coerceIn(0f, 1f)

private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/**
 * Draws [geometry] centred on [center] with outer radius [radius]. [progress] is the ignite (1 =
 * drawn), [alpha] the whole figure's opacity, [tickTurn] the tick ring's rotation in turns, and
 * [groupAlpha] each group's opacity (rings, hexagram, lattice, ray).
 */
fun DrawScope.drawFwSolarGeometry(
    geometry: FwSolarGeometry,
    center: Offset,
    radius: Float,
    progress: Float = 1f,
    alpha: Float = 1f,
    tickTurn: Float = 0f,
    groupAlpha: (String) -> Float = { 1f }
) {
    if (alpha <= 0f || radius <= 0f) return
    val minCore = 1f
    for (stroke in geometry.strokes) {
        val k = fwRevealFraction(progress, stroke.revealStart, stroke.revealEnd)
        if (k <= 0f) continue
        val a = alpha * groupAlpha(stroke.group)
        if (a <= 0f) continue
        val glowWidth = geometry.glowWidth * radius * stroke.weight
        val coreWidth = max(minCore, geometry.coreWidth * radius * stroke.weight)
        when (stroke) {
            is FwSolarGeometry.Line -> {
                val start = Offset(center.x + stroke.x1 * radius, center.y + stroke.y1 * radius)
                val full = Offset(center.x + stroke.x2 * radius, center.y + stroke.y2 * radius)
                val end = start + (full - start) * k
                drawLine(geometry.glow.copy(alpha = 0.32f * a), start, end, glowWidth, StrokeCap.Round)
                drawLine(geometry.core.copy(alpha = a), start, end, coreWidth, StrokeCap.Round)
                if (k < 1f) drawTip(geometry, end, coreWidth * 2.6f, a)
            }
            is FwSolarGeometry.Circle -> {
                val r = stroke.r * radius
                val topLeft = Offset(center.x + stroke.cx * radius - r, center.y + stroke.cy * radius - r)
                val size = Size(r * 2f, r * 2f)
                // From 12 o'clock, anticlockwise.
                val sweep = -360f * k
                drawArc(geometry.glow.copy(alpha = 0.32f * a), -90f, sweep, false, topLeft, size, style = Stroke(glowWidth, cap = StrokeCap.Round))
                drawArc(geometry.core.copy(alpha = a), -90f, sweep, false, topLeft, size, style = Stroke(coreWidth, cap = StrokeCap.Round))
                if (k < 1f) {
                    val angle = (-PI / 2 - 2 * PI * k).toFloat()
                    val tip = Offset(center.x + stroke.cx * radius + cos(angle) * r, center.y + stroke.cy * radius + sin(angle) * r)
                    drawTip(geometry, tip, coreWidth * 2.6f, a)
                }
            }
        }
    }
    val ticks = geometry.ticks
    val tickAlpha = alpha * smoothstep(ticks.appearsAt, ticks.appearsAt + 0.14f, progress) * groupAlpha("ring")
    if (tickAlpha > 0f) {
        rotate(degrees = tickTurn * 360f, pivot = center) {
            val dot = max(0.75f, ticks.size * radius)
            for (i in 0 until ticks.count) {
                val angle = (i.toFloat() / ticks.count) * 2f * PI.toFloat()
                drawCircle(geometry.core.copy(alpha = 0.85f * tickAlpha), dot,
                    Offset(center.x + cos(angle) * ticks.r * radius, center.y + sin(angle) * ticks.r * radius))
            }
        }
    }
    for (star in geometry.stars) {
        val pop = smoothstep(star.reveal - 0.04f, star.reveal + 0.03f, progress)
        if (pop <= 0f) continue
        val r = star.size * radius * 0.5f * pop
        val at = Offset(center.x + star.x * radius, center.y + star.y * radius)
        drawCircle(Brush.radialGradient(listOf(geometry.star.copy(alpha = alpha), geometry.glow.copy(alpha = 0.45f * alpha), Color.Transparent), at, max(r, 1f)), max(r, 1f), at)
        val arm = r * 0.9f
        drawLine(geometry.star.copy(alpha = 0.8f * alpha), Offset(at.x - arm, at.y), Offset(at.x + arm, at.y), max(0.75f, r * 0.08f), StrokeCap.Round)
        drawLine(geometry.star.copy(alpha = 0.8f * alpha), Offset(at.x, at.y - arm), Offset(at.x, at.y + arm), max(0.75f, r * 0.08f), StrokeCap.Round)
    }
}

/** The bright point at the head of a stroke while it draws. */
private fun DrawScope.drawTip(geometry: FwSolarGeometry, at: Offset, radius: Float, alpha: Float) {
    drawCircle(Brush.radialGradient(listOf(geometry.star.copy(alpha = alpha), Color.Transparent), at, max(radius, 1f)), max(radius, 1f), at)
}

/** Ignite timing (brand kit SPEC part 2): draws in 0.9 s, then settles at a quarter of its strength. */
object FwSolarIgniteTiming {
    const val DRAW_MS = 900
    const val SETTLE_MS = 700
    const val SETTLED_ALPHA = 0.25f
}

/**
 * The solar ignite around a success mark: the figure draws itself, core over glow, the stars pop in
 * at their moments, then it settles to a quarter of its strength. Decorative, hidden from
 * accessibility. Under reduced motion it shows the finished figure at once. [key] restarts it.
 */
@Composable
fun FwSolarIgnite(modifier: Modifier = Modifier, key: Any? = Unit, radiusFraction: Float = 0.46f, startDelayMillis: Long = 0L) {
    val context = LocalContext.current
    val geometry = remember(context) { FwSolarGeometry.load(context) } ?: return
    val still = !fwMotion.moves(FwMotionKind.TxSuccess)
    val progress = remember(key) { Animatable(if (still) 1f else 0f) }
    val strength = remember(key) { Animatable(if (still) FwSolarIgniteTiming.SETTLED_ALPHA else 1f) }
    LaunchedEffect(key, still) {
        if (still) return@LaunchedEffect
        delay(startDelayMillis)
        progress.animateTo(1f, tween(FwSolarIgniteTiming.DRAW_MS, easing = LinearEasing))
        strength.animateTo(FwSolarIgniteTiming.SETTLED_ALPHA, tween(FwSolarIgniteTiming.SETTLE_MS))
    }
    Canvas(modifier.clearAndSetSemantics { }) {
        drawFwSolarGeometry(geometry, center, size.minDimension * radiusFraction, progress.value, strength.value)
    }
}
