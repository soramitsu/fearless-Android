package jp.co.soramitsu.app.root.presentation.launch

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.SystemClock
import android.util.Log
import android.view.Choreographer
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import java.io.File
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import jp.co.soramitsu.app.R
import jp.co.soramitsu.common.launch.LaunchFlightClock
import jp.co.soramitsu.common.launch.LaunchFlightStage
import jp.co.soramitsu.common.launch.LaunchHandOff
import jp.co.soramitsu.common.launch.LaunchIgniteDirector
import jp.co.soramitsu.common.launch.LaunchTimeline
import jp.co.soramitsu.common.launch.launchClipPlacement
import jp.co.soramitsu.common.shikiban.Shikiban
import jp.co.soramitsu.common.shikiban.ShikibanFlightPainter

/**
 * The cold-start Ignite clip (brand kit SPEC, "Launch, feel and sound", part 1) over the wallet
 * host's first screen. It covers [content] and then reveals it; it never opens, delays or skips
 * any screen, and the screen underneath loads exactly as it does without it. The clip has no audio
 * track and the player is muted besides. Decorative: hidden from accessibility (touch exploration
 * gets no clip at all, see [LaunchIgniteHost]).
 *
 * Layers: brand ink, the clip on a TextureView (platform MediaPlayer, no extra library), and the
 * launch art (the same drawable the startup gate shows) which crossfades away as the clip starts.
 *
 * The launch flight (SPEC v4 part 1) replaces v3's zoom: from the clip's dive the shikiban gates are
 * drawn over the clip about the dive's focus, the mirror opens a round hole through this overlay (an
 * offscreen layer and a DST_OUT circle), the corona gathers round its rim, and the first screen's
 * tiers ([LaunchFlightStage]) fly up through it. Once the opening has cleared the view the overlay
 * stops drawing and releases the player; when the flight ends it is removed and every transform is
 * cleared.
 */
@SuppressLint("ViewConstructor")
internal class LaunchIgniteOverlay(
    context: Context,
    timeline: LaunchTimeline,
    private val tokens: Shikiban,
    private val content: View,
    private val isReady: () -> Boolean,
    private val onArrive: () -> Unit,
    private val onFinished: () -> Unit
) : FrameLayout(context), TextureView.SurfaceTextureListener, Choreographer.FrameCallback {

    private val createdAtMs = SystemClock.uptimeMillis()
    private val debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    // Debug builds only, for a walk on a loaded emulator (`run-as <package> touch cache/<name>`): a patient clip
    // start (10 s instead of 1 s) and a flight played ten times slower so a slow recording keeps its frames.
    private val patient = debuggable && File(context.cacheDir, DEBUG_PATIENT).exists()
    private val slowdown = if (debuggable && File(context.cacheDir, DEBUG_SLOW_FLIGHT).exists()) DEBUG_SLOWDOWN else 1f
    private val director = LaunchIgniteDirector(timeline, createdAtMs, if (patient) DEBUG_PATIENT_STALL_MS else LaunchHandOff.STALL_MS)
    private val focusX = timeline.focusX
    private val focusY = timeline.focusY
    private val video = TextureView(context)
    private val art = View(context)
    private var player: MediaPlayer? = null
    private var surface: Surface? = null
    private var prepared = false
    private var clipWidth = 0f
    private var clipHeight = 0f
    private var focusPx = floatArrayOf(0f, 0f)
    private var finished = false
    private var frameCallbackPosted = false
    private val ink = ContextCompat.getColor(context, jp.co.soramitsu.common.R.color.fw_bg_base)
    private val painter = ShikibanFlightPainter(tokens, resources.displayMetrics.density)
    private var clock: LaunchFlightClock? = null
    private var flightElapsedMs = 0f
    private var tiered = false
    private var arrived = false
    private var openingCleared = false
    private val windowLocation = IntArray(2)

    init {
        setBackgroundColor(ink)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        addView(video, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        art.background = ContextCompat.getDrawable(context, R.drawable.fw_window_background)
        addView(art, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        video.surfaceTextureListener = this
        // A tap anywhere starts the flight, or speeds it up. Once the opening clears, touches pass to the screen beneath.
        setOnClickListener { run(director.onTap(SystemClock.uptimeMillis(), isReady())) }
        // The screen underneath waits, invisible, to be revealed by the flight.
        content.alpha = 0f
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        log("attached")
        postFrame()
    }

    override fun onDetachedFromWindow() {
        release()
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        // Sent to the background mid-clip: drop it, so a return finds the screen, not a frozen crow.
        if (visibility == GONE && !finished) run(director.onFailure(LaunchIgniteDirector.DropReason.Background, SystemClock.uptimeMillis()))
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (changed) applyCrop()
    }

    override fun doFrame(frameTimeNanos: Long) {
        frameCallbackPosted = false
        if (finished) return
        if (clock != null) {
            stepFlight(SystemClock.uptimeMillis())
            postFrame()
            return
        }
        val position = if (prepared) runCatching { player?.currentPosition ?: 0 }.getOrDefault(0) else 0
        run(director.onFrame(position, SystemClock.uptimeMillis(), isReady()))
        postFrame()
    }

    private fun postFrame() {
        if (finished || frameCallbackPosted) return
        frameCallbackPosted = true
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
        if (finished || player != null) return
        // Debug builds only: a walk can make the clip "not start" to see the fallback
        // (`run-as <package> touch cache/fw_launch_skip_clip`).
        if (debuggable && File(context.cacheDir, DEBUG_SKIP_CLIP).exists()) {
            log("debug: clip load skipped")
            return
        }
        val created = Surface(texture)
        surface = created
        player = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build())
                resources.openRawResourceFd(R.raw.fw_launch_ignite).use { setDataSource(it) }
                setVolume(0f, 0f)
                setSurface(created)
                setOnVideoSizeChangedListener { _, w, h ->
                    clipWidth = w.toFloat()
                    clipHeight = h.toFloat()
                    applyCrop()
                }
                setOnPreparedListener {
                    prepared = true
                    run(director.onPrepared(SystemClock.uptimeMillis()))
                }
                setOnErrorListener { _, what, extra ->
                    log("player error $what/$extra")
                    run(director.onFailure(LaunchIgniteDirector.DropReason.PlayerError, SystemClock.uptimeMillis()))
                    true
                }
                director.onLoadStarted(SystemClock.uptimeMillis())
                prepareAsync()
            }
        }.getOrNull()
        if (player == null) run(director.onFailure(LaunchIgniteDirector.DropReason.PlayerError, SystemClock.uptimeMillis()))
    }

    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = applyCrop()

    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
        if (!finished) run(director.onFailure(LaunchIgniteDirector.DropReason.SurfaceLost, SystemClock.uptimeMillis()))
        return true
    }

    // A frame of the clip has reached the view: only now may the launch art give way (the player's
    // own "rendering started" comes before the frame shows when the device is busy).
    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {
        if (!finished) run(director.onClipFrame(SystemClock.uptimeMillis()))
    }

    private fun applyCrop() {
        if (clipWidth <= 0f || clipHeight <= 0f || width == 0 || height == 0) return
        val placement = launchClipPlacement(width.toFloat(), height.toFloat(), clipWidth, clipHeight, focusX, focusY)
        video.setTransform(Matrix().apply {
            setScale(placement[0], placement[1])
            postTranslate(placement[2], placement[3])
        })
        focusPx = floatArrayOf(placement[4], placement[5])
    }

    /** Where the dive ends, in this view: the clip's focus, or the kit's focus point before the clip's size is known. */
    private fun focus(): FloatArray = if (clipWidth > 0f) focusPx else floatArrayOf(width * focusX, height * focusY)

    private fun run(commands: List<LaunchIgniteDirector.Command>) {
        for (command in commands) {
            when (command) {
                LaunchIgniteDirector.Command.Play -> play()
                LaunchIgniteDirector.Command.Pause -> runCatching { player?.pause() }
                LaunchIgniteDirector.Command.RevealClip -> revealClip()
                is LaunchIgniteDirector.Command.Cover -> cover(command.reason)
                is LaunchIgniteDirector.Command.Flight -> startFlight(command.skipped)
                LaunchIgniteDirector.Command.SkipFlight -> clock?.skip(SystemClock.uptimeMillis())
                is LaunchIgniteDirector.Command.PlainFade -> plainFade(command.overlayMs)
            }
        }
    }

    private fun play() {
        if (runCatching { player?.start() }.isFailure) {
            run(director.onFailure(LaunchIgniteDirector.DropReason.PlayerError, SystemClock.uptimeMillis()))
        }
    }

    /** The clip is dropped: the launch art covers again, and stays until the screen underneath is ready. */
    private fun cover(reason: LaunchIgniteDirector.DropReason) {
        log("clip dropped ($reason) after ${sinceAttach()} ms; launch art stays until the screen is ready")
        runCatching { player?.pause() }
        art.animate().cancel()
        art.animate().alpha(1f).setDuration(ART_COVER_MS).start()
    }

    private fun sinceAttach(): Long = SystemClock.uptimeMillis() - createdAtMs

    private fun log(message: String) {
        if (debuggable) Log.i(TAG, message)
    }

    private var clipRevealed = false

    /** The launch art gives way to the clip, whose first frames rise out of the same ink. */
    private fun revealClip() {
        if (clipRevealed || finished) return
        clipRevealed = true
        log("clip first frame after ${director.firstFrameAfterMs ?: sinceAttach()} ms")
        art.animate().alpha(0f).setDuration(ART_CROSSFADE_MS).start()
    }

    /**
     * The flight starts (SPEC v4 part 1). A screen that declares tiers keeps its background still and its tiers fly;
     * a screen that declares none flies as one piece at tier 0.
     */
    private fun startFlight(skipped: Boolean) {
        if (clock != null || finished) return
        val now = SystemClock.uptimeMillis()
        log("flight starts after ${sinceAttach()} ms${if (skipped) " (skipped)" else ""}")
        clock = LaunchFlightClock(tokens.flight.durationMs, tokens.flight.skipMs, now, skipped, slowdown)
        // The opening is cut through an offscreen layer that holds the ink, the clip and the gates.
        background = null
        setWillNotDraw(false)
        tiered = LaunchFlightStage.declared > 0
        val (fx, fy) = focus().let { it[0] to it[1] }
        getLocationInWindow(windowLocation)
        if (tiered) {
            LaunchFlightStage.begin(windowLocation[0] + fx, windowLocation[1] + fy, { tokens.tierScale(it, 0f) }, { 0f })
            content.alpha = 1f
        } else {
            val contentAt = IntArray(2).also(content::getLocationInWindow)
            content.pivotX = windowLocation[0] + fx - contentAt[0]
            content.pivotY = windowLocation[1] + fy - contentAt[1]
            content.scaleX = tokens.tierScale(0, 0f)
            content.scaleY = tokens.tierScale(0, 0f)
            content.alpha = 0f
        }
        stepFlight(now)
        postFrame()
    }

    private fun stepFlight(nowMs: Long) {
        val flight = clock ?: return
        val elapsed = flight.elapsed(nowMs)
        flightElapsedMs = elapsed
        val u = elapsed / tokens.flight.durationMs
        if (!arrived && elapsed >= LaunchHandOff.FLIGHT_ARRIVE_MS) {
            arrived = true
            onArrive()
        }
        if (tiered) {
            LaunchFlightStage.apply({ tokens.tierScale(it, u) }, { tokens.tierAlpha(it, elapsed) })
        } else {
            val scale = tokens.tierScale(0, u)
            content.scaleX = scale
            content.scaleY = scale
            content.alpha = tokens.tierAlpha(0, elapsed)
        }
        if (!openingCleared) {
            val (fx, fy) = focus().let { it[0] to it[1] }
            val far = hypot(max(fx, width - fx), max(fy, height - fy))
            if (width > 0 && tokens.irisRadius(elapsed, min(width, height).toFloat()) >= far) {
                // The opening has cleared the view: nothing of the overlay is left to see.
                openingCleared = true
                log("opening cleared after ${sinceAttach()} ms (flight ${elapsed.toInt()} ms)")
                isClickable = false
                setOnClickListener(null)
                visibility = INVISIBLE
                releasePlayer()
            } else {
                invalidate()
            }
        }
        if (flight.finished(nowMs)) finish()
    }

    override fun dispatchDraw(canvas: Canvas) {
        if (clock == null) {
            super.dispatchDraw(canvas)
            return
        }
        val w = width
        val h = height
        val elapsed = flightElapsedMs
        val u = elapsed / tokens.flight.durationMs
        val (fx, fy) = focus().let { it[0] to it[1] }
        val side = min(w, h).toFloat()
        val radius = tokens.irisRadius(elapsed, side)
        val layer = canvas.saveLayer(0f, 0f, w.toFloat(), h.toFloat(), null)
        canvas.drawColor(ink)
        super.dispatchDraw(canvas)
        painter.drawCorona(canvas, fx, fy, radius, side, tokens.coronaStrength(elapsed), w, h)
        painter.drawGates(canvas, u, fx, fy, side, hypot(max(fx, w - fx), max(fy, h - fy)))
        painter.punchOpening(canvas, fx, fy, radius)
        canvas.restoreToCount(layer)
    }

    /** A dropped clip (or a tap before the screen was ready): the overlay fades to the screen, already drawn. */
    private fun plainFade(overlayMs: Long) {
        log("launch art fades to the ready screen after ${sinceAttach()} ms")
        isClickable = false
        setOnClickListener(null)
        content.animate().cancel()
        content.alpha = 1f
        animate().alpha(0f).setDuration(overlayMs).withEndAction { finish() }.start()
    }

    private fun finish() {
        if (finished) return
        finished = true
        director.onGone()
        endTransforms()
        (parent as? android.view.ViewGroup)?.removeView(this)
        release()
        onFinished()
    }

    /** Every tier at rest and no scale or pivot left on the screen underneath. */
    private fun endTransforms() {
        if (tiered) LaunchFlightStage.end()
        content.animate().cancel()
        content.alpha = 1f
        content.scaleX = 1f
        content.scaleY = 1f
        content.clearPivot()
    }

    private fun releasePlayer() {
        player?.let { runCatching { it.release() } }
        player = null
        surface?.release()
        surface = null
    }

    private fun release() {
        if (frameCallbackPosted) {
            Choreographer.getInstance().removeFrameCallback(this)
            frameCallbackPosted = false
        }
        releasePlayer()
        if (!finished) {
            // Detached without a hand-off (the host went away): never leave the screen hidden or scaled.
            finished = true
            endTransforms()
        }
    }

    private companion object {
        const val ART_CROSSFADE_MS = 300L
        const val ART_COVER_MS = 150L
        const val TAG = "FwLaunch"
        const val DEBUG_SKIP_CLIP = "fw_launch_skip_clip"
        const val DEBUG_PATIENT = "fw_launch_patient"
        const val DEBUG_PATIENT_STALL_MS = 10_000L
        const val DEBUG_SLOW_FLIGHT = "fw_launch_slow_flight"
        const val DEBUG_SLOWDOWN = 10f
    }
}

/** Back to the default pivot (the view's centre), as if none had been set. */
internal fun View.clearPivot() {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) resetPivot() else {
        pivotX = width / 2f
        pivotY = height / 2f
    }
}
