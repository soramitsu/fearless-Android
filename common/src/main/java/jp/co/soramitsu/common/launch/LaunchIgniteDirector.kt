package jp.co.soramitsu.common.launch

/**
 * Decides what the launch clip does next. It owns no player, view or clock: the overlay reports
 * what happened ([onLoadStarted], [onPrepared], [onClipFrame], [onFrame], [onTap], [onFailure])
 * and carries out the returned commands, so every rule of the launch can be tested without a device.
 *
 * The rules (brand kit SPEC, "Launch, feel and sound", part 1, with v4's launch flight in place of
 * "The zoom"): the launch art covers the screen until the clip's first frame is really on screen; the
 * clip plays to its hold frame and waits there only while the screen underneath is not ready; at the
 * clip's `zoomStartSeconds` (or when the hold releases) the flight starts: the gates come apart about
 * the dive's focus, the mirror opens and the first screen flies up through it. A tap starts the
 * flight at once, or plays the rest of it 2.5 times faster. A clip that does not start, stalls or
 * fails is dropped: the launch art covers again and stays until the screen underneath is ready,
 * then fades to it, so a launch never shows an empty screen. Nothing waits longer than its limit.
 */
class LaunchIgniteDirector(
    private val timeline: LaunchTimeline,
    private val createdAtMs: Long,
    /** How long a clip may take to start or may stall; longer only for a debug walk on a loaded emulator. */
    private val stallMs: Long = LaunchHandOff.STALL_MS
) {

    enum class Phase { Loading, Playing, Holding, Covering, Leaving, Done }

    /** Why a clip was dropped. */
    enum class DropReason { NotStarted, Stalled, PlayerError, SurfaceLost, Background, Skipped }

    sealed interface Command {
        /** Start (or resume) the clip. */
        object Play : Command

        /** Pause the clip on its hold frame. */
        object Pause : Command

        /** Start the launch flight; [skipped] plays all of it at the skip speed. */
        data class Flight(val skipped: Boolean) : Command

        /** A tap during the flight: play the rest of it at the skip speed. */
        object SkipFlight : Command

        /** The clip's first frame is on screen: the launch art gives way to it. */
        object RevealClip : Command

        /** The clip is dropped for [reason]: the launch art covers the screen until it is ready. */
        data class Cover(val reason: DropReason) : Command

        /** No flight (a dropped clip): the overlay fades over [overlayMs] to the screen underneath, already drawn. */
        data class PlainFade(val overlayMs: Long) : Command
    }

    var phase: Phase = Phase.Loading
        private set

    /** Why the clip was dropped, or null while it plays (or once it played). */
    var dropReason: DropReason? = null
        private set

    /** Milliseconds from the overlay's creation to the clip's first frame on screen, or null. */
    val firstFrameAfterMs: Long?
        get() = firstFrameAtMs?.let { it - createdAtMs }

    private var loadingSinceMs = createdAtMs
    private var loadStarted = false
    private var playSinceMs = 0L
    private var firstFrameAtMs: Long? = null
    private var holdingSinceMs = 0L
    private var coverSinceMs = 0L
    private var lastPositionMs = -1
    private var lastAdvanceAtMs = createdAtMs
    private var pastHold = false
    private var flightSkipped = false

    /**
     * The player started loading the clip. From here it has [LaunchHandOff.STALL_MS] to be ready;
     * before it, the same time runs from the overlay's creation (a surface that never comes).
     */
    fun onLoadStarted(nowMs: Long) {
        if (phase != Phase.Loading || loadStarted) return
        loadStarted = true
        loadingSinceMs = nowMs
    }

    /** The player is ready to play. */
    fun onPrepared(nowMs: Long): List<Command> {
        if (phase != Phase.Loading) return emptyList()
        phase = Phase.Playing
        playSinceMs = nowMs
        lastAdvanceAtMs = nowMs
        return listOf(Command.Play)
    }

    /** A frame of the clip reached the screen. The first one lets the clip be seen. */
    fun onClipFrame(nowMs: Long): List<Command> {
        if (firstFrameAtMs != null || (phase != Phase.Playing && phase != Phase.Holding)) return emptyList()
        firstFrameAtMs = nowMs
        return listOf(Command.RevealClip)
    }

    /**
     * Called every display frame with the clip's position. [ready] is whether the first screen is
     * underneath and nothing of the system's (a biometric prompt) is over the app.
     */
    fun onFrame(positionMs: Int, nowMs: Long, ready: Boolean): List<Command> = when (phase) {
        Phase.Loading -> if (nowMs - loadingSinceMs >= stallMs) cover(DropReason.NotStarted, nowMs) else emptyList()
        Phase.Playing -> playing(positionMs, nowMs, ready)
        Phase.Holding -> when {
            ready || nowMs - holdingSinceMs >= LaunchHandOff.HOLD_LIMIT_MS -> {
                phase = Phase.Playing
                lastAdvanceAtMs = nowMs
                listOf(Command.Play)
            }
            else -> emptyList()
        }
        Phase.Covering -> if (ready || nowMs - coverSinceMs >= LaunchHandOff.COVER_LIMIT_MS) plainHandOff() else emptyList()
        Phase.Leaving, Phase.Done -> emptyList()
    }

    private fun playing(positionMs: Int, nowMs: Long, ready: Boolean): List<Command> {
        // Playing by the player's clock but nothing on screen yet: a decoder that cannot keep up.
        if (firstFrameAtMs == null && nowMs - playSinceMs >= stallMs) return cover(DropReason.NotStarted, nowMs)
        if (positionMs != lastPositionMs) {
            lastPositionMs = positionMs
            lastAdvanceAtMs = nowMs
        } else if (nowMs - lastAdvanceAtMs >= stallMs) {
            return cover(DropReason.Stalled, nowMs)
        }
        // Before the dive: wait on the hold frame while the screen underneath is not there yet.
        if (!ready && !pastHold && positionMs >= timeline.holdMs - HOLD_LEAD_MS && positionMs < timeline.zoomStartMs + HOLD_WINDOW_MS) {
            pastHold = true
            phase = Phase.Holding
            holdingSinceMs = nowMs
            return listOf(Command.Pause)
        }
        if (positionMs >= timeline.holdMs - HOLD_LEAD_MS) pastHold = true
        if (positionMs < timeline.zoomStartMs) return emptyList()
        phase = Phase.Leaving
        return listOf(Command.Flight(skipped = false))
    }

    /**
     * A tap anywhere: start the flight at once at the skip speed, or speed up the flight that runs; while the
     * screen underneath is not ready, cover until it is.
     */
    fun onTap(nowMs: Long, ready: Boolean): List<Command> = when (phase) {
        Phase.Leaving -> if (flightSkipped) emptyList() else {
            flightSkipped = true
            listOf(Command.SkipFlight)
        }
        Phase.Done, Phase.Covering -> emptyList()
        else -> if (!ready) {
            cover(DropReason.Skipped, nowMs)
        } else {
            phase = Phase.Leaving
            flightSkipped = true
            listOf(Command.Flight(skipped = true))
        }
    }

    /** The player reported an error, its surface went away, or the app went to the background. */
    fun onFailure(reason: DropReason, nowMs: Long): List<Command> = when (phase) {
        Phase.Leaving, Phase.Done, Phase.Covering -> emptyList()
        else -> cover(reason, nowMs)
    }

    /** The hand-off's animations have ended (or the host is going away): nothing more will be asked. */
    fun onGone() {
        phase = Phase.Done
    }

    private fun cover(reason: DropReason, nowMs: Long): List<Command> {
        phase = Phase.Covering
        dropReason = reason
        coverSinceMs = nowMs
        return listOf(Command.Cover(reason))
    }

    /** No flight and no haptic: the overlay fades and the screen underneath, already drawn, is as it is. */
    private fun plainHandOff(): List<Command> {
        phase = Phase.Leaving
        flightSkipped = true
        return listOf(Command.PlainFade(LaunchHandOff.PLAIN_FADE_MS))
    }

    private companion object {
        /** Pause this long before the hold time, so the frame held is not already into the dive. */
        const val HOLD_LEAD_MS = 34

        /** A position report can skip past the hold time by a frame or two; it may still pause this far in. */
        const val HOLD_WINDOW_MS = 100
    }
}
