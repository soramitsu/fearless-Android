package jp.co.soramitsu.common.compose.theme.fw

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val RollEasing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)

/**
 * An amount that reports its own changes: changed digits roll to the new value (450 ms) and the
 * changed part is tinted up/down, then fades back ([roll] = false keeps only the 600 ms tint flash
 * used on list rows). Accessibility reads the whole value; tests still find it by its text.
 */
@Composable
fun FwRollingText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = LocalFwColors.current.textPrimary,
    roll: Boolean = true
) {
    val c = LocalFwColors.current
    val motion = fwMotion
    var shown by remember { mutableStateOf(text) }
    var plan by remember { mutableStateOf(fwDigitRollPlan(null, text)) }
    val progress = remember { Animatable(1f) }
    val tint = remember { Animatable(0f) }
    LaunchedEffect(text, motion) {
        val kind = if (roll) FwMotionKind.BalanceRoll else FwMotionKind.PriceFlash
        if (motion.mode(kind) == FwMotionMode.Still) {
            shown = text
            progress.snapTo(1f)
            tint.snapTo(0f)
            return@LaunchedEffect
        }
        if (!motion.moves(kind)) progress.snapTo(1f)
        if (text == shown) {
            progress.snapTo(1f)
            tint.snapTo(0f)
            return@LaunchedEffect
        }
        val next = fwDigitRollPlan(shown, text)
        plan = next
        shown = text
        coroutineScope {
            launch {
                if (roll && motion.mode(kind) == FwMotionMode.Full) {
                    progress.snapTo(0f)
                    progress.animateTo(1f, tween(FwMotionDurations.BALANCE_ROLL, easing = RollEasing))
                } else {
                    progress.snapTo(1f)
                }
            }
            launch {
                if (next.direction != 0) {
                    tint.snapTo(1f)
                    // Information outlives motion: the tint stays visible, only its fade gets shorter.
                    val fade = if (motion.mode(kind) == FwMotionMode.Full) FwMotionDurations.PRICE_FLASH else FwMotionDurations.REDUCED_CROSSFADE
                    delay(if (roll) FwMotionDurations.BALANCE_ROLL.toLong() else 0L)
                    tint.animateTo(0f, tween(fade))
                }
            }
        }
    }
    val accent = when (plan.direction) {
        1 -> c.priceUp
        -1 -> c.priceDown
        else -> color
    }
    val semantics = Modifier.clearAndSetSemantics { this.text = AnnotatedString(text) }
    if (progress.value >= 1f && tint.value <= 0f) {
        Text(text = text, style = style, color = color, modifier = modifier.then(semantics))
        return
    }
    Row(modifier.then(semantics), verticalAlignment = Alignment.CenterVertically) {
        plan.columns.forEachIndexed { index, column ->
            val columnColor = if (index >= plan.changedFrom) lerp(color, accent, tint.value) else color
            when {
                column.rolls && progress.value < 1f -> RollingDigit(column, plan.direction, progress.value, style, columnColor)
                column.changed && progress.value < 1f -> Box {
                    column.previous?.let { Text(it.toString(), style = style, color = columnColor, modifier = Modifier.graphicsLayer { alpha = 1f - progress.value }) }
                    Text(column.char.toString(), style = style, color = columnColor, modifier = Modifier.graphicsLayer { alpha = progress.value })
                }
                else -> Text(column.char.toString(), style = style, color = columnColor)
            }
        }
    }
}

@Composable
private fun RollingDigit(column: FwRollColumn, direction: Int, progress: Float, style: TextStyle, color: Color) {
    // Up: the new digit rises from below; down: it drops from above.
    val sign = if (direction < 0) -1f else 1f
    Box(Modifier.clipToBounds()) {
        Text(column.previous.toString(), style = style, color = color,
            modifier = Modifier.graphicsLayer { translationY = -sign * size.height * progress })
        Text(column.char.toString(), style = style, color = color,
            modifier = Modifier.graphicsLayer { translationY = sign * size.height * (1f - progress) })
    }
}

/** Remembers when a list first appeared, so only its first rows rise in, and only once. */
@Stable
class FwStagger internal constructor(private val startedAt: Long, internal val policy: FwMotionPolicy) {
    /** Delay for row [index], or null when the row appears without motion (late rows, scrolling, reduced motion). */
    fun delayFor(index: Int, now: Long = SystemClock.uptimeMillis()): Int? = when {
        policy.mode(FwMotionKind.ListStagger) != FwMotionMode.Full -> null
        now - startedAt > FIRST_APPEARANCE_WINDOW_MS -> null
        index < 0 || index >= FwMotionDurations.LIST_STAGGER_ROWS -> null
        else -> policy.staggerDelayMillis(index)
    }

    companion object {
        /** Rows composed within this time of the list's first frame belong to its first appearance. */
        const val FIRST_APPEARANCE_WINDOW_MS = 400L
    }
}

@Composable
fun rememberFwStagger(): FwStagger {
    val policy = fwMotion
    val startedAt = remember { SystemClock.uptimeMillis() }
    return remember(policy) { FwStagger(startedAt, policy) }
}

/** Rows fade in and rise 8 dp, 30 ms apart, on the list's first appearance only. */
fun Modifier.fwStaggerIn(stagger: FwStagger, index: Int): Modifier = composed {
    val delayMillis = remember { stagger.delayFor(index) }
    if (delayMillis == null) return@composed this
    val appear = remember { Animatable(0f) }
    LaunchedEffect(stagger.policy) {
        if (stagger.policy.moves(FwMotionKind.ListStagger)) {
            delay(delayMillis.toLong())
            appear.animateTo(1f, tween(FwMotionDurations.LIST_RISE))
        } else {
            appear.snapTo(1f)
        }
    }
    graphicsLayer {
        alpha = appear.value
        translationY = (1f - appear.value) * FwMotionDurations.LIST_RISE_DP.dp.toPx()
    }
}
