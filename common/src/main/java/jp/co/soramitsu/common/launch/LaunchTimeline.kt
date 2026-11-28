package jp.co.soramitsu.common.launch

import com.google.gson.JsonParser

/**
 * The launch clip's own times and the point its dive ends on (the brand kit's
 * `hero/launch-timeline.json`, copied to `raw/fw_launch_timeline.json`). Times are milliseconds
 * from the clip's first frame; the focus is a fraction of the clip's frame.
 */
data class LaunchTimeline(
    val durationMs: Int,
    val fadeInMs: Int,
    val holdMs: Int,
    val zoomStartMs: Int,
    val zoomEndMs: Int,
    val focusX: Float,
    val focusY: Float
) {
    init {
        require(fadeInMs in 0..holdMs && holdMs <= zoomStartMs && zoomStartMs < zoomEndMs && zoomEndMs <= durationMs) {
            "Launch timeline is out of order"
        }
        require(focusX in 0f..1f && focusY in 0f..1f) { "Launch focus is outside the frame" }
    }

    companion object {
        /** Parses the kit file's phone framing. Throws on anything missing: the clip is then not played. */
        fun parse(json: String): LaunchTimeline {
            val root = JsonParser.parseString(json).asJsonObject
            fun ms(name: String) = Math.round(root.get(name).asDouble * 1000.0).toInt()
            val focus = root.getAsJsonObject("focus").getAsJsonObject("phone")
            return LaunchTimeline(
                durationMs = ms("durationSeconds"),
                fadeInMs = ms("fadeInSeconds"),
                holdMs = ms("holdSeconds"),
                zoomStartMs = ms("zoomStartSeconds"),
                zoomEndMs = ms("zoomEndSeconds"),
                focusX = focus.get("x").asFloat,
                focusY = focus.get("y").asFloat
            )
        }
    }
}

/** The hand-off from the clip to the screen underneath (brand kit SPEC, "Launch, feel and sound", part 1; v4 part 1). */
object LaunchHandOff {
    /** The `launchArrive` haptic plays this far into the flight (SPEC v4 part 1, "Touch"). */
    const val FLIGHT_ARRIVE_MS = 1000

    /** A clip that cannot load, or stalls for this long, is dropped with a plain fade. */
    const val STALL_MS = 1000L
    const val PLAIN_FADE_MS = 300L

    /**
     * The longest the clip waits on its hold frame for the screen underneath. After it the clip
     * leaves anyway, so a start that never reaches a first screen cannot keep the crow on top.
     */
    const val HOLD_LIMIT_MS = 6000L

    /**
     * A dropped clip leaves the launch art over the host until its first screen is ready; after this
     * long it goes anyway, so a host that never reaches a screen cannot stay covered.
     */
    const val COVER_LIMIT_MS = 10_000L
}

/**
 * Where the clip's frame lands in a view under aspect-fill, centred on the dive's focus as far as
 * the frame allows (the clip always covers the view). Returns scaleX, scaleY, translateX,
 * translateY of a TextureView transform (a TextureView stretches its video to the view by
 * default), then the focus point in view pixels.
 */
fun launchClipPlacement(
    viewWidth: Float,
    viewHeight: Float,
    clipWidth: Float,
    clipHeight: Float,
    focusX: Float,
    focusY: Float
): FloatArray {
    val sizes = floatArrayOf(viewWidth, viewHeight, clipWidth, clipHeight)
    if (sizes.any { !it.isFinite() || it <= 0f }) {
        return floatArrayOf(1f, 1f, 0f, 0f, viewWidth * focusX, viewHeight * focusY)
    }
    val scale = maxOf(viewWidth / clipWidth, viewHeight / clipHeight)
    // Float rounding can leave the filled side a hair short of the view (1080 - 1079.99997): the lower
    // bound then sits above 0 and coerceIn throws, closing the app at start (Play 240). Never above 0.
    val width = maxOf(clipWidth * scale, viewWidth)
    val height = maxOf(clipHeight * scale, viewHeight)
    val left = (viewWidth / 2f - focusX * width).coerceIn(minOf(viewWidth - width, 0f), 0f)
    val top = (viewHeight / 2f - focusY * height).coerceIn(minOf(viewHeight - height, 0f), 0f)
    return floatArrayOf(width / viewWidth, height / viewHeight, left, top, left + focusX * width, top + focusY * height)
}

/**
 * The launch flight's own clock: milliseconds into the flight at a real time, `durationMs` long. A tap
 * plays the rest of it at `durationMs / skipMs` (2.5 times) the speed, from where it is, so it never jumps.
 */
class LaunchFlightClock(
    private val durationMs: Int,
    private val skipMs: Int,
    startedAtMs: Long,
    skipped: Boolean,
    /** Debug walks only: plays the flight this many times slower, so a loaded emulator's recording keeps its frames. */
    private val slowdown: Float = 1f
) {
    private var anchorRealMs = startedAtMs
    private var anchorFlightMs = 0f
    private val skipRate = durationMs.toFloat() / skipMs / slowdown
    private var rate = if (skipped) skipRate else 1f / slowdown

    val skipped: Boolean get() = rate == skipRate

    /** Plays the rest at the skip speed. */
    fun skip(nowMs: Long) {
        if (skipped) return
        anchorFlightMs = elapsed(nowMs)
        anchorRealMs = nowMs
        rate = skipRate
    }

    /** Milliseconds into the flight, 0..durationMs. */
    fun elapsed(nowMs: Long): Float = (anchorFlightMs + (nowMs - anchorRealMs).coerceAtLeast(0L) * rate).coerceIn(0f, durationMs.toFloat())

    fun finished(nowMs: Long): Boolean = elapsed(nowMs) >= durationMs
}
