package jp.co.soramitsu.common.compose.theme.fw

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

/** Events that may animate (DESIGN-SPEC section 4). Each one reports something that really happened. */
enum class FwMotionKind(internal val fullMillis: Int, internal val loops: Boolean = false, internal val moves: Boolean = true) {
    Press(FwMotionDurations.PRESS_IN),
    BalanceRoll(FwMotionDurations.BALANCE_ROLL),
    PriceFlash(FwMotionDurations.PRICE_FLASH, moves = false),
    SyncSweep(FwMotionDurations.SYNC_TURN, loops = true),
    SyncBloom(FwMotionDurations.SYNC_BLOOM, moves = false),
    SyncFail(FwMotionDurations.SYNC_FAIL, moves = false),
    ListStagger(FwMotionDurations.LIST_RISE),
    TabWell(FwMotionDurations.TAB_WELL),
    SheetRise(FwMotionDurations.TAB_WELL),
    TxBreathe(FwMotionDurations.TX_BREATHE, loops = true, moves = false),
    TxSuccess(FwMotionDurations.TX_SUCCESS),
    Shake(FwMotionDurations.SHAKE),
    PinDot(FwMotionDurations.PIN_DOT),
    Copied(FwMotionDurations.COPIED, moves = false)
}

/** How an event is shown: animated as designed, as a short crossfade to the final state, or not at all. */
enum class FwMotionMode { Full, Crossfade, Still }

/** Durations from design/fw.tokens.json motion.event (FwMotionTokensTest keeps them equal). */
object FwMotionDurations {
    const val PRESS_IN = 90
    const val PRESS_OUT = 160
    const val BALANCE_ROLL = 450
    const val PRICE_FLASH = 600
    const val SYNC_TURN = 1600
    const val SYNC_BLOOM = 500
    const val SYNC_FAIL = 250
    const val LIST_STAGGER = 30
    const val LIST_RISE = 220
    const val TAB_WELL = 260
    const val TX_BREATHE = 1400
    const val TX_SUCCESS = 400
    const val SHAKE = 300
    const val PIN_DOT = 120
    const val COPIED = 900
    const val REDUCED_CROSSFADE = 150

    /** Rows that take part in the first-appearance stagger; later rows appear with the last one. */
    const val LIST_STAGGER_ROWS = 8

    /** Rows rise this far (dp) on first appearance. */
    const val LIST_RISE_DP = 8f

    /** Wrong PIN or failed transaction: two shakes of this amplitude (dp). */
    const val SHAKE_DP = 6f
}

/**
 * Motion policy for one surface. Reduced motion (animator duration scale 0), Calm surfaces and
 * Reduced effects replace movement with crossfades of at most 150 ms or nothing, and keep the
 * information (tints, final states). Secure surfaces (signing, recovery phrase) allow press
 * feedback only.
 */
@Immutable
data class FwMotionPolicy(
    val reduceMotion: Boolean,
    val calm: Boolean = false,
    val reducedEffects: Boolean = false,
    val secure: Boolean = false
) {
    private val restrained: Boolean get() = reduceMotion || calm || reducedEffects

    fun mode(kind: FwMotionKind): FwMotionMode = when {
        secure && kind != FwMotionKind.Press -> FwMotionMode.Still
        !restrained -> FwMotionMode.Full
        kind.loops -> FwMotionMode.Still
        kind == FwMotionKind.Shake || kind == FwMotionKind.ListStagger -> FwMotionMode.Still
        else -> FwMotionMode.Crossfade
    }

    /** Milliseconds to animate [kind]; 0 means jump to the final state. */
    fun durationMillis(kind: FwMotionKind): Int = when (mode(kind)) {
        FwMotionMode.Full -> kind.fullMillis
        FwMotionMode.Crossfade -> minOf(kind.fullMillis, FwMotionDurations.REDUCED_CROSSFADE)
        FwMotionMode.Still -> 0
    }

    /** Whether [kind] may move or scale anything on screen (colour changes are not movement). */
    fun moves(kind: FwMotionKind): Boolean = mode(kind) == FwMotionMode.Full && kind.moves

    /** Stagger delay for list row [index] on first appearance, or 0 when rows appear together. */
    fun staggerDelayMillis(index: Int): Int = if (mode(FwMotionKind.ListStagger) != FwMotionMode.Full || index <= 0) 0
    else minOf(index, FwMotionDurations.LIST_STAGGER_ROWS - 1) * FwMotionDurations.LIST_STAGGER
}

fun FwEffects.motionPolicy(secure: Boolean = false): FwMotionPolicy = FwMotionPolicy(
    reduceMotion = reduceMotion,
    calm = calm,
    reducedEffects = tier == FwTier.Reduced,
    secure = secure
)

/** Signing and recovery-phrase hosts provide true: only press feedback may animate inside them. */
val LocalFwSecureSurface = staticCompositionLocalOf { false }

val fwMotion: FwMotionPolicy
    @Composable
    @ReadOnlyComposable
    get() = LocalFwEffects.current.motionPolicy(LocalFwSecureSurface.current)
