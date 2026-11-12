package jp.co.soramitsu.common.launch

/**
 * Decides what the launch clip does next. It owns no player, view or clock: the overlay reports
 * what happened ([onLoadStarted], [onPrepared], [onClipFrame], [onFrame], [onTap], [onFailure])
 * and carries out the returned commands, so every rule of the launch can be tested without a device.
 *
 * The rules (brand kit SPEC, "Launch, feel and sound", part 1): the launch art covers the screen
 * until the clip's first frame is really on screen; the clip plays to its hold frame and waits
 * there only while the screen underneath is not ready; then it dives and the screen underneath
 * zooms in from the dive's focus. A tap skips to the zoom. A clip that does not start, stalls or
 * fails is dropped: the launch art covers again and stays until the screen underneath is ready,
 * then fades to it, so a launch never shows an empty screen. Nothing waits longer than its limit.
 */
class LaunchIgniteDirector(private val timeline: LaunchTimeline, private val createdAtMs: Long) {

    enum class Phase { Loading, Playing, Holding, Covering, Leaving, Done }

    /** Why a clip was dropped. */
    enum class DropReason { NotStarted, Stalled, PlayerError, SurfaceLost, Background, Skipped }

    sealed interface Command {
        /** Start (or resume) the clip. */
        object Play : Command

        /** Pause the clip on its hold frame. */
        object Pause : Command

        /** The `launchArrive` haptic. */
        object Arrive : Command

        /** The clip's first frame is on screen: the launch art gives way to it. */
        object RevealClip : Command

        /** The clip is dropped for [reason]: the launch art covers the screen until it is ready. */
        data class Cover(val reason: DropReason) : Command

        /**
         * The screen underneath arrives from [contentFromScale] over [contentMs]; after
         * [overlayDelayMs] the overlay grows to [overlayToScale] and fades over [overlayMs].
         */
        data class HandOff(
            val contentFromScale: Float,
            val contentMs: Long,
            val overlayDelayMs: Long,
            val overlayToScale: Float,
            val overlayMs: Long
        ) : Command
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
    private var arrived = false

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
        Phase.Loading -> if (nowMs - loadingSinceMs >= LaunchHandOff.STALL_MS) cover(DropReason.NotStarted, nowMs) else emptyList()
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
        if (firstFrameAtMs == null && nowMs - playSinceMs >= LaunchHandOff.STALL_MS) return cover(DropReason.NotStarted, nowMs)
        if (positionMs != lastPositionMs) {
            lastPositionMs = positionMs
            lastAdvanceAtMs = nowMs
        } else if (nowMs - lastAdvanceAtMs >= LaunchHandOff.STALL_MS) {
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
        if (positionMs < LaunchHandOff.STARTS_AT_MS) return emptyList()
        phase = Phase.Leaving
        return withArrive(
            Command.HandOff(
                contentFromScale = LaunchHandOff.CONTENT_FROM_SCALE,
                contentMs = LaunchHandOff.CONTENT_MS,
                overlayDelayMs = (LaunchHandOff.OVERLAY_LEAVES_AT_MS - positionMs).coerceAtLeast(0).toLong(),
                overlayToScale = LaunchHandOff.OVERLAY_TO_SCALE,
                overlayMs = LaunchHandOff.OVERLAY_MS
            )
        )
    }

    /** A tap anywhere: jump to the zoom at once, or, while the screen underneath is not ready, cover until it is. */
    fun onTap(nowMs: Long, ready: Boolean): List<Command> = when (phase) {
        Phase.Leaving, Phase.Done, Phase.Covering -> emptyList()
        else -> if (!ready) {
            cover(DropReason.Skipped, nowMs)
        } else {
            phase = Phase.Leaving
            withArrive(
                Command.HandOff(
                    contentFromScale = LaunchHandOff.CONTENT_FROM_SCALE,
                    contentMs = LaunchHandOff.SKIP_MS,
                    overlayDelayMs = 0,
                    overlayToScale = LaunchHandOff.OVERLAY_TO_SCALE,
                    overlayMs = LaunchHandOff.SKIP_MS
                )
            )
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

    /** No zoom and no haptic: the overlay fades and the screen underneath, already drawn, is as it is. */
    private fun plainHandOff(): List<Command> {
        phase = Phase.Leaving
        return listOf(Command.HandOff(contentFromScale = 1f, contentMs = 0, overlayDelayMs = 0, overlayToScale = 1f, overlayMs = LaunchHandOff.PLAIN_FADE_MS))
    }

    private fun withArrive(handOff: Command.HandOff): List<Command> {
        if (arrived) return listOf(handOff)
        arrived = true
        return listOf(handOff, Command.Arrive)
    }

    private companion object {
        /** Pause this long before the hold time, so the frame held is not already into the dive. */
        const val HOLD_LEAD_MS = 34

        /** A position report can skip past the hold time by a frame or two; it may still pause this far in. */
        const val HOLD_WINDOW_MS = 100
    }
}
