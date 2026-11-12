package jp.co.soramitsu.common.launch

/**
 * Decides what the launch clip does next. It owns no player, view or clock: the overlay reports
 * what happened ([onPrepared], [onFrame], [onTap], [onFailure]) and carries out the returned
 * commands, so every rule of the launch can be tested without a device.
 *
 * The rules (brand kit SPEC, "Launch, feel and sound", part 1): the clip plays to its hold frame;
 * it waits there only while the screen underneath is not ready; then it dives, and the screen
 * underneath zooms in from the dive's focus. A tap skips to the zoom. A clip that cannot load or
 * stalls is dropped with a plain fade. It never waits for anything else and never loops.
 */
class LaunchIgniteDirector(private val timeline: LaunchTimeline, createdAtMs: Long) {

    enum class Phase { Loading, Playing, Holding, Leaving, Done }

    sealed interface Command {
        /** Start (or resume) the clip. */
        object Play : Command

        /** Pause the clip on its hold frame. */
        object Pause : Command

        /** The `launchArrive` haptic. */
        object Arrive : Command

        /**
         * The screen underneath arrives from [contentFromScale] over [contentMs]; after
         * [overlayDelayMs] the clip's layer grows to [overlayToScale] and fades over [overlayMs].
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

    private var loadingSinceMs = createdAtMs
    private var loadStarted = false
    private var holdingSinceMs = 0L
    private var lastPositionMs = -1
    private var lastAdvanceAtMs = createdAtMs
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

    /** The player is ready to show its first frame. */
    fun onPrepared(nowMs: Long): List<Command> {
        if (phase != Phase.Loading) return emptyList()
        phase = Phase.Playing
        lastAdvanceAtMs = nowMs
        return listOf(Command.Play)
    }

    /**
     * Called every display frame with the clip's position. [ready] is whether the first screen is
     * underneath and nothing of the system's (a biometric prompt) is over the app.
     */
    fun onFrame(positionMs: Int, nowMs: Long, ready: Boolean): List<Command> = when (phase) {
        Phase.Loading -> if (nowMs - loadingSinceMs >= LaunchHandOff.STALL_MS) plainFade() else emptyList()
        Phase.Playing -> playing(positionMs, nowMs, ready)
        Phase.Holding -> when {
            ready || nowMs - holdingSinceMs >= LaunchHandOff.HOLD_LIMIT_MS -> {
                phase = Phase.Playing
                lastAdvanceAtMs = nowMs
                listOf(Command.Play)
            }
            else -> emptyList()
        }
        Phase.Leaving, Phase.Done -> emptyList()
    }

    private fun playing(positionMs: Int, nowMs: Long, ready: Boolean): List<Command> {
        if (positionMs != lastPositionMs) {
            lastPositionMs = positionMs
            lastAdvanceAtMs = nowMs
        } else if (nowMs - lastAdvanceAtMs >= LaunchHandOff.STALL_MS) {
            return plainFade()
        }
        // Before the dive: wait on the hold frame while the screen underneath is not there yet.
        if (!ready && positionMs >= timeline.holdMs - HOLD_LEAD_MS && positionMs < timeline.zoomStartMs + HOLD_WINDOW_MS && !pastHold) {
            pastHold = true
            phase = Phase.Holding
            holdingSinceMs = nowMs
            return listOf(Command.Pause)
        }
        if (positionMs >= timeline.holdMs - HOLD_LEAD_MS) pastHold = true
        val commands = ArrayList<Command>(2)
        if (positionMs >= LaunchHandOff.STARTS_AT_MS) {
            phase = Phase.Leaving
            commands += Command.HandOff(
                contentFromScale = LaunchHandOff.CONTENT_FROM_SCALE,
                contentMs = LaunchHandOff.CONTENT_MS,
                overlayDelayMs = (LaunchHandOff.OVERLAY_LEAVES_AT_MS - positionMs).coerceAtLeast(0).toLong(),
                overlayToScale = LaunchHandOff.OVERLAY_TO_SCALE,
                overlayMs = LaunchHandOff.OVERLAY_MS
            )
            // The haptic lands at 2.6 s of the clip; the overlay schedules it relative to this command.
            if (!arrived) {
                arrived = true
                commands += Command.Arrive
            }
        }
        return commands
    }

    private var pastHold = false

    /** A tap anywhere: jump to the zoom at once. */
    fun onTap(): List<Command> {
        if (phase == Phase.Leaving || phase == Phase.Done) return emptyList()
        phase = Phase.Leaving
        val commands = ArrayList<Command>(2)
        commands += Command.HandOff(
            contentFromScale = LaunchHandOff.CONTENT_FROM_SCALE,
            contentMs = LaunchHandOff.SKIP_MS,
            overlayDelayMs = 0,
            overlayToScale = LaunchHandOff.OVERLAY_TO_SCALE,
            overlayMs = LaunchHandOff.SKIP_MS
        )
        if (!arrived) {
            arrived = true
            commands += Command.Arrive
        }
        return commands
    }

    /** The player reported an error, or its surface went away. */
    fun onFailure(): List<Command> = if (phase == Phase.Leaving || phase == Phase.Done) emptyList() else plainFade()

    /** The hand-off's animations have ended (or the host is going away): nothing more will be asked. */
    fun onGone() {
        phase = Phase.Done
    }

    /** No zoom and no haptic: the clip's layer simply fades and the screen underneath is as it is. */
    private fun plainFade(): List<Command> {
        phase = Phase.Leaving
        return listOf(Command.HandOff(contentFromScale = 1f, contentMs = 0, overlayDelayMs = 0, overlayToScale = 1f, overlayMs = LaunchHandOff.PLAIN_FADE_MS))
    }

    private companion object {
        /** Pause this long before the hold time, so the frame held is not already into the dive. */
        const val HOLD_LEAD_MS = 34

        /** A position report can skip past the hold time by a frame or two; it may still pause this far in. */
        const val HOLD_WINDOW_MS = 100
    }
}
