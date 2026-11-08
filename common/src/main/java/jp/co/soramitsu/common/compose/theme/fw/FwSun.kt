package jp.co.soramitsu.common.compose.theme.fw

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R

enum class FwSyncPhase { Idle, Running, Done, Failed }

/** What the hero sun reports: whether the wallet is syncing (and how far), synced, or failing. */
@Immutable
data class FwSyncState(val phase: FwSyncPhase, val progress: Float? = null)

/** Bloom of the sun glow after a sync finishes: brightness and scale over t in 0..1 (0.35 is the peak). */
internal fun fwBloom(t: Float): Pair<Float, Float> {
    val x = t.coerceIn(0f, 1f)
    return if (x < BLOOM_PEAK) {
        val k = x / BLOOM_PEAK
        (BLOOM_START + (1f - BLOOM_START) * k) to (1f + BLOOM_SCALE * k)
    } else {
        val k = (x - BLOOM_PEAK) / (1f - BLOOM_PEAK)
        (1f - (1f - BLOOM_REST) * k) to (1f + BLOOM_SCALE * (1f - k))
    }
}

/** Sweep of the sync arc in degrees: the known progress, or a quarter turn while it is unknown. */
internal fun fwSyncSweep(progress: Float?): Float = progress?.let { (it.coerceIn(0f, 1f) * 360f).coerceAtLeast(MIN_SWEEP) } ?: UNKNOWN_SWEEP

private const val BLOOM_PEAK = 0.35f
private const val BLOOM_START = 0.6f
private const val BLOOM_REST = 0.85f
private const val BLOOM_SCALE = 0.08f
private const val MIN_SWEEP = 12f
private const val UNKNOWN_SWEEP = 90f

/**
 * The hero sun: the approved badge as an emissive light. While a sync runs a thin arc of light
 * sweeps around it (its length is the progress when known); a finished sync blooms the glow once;
 * a failure dims the sun to 60 % and shows a small warning dot. Nothing loops while idle.
 */
@Composable
fun FwSunBadge(
    sync: FwSyncState,
    modifier: Modifier = Modifier,
    badgeSize: Dp = 112.dp,
    @DrawableRes badge: Int = R.drawable.fw_brand_badge,
    contentDescription: String? = null
) {
    val fx = LocalFwEffects.current
    val c = LocalFwColors.current
    val motion = fwMotion
    val dim by animateFloatAsState(
        if (sync.phase == FwSyncPhase.Failed) FwLight.SUN_DIM else 1f,
        tween(motion.durationMillis(FwMotionKind.SyncFail)),
        label = "fwSunDim"
    )
    val bloom = remember { Animatable(1f) }
    var previous by remember { mutableStateOf(sync.phase) }
    LaunchedEffect(sync.phase, motion) {
        if (motion.mode(FwMotionKind.SyncBloom) != FwMotionMode.Full) bloom.snapTo(1f)
        if (previous == FwSyncPhase.Running && sync.phase == FwSyncPhase.Done && motion.mode(FwMotionKind.SyncBloom) == FwMotionMode.Full) {
            bloom.snapTo(0f)
            bloom.animateTo(1f, tween(FwMotionDurations.SYNC_BLOOM, easing = FastOutSlowInEasing))
        }
        previous = sync.phase
    }
    val rotation = if (sync.phase == FwSyncPhase.Running && motion.mode(FwMotionKind.SyncSweep) == FwMotionMode.Full) {
        rememberInfiniteTransition(label = "fwSync").animateFloat(
            0f, 360f, infiniteRepeatable(tween(FwMotionDurations.SYNC_TURN, easing = LinearEasing)), label = "fwSyncTurn"
        )
    } else remember { mutableFloatStateOf(0f) }
    val sweep by animateFloatAsState(fwSyncSweep(sync.progress),
        tween(if (motion.moves(FwMotionKind.SyncSweep)) FwMotionDurations.REDUCED_CROSSFADE else 0), label = "fwSyncSweep")
    val glow = fx.glow
    Box(
        modifier = modifier
            .size(badgeSize + 16.dp)
            .semantics { if (contentDescription != null) this.contentDescription = contentDescription }
            .drawBehind {
                val (brightness, scale) = fwBloom(bloom.value)
                val radius = badgeSize.toPx() / 2f
                val center = Offset(this.size.width / 2f, this.size.height / 2f)
                if (glow) {
                    val reach = (radius + 34.dp.toPx()) * scale
                    drawCircle(
                        Brush.radialGradient(
                            0f to c.sunCore.copy(alpha = 0.55f * brightness * dim),
                            0.45f to c.brand.copy(alpha = 0.20f * brightness * dim),
                            1f to Color.Transparent,
                            center = center, radius = reach
                        ),
                        reach, center
                    )
                }
                // Reduced effects and high contrast remove the badge's own shadow too.
                if (glow) drawCircle(
                    Brush.radialGradient(0f to c.brandEnd.copy(alpha = 0.55f * dim), 1f to Color.Transparent,
                        center = center + Offset(0f, 6.dp.toPx()), radius = radius * 1.12f),
                    radius * 1.12f, center + Offset(0f, 6.dp.toPx())
                )
            }
            .drawWithContent {
                drawContent()
                if (sync.phase == FwSyncPhase.Running) {
                    val stroke = 2.5.dp.toPx()
                    val ring = badgeSize.toPx() / 2f + 5.dp.toPx()
                    val center = Offset(this.size.width / 2f, this.size.height / 2f)
                    rotate(rotation.value - 90f, center) {
                        drawArc(
                            Brush.sweepGradient(0f to Color.Transparent, sweep / 360f to c.textOnAccent.copy(alpha = 0.95f), center = center),
                            startAngle = 0f, sweepAngle = sweep, useCenter = false,
                            topLeft = center - Offset(ring, ring), size = Size(ring * 2f, ring * 2f),
                            style = Stroke(stroke, cap = StrokeCap.Round)
                        )
                    }
                }
                if (sync.phase == FwSyncPhase.Failed) {
                    val dot = 5.dp.toPx()
                    val at = Offset(this.size.width / 2f + badgeSize.toPx() * 0.36f, this.size.height / 2f - badgeSize.toPx() * 0.36f)
                    drawCircle(c.bgBase, dot + 2.dp.toPx(), at)
                    drawCircle(c.warning, dot, at)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(badge),
            contentDescription = null,
            modifier = Modifier.size(badgeSize).graphicsLayer {
                alpha = dim
                val scale = fwBloom(bloom.value).second
                scaleX = scale
                scaleY = scale
            }
        )
    }
}

enum class FwTxStatus { Pending, Success, Failed }

/**
 * Status ring around a transaction glyph: amber and breathing while pending, filling with a drawn
 * check on success, shaking twice on failure. Only a change seen on screen animates; history rows
 * that are already final are drawn still.
 */
@Composable
fun FwTxStatusRing(status: FwTxStatus, modifier: Modifier = Modifier, size: Dp = 40.dp, content: @Composable () -> Unit = {}) {
    val c = LocalFwColors.current
    val motion = fwMotion
    var seen by remember { mutableStateOf(status) }
    val fill = remember { Animatable(if (status == FwTxStatus.Pending) 0f else 1f) }
    val shake = remember { Animatable(0f) }
    LaunchedEffect(status, motion) {
        if (!motion.moves(FwMotionKind.Shake)) shake.snapTo(0f)
        val changed = seen != status
        seen = status
        when {
            !changed -> fill.snapTo(if (status == FwTxStatus.Pending) 0f else 1f)
            status == FwTxStatus.Success && motion.moves(FwMotionKind.TxSuccess) -> {
                fill.snapTo(0f)
                fill.animateTo(1f, tween(motion.durationMillis(FwMotionKind.TxSuccess)))
            }
            status == FwTxStatus.Failed && motion.moves(FwMotionKind.Shake) -> {
                fill.snapTo(1f)
                shake.animateTo(0f, keyframes {
                    durationMillis = FwMotionDurations.SHAKE
                    -FwMotionDurations.SHAKE_DP at 37
                    FwMotionDurations.SHAKE_DP at 112
                    -FwMotionDurations.SHAKE_DP at 187
                    FwMotionDurations.SHAKE_DP at 262
                })
            }
            else -> fill.snapTo(1f)
        }
    }
    val breathe: State<Float> = if (status == FwTxStatus.Pending && motion.mode(FwMotionKind.TxBreathe) == FwMotionMode.Full) {
        rememberInfiniteTransition(label = "fwTxPending").animateFloat(
            0.5f, 1f, infiniteRepeatable(tween(FwMotionDurations.TX_BREATHE / 2), RepeatMode.Reverse), label = "fwTxBreathe"
        )
    } else remember { mutableFloatStateOf(1f) }
    val color = when (status) {
        FwTxStatus.Pending -> c.warning
        FwTxStatus.Success -> c.success
        FwTxStatus.Failed -> c.danger
    }
    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { translationX = shake.value.dp.toPx() }
            .drawWithContent {
                drawContent()
                val stroke = 2.dp.toPx()
                val inset = stroke / 2f
                val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
                when (status) {
                    FwTxStatus.Pending -> drawArc(color.copy(alpha = breathe.value), 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                    else -> {
                        drawArc(color.copy(alpha = 0.25f), 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                        drawArc(color, -90f, 360f * fill.value, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                    }
                }
                if (status == FwTxStatus.Success && fill.value > 0.5f) {
                    // The check draws during the second half of the fill, at the ring's lower right.
                    val r = this.size.minDimension * 0.17f
                    val at = Offset(this.size.width - r, this.size.height - r)
                    drawCircle(c.success, r, at)
                    val check = Path().apply {
                        moveTo(at.x - r * 0.45f, at.y)
                        lineTo(at.x - r * 0.1f, at.y + r * 0.35f)
                        lineTo(at.x + r * 0.5f, at.y - r * 0.35f)
                    }
                    val measure = PathMeasure().apply { setPath(check, false) }
                    val partial = Path()
                    measure.getSegment(0f, measure.length * ((fill.value - 0.5f) * 2f), partial, true)
                    drawPath(partial, c.bgBase, style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round))
                }
            },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
