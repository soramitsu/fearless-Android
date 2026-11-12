package jp.co.soramitsu.app.root.presentation.launch

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.SystemClock
import android.view.Choreographer
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import jp.co.soramitsu.app.R
import jp.co.soramitsu.common.launch.LaunchHandOff
import jp.co.soramitsu.common.launch.LaunchIgniteDirector
import jp.co.soramitsu.common.launch.LaunchTimeline
import jp.co.soramitsu.common.launch.launchClipPlacement

/**
 * The cold-start Ignite clip (brand kit SPEC, "Launch, feel and sound", part 1) over the wallet
 * host's first screen. It covers [content] and then reveals it; it never opens, delays or skips
 * any screen, and the screen underneath loads exactly as it does without it. The clip has no audio
 * track and the player is muted besides. Decorative: hidden from accessibility (touch exploration
 * gets no clip at all, see [LaunchIgniteHost]).
 *
 * Layers: brand ink, the clip on a TextureView (platform MediaPlayer, no extra library), and the
 * launch art (the same drawable the startup gate shows) which crossfades away as the clip starts.
 */
@SuppressLint("ViewConstructor")
internal class LaunchIgniteOverlay(
    context: Context,
    timeline: LaunchTimeline,
    private val content: View,
    private val isReady: () -> Boolean,
    private val onArrive: () -> Unit,
    private val onFinished: () -> Unit
) : FrameLayout(context), TextureView.SurfaceTextureListener, Choreographer.FrameCallback {

    private val director = LaunchIgniteDirector(timeline, SystemClock.uptimeMillis())
    private val focusX = timeline.focusX
    private val focusY = timeline.focusY
    private val video = TextureView(context)
    private val art = View(context)
    private var player: MediaPlayer? = null
    private var surface: Surface? = null
    private var prepared = false
    private var startedOnce = false
    private var clipWidth = 0f
    private var clipHeight = 0f
    private var focusPx = floatArrayOf(0f, 0f)
    private var finished = false
    private var frameCallbackPosted = false

    init {
        setBackgroundColor(ContextCompat.getColor(context, jp.co.soramitsu.common.R.color.fw_bg_base))
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        addView(video, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        art.background = ContextCompat.getDrawable(context, R.drawable.fw_window_background)
        addView(art, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        video.surfaceTextureListener = this
        // A tap anywhere skips to the zoom. While the overlay leaves, touches pass to the screen beneath.
        setOnClickListener { run(director.onTap()) }
        // The screen underneath waits, invisible, to be revealed by the zoom.
        content.alpha = 0f
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        postFrame()
    }

    override fun onDetachedFromWindow() {
        release()
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        // Sent to the background mid-clip: drop it, so a return finds the screen, not a frozen crow.
        if (visibility == GONE && !finished) run(director.onFailure())
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (changed) applyCrop()
    }

    override fun doFrame(frameTimeNanos: Long) {
        frameCallbackPosted = false
        if (finished) return
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
                setOnErrorListener { _, _, _ ->
                    run(director.onFailure())
                    true
                }
                director.onLoadStarted(SystemClock.uptimeMillis())
                prepareAsync()
            }
        }.getOrNull()
        if (player == null) run(director.onFailure())
    }

    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = applyCrop()

    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
        if (!finished) run(director.onFailure())
        return true
    }

    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit

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
                LaunchIgniteDirector.Command.Arrive -> postDelayed({ onArrive() }, ARRIVE_AFTER_HAND_OFF_MS)
                is LaunchIgniteDirector.Command.HandOff -> handOff(command)
            }
        }
    }

    private fun play() {
        if (runCatching { player?.start() }.isFailure) {
            run(director.onFailure())
            return
        }
        if (!startedOnce) {
            startedOnce = true
            // The launch art gives way to the clip, whose first frames rise out of the same ink.
            art.animate().alpha(0f).setDuration(ART_CROSSFADE_MS).start()
        }
    }

    private fun handOff(command: LaunchIgniteDirector.Command.HandOff) {
        isClickable = false
        setOnClickListener(null)
        isClickable = false
        val (fx, fy) = focus().let { it[0] to it[1] }
        content.pivotX = fx
        content.pivotY = fy
        if (command.contentMs <= 0L) {
            content.animate().cancel()
            content.alpha = 1f
            content.scaleX = 1f
            content.scaleY = 1f
        } else {
            content.scaleX = command.contentFromScale
            content.scaleY = command.contentFromScale
            content.alpha = 0f
            content.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(command.contentMs)
                .setInterpolator(DecelerateInterpolator(DECELERATION)).start()
        }
        pivotX = fx
        pivotY = fy
        animate().alpha(0f).scaleX(command.overlayToScale).scaleY(command.overlayToScale)
            .setStartDelay(command.overlayDelayMs).setDuration(command.overlayMs)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction { finish() }
            .start()
    }

    private fun finish() {
        if (finished) return
        finished = true
        director.onGone()
        content.animate().cancel()
        content.alpha = 1f
        content.scaleX = 1f
        content.scaleY = 1f
        (parent as? android.view.ViewGroup)?.removeView(this)
        release()
        onFinished()
    }

    private fun release() {
        if (frameCallbackPosted) {
            Choreographer.getInstance().removeFrameCallback(this)
            frameCallbackPosted = false
        }
        player?.let { runCatching { it.release() } }
        player = null
        surface?.release()
        surface = null
        if (!finished) {
            // Detached without a hand-off (the host went away): never leave the screen hidden.
            content.alpha = 1f
            content.scaleX = 1f
            content.scaleY = 1f
        }
    }

    private companion object {
        /** The `launchArrive` haptic lands at 2.6 s, 50 ms after the hand-off starts at 2.55 s. */
        const val ARRIVE_AFTER_HAND_OFF_MS = (LaunchHandOff.HAPTIC_AT_MS - LaunchHandOff.STARTS_AT_MS).toLong()
        const val ART_CROSSFADE_MS = 300L
        const val DECELERATION = 1.6f
    }
}
