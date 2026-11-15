package jp.co.soramitsu.common.compose.theme.fw

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import jp.co.soramitsu.common.feedback.SealFrame
import jp.co.soramitsu.common.feedback.TransferSeal
import jp.co.soramitsu.common.feedback.sealTimeline
import jp.co.soramitsu.common.shikiban.Shikiban
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Draws the seal (brand kit SPEC v4 part 3, the kit's `drawSeal`): the solar geometry drawn by a
 * transfer's stages. Both rings for `ready`, the upward triangle's three sides for `signed`, `sent` and
 * `accepted`, a star landing on each corner as its side closes, then the rest of the ignite by
 * [SealFrame.progress]. While waiting a cyan comet runs the next stroke over a faint dashed trace of it;
 * a broken seal turns ember, drops by up to 0.2 of the radius and fades.
 */
fun DrawScope.drawFwSeal(
    geometry: FwSolarGeometry,
    tokens: Shikiban,
    center: Offset,
    radius: Float,
    frame: SealFrame,
    seconds: Float,
    turn: Float
) {
    val r = radius
    val q = frame.q
    val rings = geometry.strokes.filter { it.group == "ring" }
    val sides = geometry.strokes.filter { it.group == "hexagram" }.take(SIDES)
    val broken = frame.broken
    val failure = Color(tokens.colors.failure)
    val strength = frame.strength
    fun amount(item: FwSolarGeometry.Stroke2): Float = when {
        item.group == "ring" -> q.coerceIn(0f, 1f)
        sides.indexOf(item) >= 0 -> (q - 1f - sides.indexOf(item)).coerceIn(0f, 1f)
        q >= SealFrame.STAGES -> fwRevealFraction(frame.progress, item.revealStart, item.revealEnd)
        else -> 0f
    }
    fun pointOn(item: FwSolarGeometry.Stroke2, k: Float): Offset = when (item) {
        is FwSolarGeometry.Line -> Offset(center.x + (item.x1 + (item.x2 - item.x1) * k) * r, center.y + (item.y1 + (item.y2 - item.y1) * k) * r)
        is FwSolarGeometry.Circle -> {
            val a = (-PI / 2 - k * 2 * PI).toFloat()
            Offset(center.x + (item.cx + cos(a) * item.r) * r, center.y + (item.cy + sin(a) * item.r) * r)
        }
    }
    val fall = broken * broken * r * BREAK_FALL
    for (pass in 0..1) {
        val glowPass = pass == 0
        val color = if (broken > 0f) failure else if (glowPass) geometry.glow else geometry.core
        val alpha = ((if (glowPass) 0.35f else 1f) * strength * (1f - broken * 0.9f)).coerceIn(0f, 1f)
        if (alpha <= 0f) continue
        geometry.strokes.forEachIndexed { index, item ->
            val k = amount(item)
            if (k <= 0f) return@forEachIndexed
            val drop = if (broken > 0f) fall * (0.4f + ((index * 37) % 10) / 10f) else 0f
            val width = max(0.6f, (if (glowPass) geometry.glowWidth * 0.5f else geometry.coreWidth * 1.6f) * item.weight * r)
            when (item) {
                is FwSolarGeometry.Line -> {
                    val start = Offset(center.x + item.x1 * r, center.y + item.y1 * r + drop)
                    val end = pointOn(item, k).let { Offset(it.x, it.y + drop) }
                    drawLine(color.copy(alpha = alpha), start, end, width, StrokeCap.Round, blendMode = BlendMode.Plus)
                }
                is FwSolarGeometry.Circle -> {
                    val cr = item.r * r
                    drawArc(color.copy(alpha = alpha), -90f, -360f * k, false,
                        Offset(center.x + item.cx * r - cr, center.y + item.cy * r - cr + drop), Size(cr * 2f, cr * 2f),
                        style = Stroke(width, cap = StrokeCap.Round), blendMode = BlendMode.Plus)
                }
            }
        }
    }
    if (broken > 0f) return
    // The heaven plate turns from the moment the transfer is ready.
    val plate = q.coerceIn(0f, 1f) * 0.9f * strength
    if (plate > 0f) {
        val ticks = geometry.ticks
        rotate(degrees = turn * 360f, pivot = center) {
            val dot = max(0.6f, ticks.size * r)
            for (i in 0 until ticks.count) {
                val a = (i.toFloat() / ticks.count) * 2f * PI.toFloat()
                drawCircle(geometry.core.copy(alpha = plate), dot, Offset(center.x + cos(a) * ticks.r * r, center.y + sin(a) * ticks.r * r))
            }
        }
    }
    // A star lands on each corner of the triangle as its side closes (`seal.stars`); the rest follow the ignite.
    val corner = tokens.seal.stars.entries.associate { (stage, star) -> star to tokens.seal.stages.indexOf(stage) + 1 }
    geometry.stars.forEachIndexed { index, star ->
        val landsAt = corner[index]
        val k = if (landsAt != null) ((q - landsAt + 0.25f) / 0.25f).coerceIn(0f, 1f)
        else if (q >= SealFrame.STAGES) fwRevealFraction(frame.progress, star.reveal - 0.04f, star.reveal + 0.03f) else 0f
        if (k <= 0f) return@forEachIndexed
        val size = max(1f, star.size * r * 0.5f * (0.6f + 0.4f * k))
        val at = Offset(center.x + star.x * r, center.y + star.y * r)
        drawCircle(Brush.radialGradient(listOf(geometry.star.copy(alpha = k * strength), geometry.core.copy(alpha = 0.6f * k * strength), Color.Transparent), at, size), size, at)
    }
    // In flight: a cyan comet runs the stroke the transfer is waiting for, over a faint dashed trace of it.
    if (frame.waiting && q < SealFrame.STAGES) {
        val settled = (q + 1e-3f).toInt()
        val pending = if (settled == 0) rings.firstOrNull() else sides.getOrNull(settled - 1)
        if (pending != null) {
            val flight = Color(tokens.colors.flight)
            val width = max(0.6f, geometry.coreWidth * pending.weight * r * 1.2f)
            val dash = PathEffect.dashPathEffect(floatArrayOf(r * 0.02f, r * 0.045f))
            when (pending) {
                is FwSolarGeometry.Line -> drawLine(flight.copy(alpha = 0.22f * strength), Offset(center.x + pending.x1 * r, center.y + pending.y1 * r),
                    Offset(center.x + pending.x2 * r, center.y + pending.y2 * r), width, pathEffect = dash)
                is FwSolarGeometry.Circle -> drawCircle(flight.copy(alpha = 0.22f * strength), pending.r * r,
                    Offset(center.x + pending.cx * r, center.y + pending.cy * r), style = Stroke(width, pathEffect = dash))
            }
            val t = (seconds / tokens.seal.cometSeconds).mod(1f)
            val head = 1f - (1f - t) * (1f - t) * (1f - t)
            for (i in 0 until COMET_DOTS) {
                val k = head - i * 0.022f
                if (k < 0f) break
                val at = pointOn(pending, k)
                val size = max(1f, r * 0.075f * (1f - i / 11f))
                drawCircle(Brush.radialGradient(listOf(if (i == 0) Color.White else flight, flight, Color.Transparent), at, size),
                    size, at, alpha = (1f - i / COMET_DOTS.toFloat()) * strength)
            }
        }
    }
}

private const val SIDES = 3
private const val COMET_DOTS = 9

/** A broken seal's strokes drop by up to 0.2 of the radius (SPEC v4 part 3, "Failure"). */
private const val BREAK_FALL = 0.2f

/**
 * The seal for [record], [size] across: advanced every frame while something is drawing, waiting or
 * breaking, then still. Reduced motion shows the strokes of the stages reached, without the comet.
 * Decorative: the screen's own words are its accessible text.
 */
@Composable
fun FwTransferSeal(record: TransferSeal.Record, modifier: Modifier = Modifier, size: Dp = 176.dp) {
    val context = LocalContext.current
    val geometry = remember(context) { FwSolarGeometry.load(context) } ?: return
    val tokens = FwShikiban.tokens() ?: return
    val timeline = remember(tokens) { tokens.sealTimeline() }
    val moves = fwShikibanMoves
    val now = remember { mutableLongStateOf(TransferSeal.nowMs()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(record, moves, lifecycle) {
        now.longValue = TransferSeal.nowMs()
        if (!moves) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                withFrameNanos { now.longValue = TransferSeal.nowMs() }
                val rest = timeline.restsAtMs(record)
                if (rest != null && now.longValue >= rest) break
            }
        }
    }
    Canvas(modifier.size(size).clearAndSetSemantics { }) {
        val t = now.longValue
        val frame = if (moves) timeline.frame(record, t) else timeline.frame(record, Long.MAX_VALUE / 4).let {
            // Still: the strokes of the stages reached; a failure shows as ember without falling.
            if (record.failedAtMs != null) it.copy(broken = 0f) else it.copy(waiting = false)
        }
        val seconds = ((t - (record.readyAtMs ?: t)) / 1000f)
        val turn = if (moves) seconds * SEAL_TURNS_PER_SECOND else 0f
        if (record.failedAtMs != null && !moves) {
            drawFwSeal(geometry, tokens, center, this.size.minDimension * SEAL_RADIUS, frame.copy(broken = STILL_BROKEN), 0f, 0f)
        } else {
            drawFwSeal(geometry, tokens, center, this.size.minDimension * SEAL_RADIUS, frame, seconds, turn)
        }
    }
}

/** The figure's outer radius in its box (the kit's reference drawing). */
private const val SEAL_RADIUS = 0.44f

/** The heaven plate's turn while a transfer is in flight (the kit's preview). */
private const val SEAL_TURNS_PER_SECOND = 0.12f / (2f * PI.toFloat())

/** Under reduced motion a failure is shown ember and faint, without falling. */
private const val STILL_BROKEN = 0.35f

/**
 * The seal over the screen on ink at 72 % (SPEC v4 part 3, "Where"), for a screen whose own progress is a
 * spinner in its button, and for the debug seam. Shown while [TransferSeal.Record.shownOver] is set.
 */
@Composable
fun FwTransferSealOverlay() {
    val record by TransferSeal.record.collectAsState()
    if (!record.shownOver || record.readyAtMs == null) return
    Box(
        Modifier
            .fillMaxSize()
            .background(LocalFwColors.current.brandInk.copy(alpha = OVERLAY_INK)),
        contentAlignment = Alignment.Center
    ) {
        FwTransferSeal(record)
    }
}

private const val OVERLAY_INK = 0.72f

/**
 * A failed transfer's seal breaking over the screen for `seal.breakMs` after the loader has gone (SPEC v4 part 3,
 * "Failure"). It takes no touches; the app's own error appears exactly as before. Nothing under reduced motion.
 */
@Composable
fun FwSealBreakOverlay(record: TransferSeal.Record) {
    val failedAt = record.failedAtMs ?: return
    val tokens = FwShikiban.tokens() ?: return
    if (!fwShikibanMoves) return
    val visible = remember(failedAt) { androidx.compose.runtime.mutableStateOf(TransferSeal.nowMs() - failedAt < tokens.seal.breakMs) }
    LaunchedEffect(failedAt) {
        val left = tokens.seal.breakMs - (TransferSeal.nowMs() - failedAt)
        if (left > 0) kotlinx.coroutines.delay(left)
        visible.value = false
    }
    if (!visible.value) return
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { FwTransferSeal(record) }
}
