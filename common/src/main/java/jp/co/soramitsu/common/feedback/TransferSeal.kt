package jp.co.soramitsu.common.feedback

import jp.co.soramitsu.common.shikiban.CubicBezier
import jp.co.soramitsu.common.shikiban.Shikiban
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The seal (brand kit SPEC v4 part 3): a transfer's stages as they really happen, kept where both the
 * send screen and the success screen can read them, so the figure drawn on one continues on the other.
 * It holds stage times only: no amount, address, key or hash. The send flow reports what it knows; a
 * stage the app cannot tell apart from the next is lit together with it when that event arrives.
 */
object TransferSeal {
    enum class Stage { Idle, Ready, Signed, Sent, Accepted, Failed }

    /**
     * When each stage was reached ([Stage.Ready] … [Stage.Accepted]), in [nowMs] milliseconds, and when the
     * transfer failed. [shownOver] is true while the seal should be drawn over the screen on ink (the debug
     * seam, or a screen whose own progress is a spinner in its button).
     */
    data class Record(
        val readyAtMs: Long? = null,
        val signedAtMs: Long? = null,
        val sentAtMs: Long? = null,
        val acceptedAtMs: Long? = null,
        val failedAtMs: Long? = null,
        val shownOver: Boolean = false
    ) {
        val stage: Stage
            get() = when {
                failedAtMs != null -> Stage.Failed
                acceptedAtMs != null -> Stage.Accepted
                sentAtMs != null -> Stage.Sent
                signedAtMs != null -> Stage.Signed
                readyAtMs != null -> Stage.Ready
                else -> Stage.Idle
            }

        /** Times of the four drawn stages in order (null where not reached). */
        val times: List<Long?> get() = listOf(readyAtMs, signedAtMs, sentAtMs, acceptedAtMs)
    }

    /** The seal's clock: monotonic milliseconds (`System.nanoTime`), the same on every screen and in JVM tests. */
    fun nowMs(): Long = System.nanoTime() / NANOS_PER_MS

    private const val NANOS_PER_MS = 1_000_000L

    private val mutable = MutableStateFlow(Record())
    val record: StateFlow<Record> = mutable

    /** The final control was pressed: the app is preparing to sign. */
    fun ready(nowMs: Long, shownOver: Boolean = false) {
        mutable.value = Record(readyAtMs = nowMs, shownOver = shownOver)
    }

    /** The transaction is signed. */
    fun signed(nowMs: Long) = advance(Stage.Signed, nowMs)

    /** It has been handed to a node. */
    fun sent(nowMs: Long) = advance(Stage.Sent, nowMs)

    /** The network accepted it. Earlier stages the app could not tell apart are lit now, together. */
    fun accepted(nowMs: Long) = advance(Stage.Accepted, nowMs)

    /** It failed or was refused: the seal breaks. */
    fun failed(nowMs: Long) {
        val current = mutable.value
        if (current.readyAtMs == null || current.acceptedAtMs != null || current.failedAtMs != null) return
        mutable.value = current.copy(failedAtMs = nowMs)
    }

    /** The submission ended without a result (a validation stopped it before signing): nothing to draw. */
    fun cancelIfPending() {
        val current = mutable.value
        if (current.acceptedAtMs == null && current.failedAtMs == null) mutable.value = Record()
    }

    /** Clears the seal (the screen showing it has gone). */
    fun clear() {
        mutable.value = Record()
    }

    /**
     * For the success screen: the record of an operation the network accepted within [windowMs], taken once (a later
     * success screen of another operation draws no seal from it).
     */
    @Synchronized
    fun consumeRecentAccepted(nowMs: Long, windowMs: Long = TransferMoments.DEFAULT_WINDOW_MS): Record? =
        recentAccepted(nowMs, windowMs)?.also { mutable.value = Record() }

    /**
     * Runs one submission (the moment it is handed to signing) and reports its stages: `ready` before, `accepted` with
     * everything this caller cannot tell apart on success. On failure [definiteFailure] decides whether the operation
     * certainly did not happen (the seal breaks) or its fate is unknown (the seal is cleared, never broken). A
     * cancelled submission clears it. The result is returned unchanged and at once. [shownOver] draws the seal over the
     * whole screen on ink ([TransferSealOverlayHost]) for a screen whose own progress is a spinner in its button.
     */
    suspend fun <T> track(
        definiteFailure: (Throwable) -> Boolean = { true },
        shownOver: Boolean = false,
        submit: suspend () -> Result<T>
    ): Result<T> {
        ready(nowMs(), shownOver)
        val result = try {
            submit()
        } catch (error: Throwable) {
            cancelIfPending()
            throw error
        }
        result.fold(
            onSuccess = { accepted(nowMs()) },
            onFailure = { if (it !is kotlinx.coroutines.CancellationException && definiteFailure(it)) failed(nowMs()) else cancelIfPending() }
        )
        return result
    }

    /** The accepted transfer's record if it was accepted within [windowMs], for the success screen. */
    fun recentAccepted(nowMs: Long, windowMs: Long = TransferMoments.DEFAULT_WINDOW_MS): Record? =
        mutable.value.takeIf { it.acceptedAtMs != null && nowMs - it.acceptedAtMs in 0..windowMs }

    private fun advance(stage: Stage, nowMs: Long) {
        val current = mutable.value
        if (current.readyAtMs == null || current.failedAtMs != null) return
        mutable.value = current.copy(
            signedAtMs = current.signedAtMs ?: nowMs.takeIf { stage >= Stage.Signed },
            sentAtMs = current.sentAtMs ?: nowMs.takeIf { stage >= Stage.Sent },
            acceptedAtMs = current.acceptedAtMs ?: nowMs.takeIf { stage >= Stage.Accepted }
        )
    }

    /** For tests and the debug seam. */
    fun set(record: Record) {
        mutable.value = record
    }
}

/**
 * How the seal looks at a moment (SPEC v4 part 3), from a [TransferSeal.Record]: [q] the strokes drawn
 * (0–4: 1 both rings, 2–4 the triangle's three sides), [progress] the ignite beyond them, [strength] the
 * figure's opacity, [broken] 0–1 once it failed, and whether it is [waiting] for the next stage (the cyan
 * comet runs that stroke then).
 */
data class SealFrame(val q: Float, val progress: Float, val strength: Float, val broken: Float, val waiting: Boolean) {
    val complete: Boolean get() = q >= STAGES && progress >= 1f

    companion object {
        const val STAGES = 4
        val Idle = SealFrame(0f, 0f, 1f, 0f, waiting = false)
    }
}

/** The seal's timing, from the kit's `seal` tokens. */
class SealTimeline(
    private val stageMs: Int,
    private val quickMs: Int,
    private val igniteMs: Int,
    private val restMs: Int,
    private val restStrength: Float,
    private val breakMs: Int,
    private val acceptedProgress: Float,
    private val settle: CubicBezier
) {
    /** When stroke i (0 ready … 3 accepted) starts and ends drawing; null where its stage is not reached. */
    fun strokeWindows(record: TransferSeal.Record): List<Pair<Long, Long>?> {
        val times = record.times
        val windows = ArrayList<Pair<Long, Long>?>(SealFrame.STAGES)
        var previous: Pair<Long, Long>? = null
        for (i in 0 until SealFrame.STAGES) {
            val at = times[i]
            if (at == null) {
                windows += null
                continue
            }
            val window = if (i > 0 && previous != null && at == times[i - 1]) {
                // Reported by the same event as the stage before: the app could not tell them apart, so they draw together.
                previous
            } else {
                val start = maxOf(at, previous?.second ?: Long.MIN_VALUE)
                // When the next stage is already known as this stroke starts, it draws in 180 ms instead of 360.
                val next = times.getOrNull(i + 1)
                start to start + if (next != null && next <= start) quickMs else stageMs
            }
            windows += window
            previous = window
        }
        return windows
    }

    fun frame(record: TransferSeal.Record, nowMs: Long): SealFrame {
        if (record.readyAtMs == null) return SealFrame.Idle
        val until = record.failedAtMs?.let { minOf(it, nowMs) } ?: nowMs
        val windows = strokeWindows(record)
        var q = 0f
        var drawing = false
        for (w in windows) {
            if (w == null) break
            val k = ((until - w.first).toFloat() / (w.second - w.first)).coerceIn(0f, 1f)
            if (k > 0f && k < 1f) drawing = true
            q += settle(k)
        }
        val failedAt = record.failedAtMs
        if (failedAt != null) {
            val broken = ((nowMs - failedAt).toFloat() / breakMs).coerceIn(0f, 1f)
            return SealFrame(q, acceptedProgress, 1f, broken, waiting = false)
        }
        val acceptedEnd = windows[SealFrame.STAGES - 1]?.second
        if (acceptedEnd == null || nowMs < acceptedEnd) {
            return SealFrame(q, acceptedProgress, 1f, 0f, waiting = !drawing)
        }
        // Accepted: the rest of the ignite runs from the stage reached to 1, then rests at a quarter.
        val k = ((nowMs - acceptedEnd).toFloat() / igniteMs).coerceIn(0f, 1f)
        val p = acceptedProgress + (1f - acceptedProgress) * (1f - (1f - k) * (1f - k))
        val fade = ((nowMs - acceptedEnd - igniteMs).toFloat() / restMs).coerceIn(0f, 1f)
        return SealFrame(SealFrame.STAGES.toFloat(), p, 1f - (1f - restStrength) * fade, 0f, waiting = false)
    }

    /** When the figure is finished and at rest (nothing left to animate), or null while it is not known. */
    fun restsAtMs(record: TransferSeal.Record): Long? {
        record.failedAtMs?.let { return it + breakMs }
        val acceptedEnd = strokeWindows(record)[SealFrame.STAGES - 1]?.second ?: return null
        return acceptedEnd + igniteMs + restMs
    }
}

/** The seal's timeline from the kit's tokens: stages draw over `seal.stageMs`, or 180 ms (the ladder's third step) when the next is known. */
fun Shikiban.sealTimeline(): SealTimeline = SealTimeline(
    stageMs = seal.stageMs,
    quickMs = durationsMs[2],
    igniteMs = seal.igniteMs,
    restMs = seal.restMs,
    restStrength = seal.restStrength,
    breakMs = seal.breakMs,
    acceptedProgress = sealProgress("accepted"),
    settle = settle
)
