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

/** The hand-off from the clip to the screen underneath (brand kit SPEC, "Launch, feel and sound", part 1). */
object LaunchHandOff {
    /** The screen underneath starts to arrive here: scale [CONTENT_FROM_SCALE] and clear, to its own size and opaque. */
    const val STARTS_AT_MS = 2550
    const val CONTENT_MS = 550L
    const val CONTENT_FROM_SCALE = 1.18f

    /** The clip's layer leaves from here: it grows to [OVERLAY_TO_SCALE] and fades out. */
    const val OVERLAY_LEAVES_AT_MS = 2700
    const val OVERLAY_MS = 450L
    const val OVERLAY_TO_SCALE = 1.6f

    /** The `launchArrive` haptic. */
    const val HAPTIC_AT_MS = 2600

    /** A tap anywhere jumps to the zoom at once. */
    const val SKIP_MS = 300L

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

    /** The wallet appearing after a later unlock: from this scale, fading in. */
    const val LANDING_FROM_SCALE = 1.06f
    const val LANDING_MS = 350L
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
    if (minOf(viewWidth, viewHeight, clipWidth, clipHeight) <= 0f) {
        return floatArrayOf(1f, 1f, 0f, 0f, viewWidth * focusX, viewHeight * focusY)
    }
    val scale = maxOf(viewWidth / clipWidth, viewHeight / clipHeight)
    val width = clipWidth * scale
    val height = clipHeight * scale
    val left = (viewWidth / 2f - focusX * width).coerceIn(viewWidth - width, 0f)
    val top = (viewHeight / 2f - focusY * height).coerceIn(viewHeight - height, 0f)
    return floatArrayOf(width / viewWidth, height / viewHeight, left, top, left + focusX * width, top + focusY * height)
}
