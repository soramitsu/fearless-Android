package jp.co.soramitsu.common.compose.theme.fw

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.Canvas as ComposeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import jp.co.soramitsu.common.shikiban.Shikiban
import kotlin.math.max
import kotlin.math.sqrt
import kotlinx.coroutines.delay

/**
 * The shikiban instruments in Compose (brand kit SPEC v4 parts 2, 4 and 6): the sync dial, the mirror
 * loader and the hero's composition marks, ported from the kit's `tools/shikiban-draw.mjs`. Colours
 * come from the kit's `tokens/shikiban.json` (gold is final, cyan in flight, ember failure). Each
 * draws in a draw-only scope; anything that moves runs only while it is on screen, resumed and
 * changing, and stands still under reduced motion or Reduced effects.
 */
object FwShikiban {
    /** Tokens for composables; null when the kit file cannot be read (nothing of shikiban is then drawn). */
    @Composable
    fun tokens(): Shikiban? {
        val context = LocalContext.current
        return remember(context) { Shikiban.load(context) }
    }
}

/** Whether a shikiban instrument may move here: not under reduced motion, Calm or Reduced effects. */
internal val fwShikibanMoves: Boolean
    @Composable get() = fwMotion.mode(FwMotionKind.SyncSweep) == FwMotionMode.Full

private fun turnToDegrees(turn: Float) = -90f + turn * 360f

/** Seconds since this composable started, advanced every frame while [running] and the screen is resumed. */
@Composable
internal fun rememberFwSeconds(running: Boolean): State<Float> {
    val seconds = remember { mutableFloatStateOf(0f) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(running, lifecycle) {
        if (!running) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val base = seconds.floatValue
            val start = withFrameNanos { it }
            while (true) {
                withFrameNanos { now -> seconds.floatValue = base + (now - start) / 1e9f }
            }
        }
    }
    return seconds
}

// ---------------------------------------------------------------------------------------------------------------------
// The sync dial (SPEC v4 part 2)
// ---------------------------------------------------------------------------------------------------------------------

/**
 * The sync dial: eight segments clockwise from 12 o'clock, gold up to `done / total`, the last
 * `failed / total` ember, the rest gold at 20 %. While anything is outstanding a cyan comet circles it;
 * an app that knows only busy or idle shows the comet over an unlit ring. When everything has answered
 * or given up the comet stops, a gold dot fills the centre and one thin ring expands and fades.
 * Decorative: the words beside it carry the accessible text.
 */
@Composable
fun FwSyncDial(sync: FwSyncState, modifier: Modifier = Modifier, size: Dp = 18.dp) {
    val tokens = FwShikiban.tokens() ?: return
    val state = tokens.dialState(sync.done, sync.failed, sync.total)
    val busy = sync.phase == FwSyncPhase.Running || state.phase == Shikiban.DialPhase.Syncing
    val moves = fwShikibanMoves
    val seconds = rememberFwSeconds(running = busy && moves)
    val seal = remember { Animatable(1f) }
    var wasBusy by remember { mutableStateOf(busy) }
    LaunchedEffect(busy) {
        if (wasBusy && !busy && moves) {
            seal.snapTo(0f)
            seal.animateTo(1f, tween(tokens.dial.sealMs, easing = LinearEasing))
        }
        wasBusy = busy
    }
    Canvas(modifier.size(size).clearAndSetSemantics { }) {
        drawSyncDial(tokens, state, busy, comet = moves, seconds = if (moves) seconds.value else 0f, seal = seal.value)
    }
}

internal fun DrawScope.drawSyncDial(tokens: Shikiban, state: Shikiban.DialState, busy: Boolean, comet: Boolean, seconds: Float, seal: Float) {
    val r = this.size.minDimension * DIAL_RADIUS
    val width = max(1.5f, r * 0.2f)
    val c = center
    val box = Size(r * 2f, r * 2f)
    val topLeft = Offset(c.x - r, c.y - r)
    val rest = Color(tokens.colors.rest)
    val core = Color(tokens.colors.core)
    val failure = Color(tokens.colors.failure)
    val flight = Color(tokens.colors.flight)
    fun arc(from: Float, to: Float, color: Color, alpha: Float) {
        drawArc(color.copy(alpha = alpha), turnToDegrees(from), (to - from) * 360f, false, topLeft, box, style = Stroke(width, cap = StrokeCap.Butt))
    }
    for (s in 0 until tokens.dial.segments) {
        val whole = tokens.dialSegment(s, 0f, 1f) ?: continue
        arc(whole.first, whole.second, rest, 0.2f)
        tokens.dialSegment(s, 0f, state.lit)?.let { arc(it.first, it.second, if (busy) core else rest, 1f) }
        tokens.dialSegment(s, state.failedFrom, 1f)?.let { arc(it.first, it.second, failure, 1f) }
    }
    if (busy) {
        // In flight: a cyan comet circles the ring, its tail fading to nothing (none under reduced motion,
        // where the lit share and the count say it all).
        if (!comet) return
        val head = (seconds / tokens.dial.cometSeconds).mod(1f)
        for (i in 0 until COMET_STEPS) {
            val a = head - tokens.dial.cometTurn * i / COMET_STEPS
            val b = head - tokens.dial.cometTurn * (i + 1) / COMET_STEPS
            arc(b, a, flight, 0.9f * (1f - i.toFloat() / COMET_STEPS))
        }
    } else {
        // Settled: the wallet is live. Networks that gave up stay ember on the ring; the centre is never an alarm.
        drawCircle(rest, r * 0.36f, c)
    }
    if (seal > 0f && seal < 1f) {
        drawCircle(core.copy(alpha = (1f - seal) * 0.9f), r * (1f + seal * 1.1f), c,
            style = Stroke(max(1f, width * 0.35f * (1f - seal * 0.5f))), blendMode = BlendMode.Plus)
    }
}

/** The ring's radius as a share of the dial's size (the kit's reference drawing). */
private const val DIAL_RADIUS = 0.36f
private const val COMET_STEPS = 10

// ---------------------------------------------------------------------------------------------------------------------
// The heaven plate (SPEC v4 part 2): the hero's tick ring turns 14 times faster while syncing
// ---------------------------------------------------------------------------------------------------------------------

/** 0 at rest, 1 while syncing; eases back over 720 ms when the sync settles. Still under reduced motion. */
@Composable
fun rememberFwHeavenBoost(syncing: Boolean): State<Float> {
    val boost = remember { Animatable(0f) }
    val moves = fwShikibanMoves
    LaunchedEffect(syncing, moves) {
        if (!moves) {
            boost.snapTo(0f)
            return@LaunchedEffect
        }
        boost.animateTo(if (syncing) 1f else 0f, tween(if (syncing) HEAVEN_IN_MS else HEAVEN_OUT_MS))
    }
    return boost.asState()
}

/** The tick ring turns this many times faster while syncing. */
const val FW_HEAVEN_SYNC_RATE = 14f

/** The geometry brightens by half again while syncing. */
const val FW_HEAVEN_SYNC_BRIGHTEN = 0.5f
private const val HEAVEN_IN_MS = 360
private const val HEAVEN_OUT_MS = 720

// ---------------------------------------------------------------------------------------------------------------------
// The loader (SPEC v4 part 4)
// ---------------------------------------------------------------------------------------------------------------------

/**
 * Three comets (the crow's three legs) circling the mirror's rim, a third of a turn apart; the rim and
 * an inner circle rest under them. With a known [progress] the rim fills clockwise from 12 o'clock
 * instead. Reduced motion: the comets stand still and fade in turn, one every 720 ms.
 */
@Composable
fun FwMirrorLoader(modifier: Modifier = Modifier, progress: Float? = null) {
    val tokens = FwShikiban.tokens() ?: return
    val moves = fwShikibanMoves
    val seconds = rememberFwSeconds(running = moves && progress == null)
    val turnIndex = remember { mutableFloatStateOf(0f) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(moves, progress == null, lifecycle) {
        if (moves || progress != null) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(tokens.durationsMs[STILL_STEP_INDEX].toLong())
                turnIndex.floatValue = (turnIndex.floatValue + 1f).mod(tokens.loader.comets.toFloat())
            }
        }
    }
    Canvas(modifier.clearAndSetSemantics { }) {
        drawMirrorLoader(tokens, size.minDimension * LOADER_RADIUS, seconds.value, progress, if (moves) null else turnIndex.floatValue.toInt())
    }
}

/** The rim, its inner circle, and either the comets or the filled share. [stillComet] lights one standing comet. */
internal fun DrawScope.drawMirrorLoader(tokens: Shikiban, r: Float, seconds: Float, progress: Float?, stillComet: Int? = null) {
    val c = center
    val width = max(1.25f, r * 0.045f)
    val core = Color(tokens.colors.core)
    val glow = Color(tokens.colors.glow)
    val star = Color(tokens.colors.star)
    val rim = rimPath(tokens, c, r)
    glowStroke(rim, width, tokens.loader.restAlpha, core, glow, 3f)
    val inner = r * tokens.mirror.cusp * MIRROR_INNER / sqrt(2f)
    drawCircle(glow.copy(alpha = 0.3f * tokens.loader.restAlpha * 0.8f), inner, c, style = Stroke(width * 0.6f * 3f), blendMode = BlendMode.Plus)
    drawCircle(core.copy(alpha = tokens.loader.restAlpha * 0.8f), inner, c, style = Stroke(width * 0.6f), blendMode = BlendMode.Plus)
    if (progress != null) {
        glowStroke(rimSpan(tokens, c, r, 0f, max(0.001f, progress.coerceIn(0f, 1f)), 64), width * 1.4f, 1f, core, glow, 5f)
        return
    }
    val comets = tokens.loaderComets(if (stillComet != null) 0f else seconds)
    comets.forEachIndexed { index, (head, tail) ->
        val fade = if (stillComet == null) 1f else if (index == stillComet) 1f else 0.25f
        for (i in 0 until TAIL_STEPS) {
            val a = head - (head - tail) * i / TAIL_STEPS
            val b = head - (head - tail) * (i + 1) / TAIL_STEPS
            glowStroke(rimSpan(tokens, c, r, b, a, 6), width * 1.4f * (1f - i.toFloat() / TAIL_STEPS * 0.5f),
                (1f - i.toFloat() / TAIL_STEPS) * fade, if (i < 2) star else core, glow, 5f)
        }
    }
}

/** The rim's outer radius as a share of the loader's size (the kit's reference drawing). */
private const val LOADER_RADIUS = 0.36f
private const val TAIL_STEPS = 8
private const val MIRROR_INNER = 0.94f

/** 720 ms: the reduced-motion loader lights its comets in turn on this step of the ladder. */
private const val STILL_STEP_INDEX = 6

private fun rimPath(tokens: Shikiban, c: Offset, r: Float): Path = Path().apply {
    tokens.mirror.rim.forEachIndexed { index, arc ->
        val ar = arc.r * r
        val rect = Rect(c.x + arc.cx * r - ar, c.y + arc.cy * r - ar, c.x + arc.cx * r + ar, c.y + arc.cy * r + ar)
        arcTo(rect, Math.toDegrees(arc.from.toDouble()).toFloat(), Math.toDegrees((arc.to - arc.from).toDouble()).toFloat(), index == 0)
    }
    close()
}

private fun rimSpan(tokens: Shikiban, c: Offset, r: Float, from: Float, to: Float, steps: Int): Path = Path().apply {
    for (i in 0..steps) {
        val (x, y) = tokens.rimPoint(from + (to - from) * i / steps)
        if (i == 0) moveTo(c.x + x * r, c.y + y * r) else lineTo(c.x + x * r, c.y + y * r)
    }
}

/** A soft glow under a bright core, additive (the kit's `glowStroke`). */
private fun DrawScope.glowStroke(path: Path, width: Float, alpha: Float, core: Color, glow: Color, glowScale: Float) {
    if (alpha <= 0f) return
    drawPath(path, glow.copy(alpha = (0.3f * alpha).coerceIn(0f, 1f)), style = Stroke(width * glowScale, cap = StrokeCap.Round, join = StrokeJoin.Round), blendMode = BlendMode.Plus)
    drawPath(path, core.copy(alpha = alpha.coerceIn(0f, 1f)), style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round), blendMode = BlendMode.Plus)
}

// ---------------------------------------------------------------------------------------------------------------------
// The hero's composition marks (SPEC v4 part 6)
// ---------------------------------------------------------------------------------------------------------------------

/**
 * Registration marks, the asanoha lattice and its band of light on the balance card. The lattice is
 * drawn once into a bitmap for the card's size; afterwards only the band moves (a gradient mask over a
 * second, brighter copy), every 9 s, and only while the card is on screen and resumed. [textTop] is
 * where the card's text starts: the band stays above it so every text keeps its contrast. Reduced
 * motion stops the band; Reduced effects (and the Lite tier, SPEC v4) drop it.
 */
fun Modifier.fwCompositionMarks(fx: FwEffects, tokens: Shikiban?, textTop: () -> Float?): Modifier =
    if (tokens == null || fx.paintReduced) this else composed {
        val moves = fwShikibanMoves && fx.tier == FwTier.Full
        val seconds = rememberFwSeconds(running = moves)
        val density = LocalDensity.current
        drawWithCache {
            val cell = with(density) { ASANOHA_CELL.dp.toPx() }
            val lattice = asanohaBitmap(tokens, size, cell, Color(tokens.colors.rest), with(density) { 0.75.dp.toPx() })
            val gold = Color(tokens.colors.rest)
            val m = with(density) { 9.dp.toPx() }
            val arm = with(density) { 10.dp.toPx() }
            val tick = with(density) { 3.dp.toPx() }
            val hairline = max(1f, with(density) { 0.5.dp.toPx() })
            val band = with(density) { BAND_WIDTH.dp.toPx() }
            val fadeOut = with(density) { BAND_FADE_DP.dp.toPx() }
            val brackets = Path().apply {
                for ((x, y, dx, dy) in listOf(
                    listOf(m, m, 1f, 1f), listOf(size.width - m, m, -1f, 1f),
                    listOf(m, size.height - m, 1f, -1f), listOf(size.width - m, size.height - m, -1f, -1f)
                )) {
                    moveTo(x + dx * arm, y)
                    lineTo(x, y)
                    lineTo(x, y + dy * arm)
                }
            }
            val ticks = Path().apply {
                val cut = 1f / sqrt(2f)
                for (f in listOf(1f - cut, 0.5f, cut)) {
                    moveTo(size.width * f, m - tick)
                    lineTo(size.width * f, m + tick)
                    moveTo(size.width * f, size.height - m - tick)
                    lineTo(size.width * f, size.height - m + tick)
                }
            }
            onDrawBehind {
                drawImage(lattice, alpha = LATTICE_ALPHA)
                if (moves) {
                    val bottom = (textTop() ?: size.height).coerceIn(0f, size.height)
                    val travel = size.width + band
                    val x = (seconds.value / BAND_SECONDS).mod(1f) * travel - band / 2f
                    if (bottom > 0f) {
                        drawContext.canvas.saveLayer(Rect(0f, 0f, size.width, bottom), Paint())
                        drawImage(lattice, alpha = BAND_ALPHA)
                        drawRect(
                            Brush.horizontalGradient(
                                0f to Color.Transparent, 0.5f to Color.Black, 1f to Color.Transparent,
                                startX = x - band / 2f, endX = x + band / 2f
                            ),
                            blendMode = BlendMode.DstIn
                        )
                        // The band fades out above the text instead of ending on a hard line.
                        drawRect(
                            Brush.verticalGradient(0f to Color.Black, 1f to Color.Transparent,
                                startY = (bottom - fadeOut).coerceAtLeast(0f), endY = bottom),
                            blendMode = BlendMode.DstIn
                        )
                        drawContext.canvas.restore()
                    }
                }
                drawPath(brackets, gold.copy(alpha = BRACKET_ALPHA), style = Stroke(hairline))
                drawPath(ticks, gold.copy(alpha = TICK_ALPHA), style = Stroke(hairline))
            }
        }
    }

/** The asanoha lattice over [size], cell [cell] pixels, drawn once into a bitmap. */
private fun asanohaBitmap(tokens: Shikiban, size: Size, cell: Float, color: Color, width: Float): ImageBitmap {
    val w = max(1, size.width.toInt())
    val h = max(1, size.height.toInt())
    val bitmap = ImageBitmap(w, h)
    val path = Path()
    val tileH = tokens.asanoha.height * cell
    var ty = 0f
    while (ty < h) {
        var tx = 0f
        while (tx < w) {
            for (l in tokens.asanoha.lines) {
                path.moveTo(tx + l[0] * cell, ty + l[1] * cell)
                path.lineTo(tx + l[2] * cell, ty + l[3] * cell)
            }
            tx += cell * tokens.asanoha.width
        }
        ty += tileH
    }
    CanvasDrawScope().draw(androidx.compose.ui.unit.Density(1f), LayoutDirection.Ltr, ComposeCanvas(bitmap), Size(w.toFloat(), h.toFloat())) {
        drawPath(path, color, style = Stroke(width))
    }
    return bitmap
}

private const val ASANOHA_CELL = 26f
private const val LATTICE_ALPHA = 0.05f
private const val BAND_ALPHA = 0.32f
private const val BAND_WIDTH = 220f
private const val BAND_SECONDS = 9f
private const val BAND_FADE_DP = 48f
private const val BRACKET_ALPHA = 0.7f
private const val TICK_ALPHA = 0.45f

// ---------------------------------------------------------------------------------------------------------------------
// The value tick (SPEC v4 part 5)
// ---------------------------------------------------------------------------------------------------------------------

/** True inside the wallet home, where a value the user is looking at ticks when it changes. */
val LocalFwValueTicks = androidx.compose.runtime.staticCompositionLocalOf { false }

/**
 * The number a formatted amount shows ("1,234.56 DOT", "$0.0861901", "1.234,5"), or null. Only the
 * direction of a change is taken from it: the text itself is never altered.
 */
fun fwDisplayedNumber(text: String): java.math.BigDecimal? {
    val kept = text.filter { it.isDigit() || it == '.' || it == ',' }.trim('.', ',')
    if (kept.none { it.isDigit() }) return null
    val lastDot = kept.lastIndexOf('.')
    val lastComma = kept.lastIndexOf(',')
    val normal = when {
        lastDot >= 0 && lastComma >= 0 -> if (lastDot > lastComma) kept.replace(",", "") else kept.replace(".", "").replace(',', '.')
        lastComma >= 0 -> if (kept.count { it == ',' } == 1 && kept.length - lastComma - 1 != 3) kept.replace(',', '.') else kept.replace(",", "")
        kept.count { it == '.' } > 1 -> kept.replace(".", "")
        else -> kept
    }
    val negative = text.contains('-') || text.contains('−')
    return normal.toBigDecimalOrNull()?.let { if (negative) it.negate() else it }
}

/**
 * When [value] changes after its first display: the new text comes in 6 dp from below if it grew, from
 * above if it shrank, over 180 ms, and a one-pixel line under it in the price-up or price-down colour
 * sweeps left to right and fades over 510 ms. Never on the first value, never when [enabled] is false
 * (a hidden asset, outside the wallet home), at most once in 3 s, never under reduced motion. With
 * [lineOnly] the text stays put (the total keeps its own count-up). The text shown is always the new one.
 */
fun Modifier.fwValueTick(value: String?, enabled: Boolean = true, lineOnly: Boolean = false): Modifier = composed {
    val ticks = LocalFwValueTicks.current && enabled
    val moves = fwMotion.moves(FwMotionKind.BalanceRoll)
    val c = LocalFwColors.current
    val tokens = FwShikiban.tokens()
    val density = LocalDensity.current
    val shift = remember { Animatable(0f) }
    val line = remember { Animatable(1f) }
    val up = remember { mutableStateOf(true) }
    val previous = remember { mutableStateOf<String?>(null) }
    val lastTickAt = remember { androidx.compose.runtime.mutableLongStateOf(Long.MIN_VALUE / 2) }
    LaunchedEffect(value) {
        val before = previous.value
        previous.value = value
        if (!ticks || !moves || tokens == null || before == null || value == null || before == value) return@LaunchedEffect
        val now = System.nanoTime() / NANOS_PER_MS
        if (now - lastTickAt.longValue < TICK_GAP_MS) return@LaunchedEffect
        val a = fwDisplayedNumber(before) ?: return@LaunchedEffect
        val b = fwDisplayedNumber(value) ?: return@LaunchedEffect
        if (a.compareTo(b) == 0) return@LaunchedEffect
        lastTickAt.longValue = now
        up.value = b > a
        val rise = with(density) { TICK_RISE_DP.dp.toPx() }
        kotlinx.coroutines.coroutineScope {
            launch {
                line.snapTo(0f)
                line.animateTo(1f, tween(tokens.durationsMs[LINE_STEP], easing = LinearEasing))
            }
            if (!lineOnly) {
                shift.snapTo(if (up.value) rise else -rise)
                shift.animateTo(0f, tween(tokens.durationsMs[RISE_STEP], easing = androidx.compose.animation.core.CubicBezierEasing(
                    tokens.settle.x1, tokens.settle.y1, tokens.settle.x2, tokens.settle.y2)))
            }
        }
    }
    this
        .graphicsLayer { translationY = shift.value }
        .drawWithContent {
            drawContent()
            val k = line.value
            if (k >= 1f) return@drawWithContent
            val colour = if (up.value) c.priceUp else c.priceDown
            val y = size.height - 0.5f
            val width = 1f.coerceAtLeast(with(density) { 1.dp.toPx() } * 0.5f)
            // The line sweeps in over the first half of its time and fades over the rest.
            val reach = (k * 2f).coerceAtMost(1f) * size.width
            val fade = if (k < 0.5f) 1f else 1f - (k - 0.5f) * 2f
            drawLine(colour.copy(alpha = fade), Offset(0f, y), Offset(reach, y), width)
        }
}

private const val NANOS_PER_MS = 1_000_000L
private const val TICK_GAP_MS = 3_000L
private const val TICK_RISE_DP = 6f

/** 180 ms (the ladder's third step) for the text, 510 ms (sixth) for the line. */
private const val RISE_STEP = 2
private const val LINE_STEP = 5
