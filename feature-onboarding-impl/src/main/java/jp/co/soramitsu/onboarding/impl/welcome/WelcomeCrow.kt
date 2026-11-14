package jp.co.soramitsu.onboarding.impl.welcome

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.RawRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import jp.co.soramitsu.common.compose.theme.fw.FwTier
import jp.co.soramitsu.common.compose.theme.fw.LocalFwColors
import jp.co.soramitsu.common.compose.theme.fw.LocalFwEffects
import jp.co.soramitsu.feature_onboarding_impl.R
import kotlin.math.max

/** Crossfade from the idle loop into the call and back (brand rollout spec: 250 ms). */
internal const val CROW_CROSSFADE_MS = 250L

// The idle loop's first frame replaces the poster this quickly once it renders.
private const val CROW_REVEAL_MS = 150L

/** Sound follows the device: in silent or vibrate ringer mode the call plays without sound. */
internal fun crowCallMuted(ringerMode: Int): Boolean = ringerMode != AudioManager.RINGER_MODE_NORMAL

/**
 * Aspect-fill for a TextureView, which stretches its video to the view by default: the video covers
 * the view, centred horizontally and anchored to the top. Returns scaleX, scaleY, translateX and
 * translateY of the view's transform.
 */
internal fun crowCropTransform(
    viewWidth: Float,
    viewHeight: Float,
    videoWidth: Float,
    videoHeight: Float
): FloatArray {
    if (minOf(viewWidth, viewHeight, videoWidth, videoHeight) <= 0f) return floatArrayOf(1f, 1f, 0f, 0f)
    val scale = max(viewWidth / videoWidth, viewHeight / videoHeight)
    val width = videoWidth * scale
    val height = videoHeight * scale
    return floatArrayOf(width / viewWidth, height / viewHeight, (viewWidth - width) / 2f, 0f)
}

private fun mediaAttributes(): AudioAttributes = AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_MEDIA)
    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
    .build()

private fun Context.rawPlayer(@RawRes res: Int): MediaPlayer = MediaPlayer().also { player ->
    player.setAudioAttributes(mediaAttributes())
    resources.openRawResourceFd(res).use { player.setDataSource(it) }
}

/**
 * The idle loop and the call on two TextureViews played by the platform MediaPlayer (no extra
 * player library). The idle loop is muted and loops; the call starts and ends on the loop's first
 * pose, so the view crossfades into it and, once it ends, back into the loop restarted at its first
 * frame. Taps during a call are ignored. Hidden from accessibility: the page's crow button speaks.
 */
@SuppressLint("ViewConstructor")
internal class CrowVideoView(context: Context) : FrameLayout(context) {
    private val idleView = TextureView(context).apply { alpha = 0f }
    private val callView = TextureView(context).apply { alpha = 0f }
    private val audio: AudioManager? = context.getSystemService(AudioManager::class.java)
    private var idle: MediaPlayer? = null
    private var call: MediaPlayer? = null
    private var idleSurface: Surface? = null
    private var callSurface: Surface? = null
    private var idlePrepared = false
    private var callPrepared = false
    private var callRendered = false
    private var active = false
    private var clipWidth = 0f
    private var clipHeight = 0f

    /** True from a tap until the loop is back on screen. */
    var calling = false
        private set

    init {
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        addView(idleView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(callView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        // A TextureView can drop its SurfaceTexture (for example while the app is in the background) and
        // offer a new one later: each player closes with its surface and opens again on the new one.
        idleView.surfaceTextureListener = SurfaceListener(::openIdle, ::closeIdle)
        callView.surfaceTextureListener = SurfaceListener(::openCall, ::closeCall)
    }

    /** Plays the loop while the page is resumed and the crow is on screen; pauses it otherwise. */
    fun setActive(value: Boolean) {
        if (active == value) return
        active = value
        if (value) {
            if (idlePrepared && !calling) idle?.start()
        } else {
            idle?.takeIf { idlePrepared && it.isPlaying }?.pause()
            if (calling) endCallNow()
        }
    }

    /** Starts the call; false when it cannot start now (already calling, not ready or inactive). */
    fun playCall(): Boolean {
        val player = call?.takeIf { callPrepared && active } ?: return false
        if (calling) return false
        calling = true
        val volume = if (crowCallMuted(audio?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL)) 0f else 1f
        player.setVolume(volume, volume)
        player.seekTo(0)
        player.start()
        // The first call waits for its first frame; later calls still show the shared first pose.
        if (callRendered) crossfadeIn()
        return true
    }

    fun release() {
        closeCall()
        closeIdle()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        applyCrop()
    }

    private fun openIdle(texture: SurfaceTexture) {
        closeIdle()
        val surface = Surface(texture).also { idleSurface = it }
        idle = open(surface, R.raw.fw_welcome_idle).apply {
            isLooping = true
            setVolume(0f, 0f)
            setOnPreparedListener {
                idlePrepared = true
                if (active && !calling) it.start()
            }
            setOnInfoListener { _, what, _ ->
                if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) idleView.animate().alpha(1f).setDuration(CROW_REVEAL_MS)
                false
            }
            setOnSeekCompleteListener { if (calling) returnToLoop() }
            prepareAsync()
        }
    }

    private fun openCall(texture: SurfaceTexture) {
        closeCall()
        val surface = Surface(texture).also { callSurface = it }
        call = open(surface, R.raw.fw_welcome_caw).apply {
            setOnPreparedListener { callPrepared = true }
            setOnInfoListener { _, what, _ ->
                if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                    callRendered = true
                    if (calling) crossfadeIn()
                }
                false
            }
            setOnCompletionListener {
                val loop = idle
                if (loop == null || !idlePrepared) endCallNow() else loop.seekTo(0)
            }
            prepareAsync()
        }
    }

    private fun closeIdle() {
        idleView.animate().cancel()
        idleView.alpha = 0f
        idle?.release()
        idle = null
        idlePrepared = false
        idleSurface?.release()
        idleSurface = null
    }

    private fun closeCall() {
        callView.animate().cancel()
        callView.alpha = 0f
        call?.release()
        call = null
        callPrepared = false
        callRendered = false
        callSurface?.release()
        callSurface = null
        if (calling) {
            calling = false
            if (active && idlePrepared) idle?.start()
        }
    }

    private fun open(surface: Surface, @RawRes res: Int): MediaPlayer {
        return context.rawPlayer(res).apply {
            setSurface(surface)
            setOnVideoSizeChangedListener { _, width, height ->
                clipWidth = width.toFloat()
                clipHeight = height.toFloat()
                applyCrop()
            }
            // A device that cannot decode a clip keeps the poster (or the loop); nothing depends on the video.
            setOnErrorListener { player, _, _ ->
                if (player === idle) {
                    idleView.alpha = 0f
                } else {
                    callView.alpha = 0f
                    if (calling && active && idlePrepared) idle?.start()
                }
                calling = false
                true
            }
        }
    }

    private fun crossfadeIn() {
        callView.animate().alpha(1f).setDuration(CROW_CROSSFADE_MS).withEndAction {
            idle?.takeIf { idlePrepared && it.isPlaying }?.pause()
        }
    }

    private fun returnToLoop() {
        if (active) idle?.start()
        callView.animate().alpha(0f).setDuration(CROW_CROSSFADE_MS).withEndAction { calling = false }
    }

    private fun endCallNow() {
        callView.animate().cancel()
        call?.takeIf { callPrepared && it.isPlaying }?.pause()
        callView.alpha = 0f
        calling = false
    }

    private fun applyCrop() {
        val crop = crowCropTransform(width.toFloat(), height.toFloat(), clipWidth, clipHeight)
        for (view in listOf(idleView, callView)) {
            view.setTransform(
                Matrix().apply {
                    setScale(crop[0], crop[1])
                    postTranslate(crop[2], crop[3])
                }
            )
        }
    }

    private class SurfaceListener(
        private val open: (SurfaceTexture) -> Unit,
        private val close: () -> Unit
    ) : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) = open(surface)
        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) = Unit
        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
            close()
            return true
        }
        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
    }
}

/** The call's sound alone, for reduced motion: the poster stays still and the crow is still heard. */
private class CrowCallSound(private val context: Context) {
    private var player: MediaPlayer? = null

    fun play(): Boolean {
        val audio = context.getSystemService(AudioManager::class.java)
        if (player != null || crowCallMuted(audio?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL)) return false
        player = context.rawPlayer(R.raw.fw_welcome_caw_sound).apply {
            setOnPreparedListener { it.start() }
            setOnCompletionListener { this@CrowCallSound.release() }
            setOnErrorListener { _, _, _ ->
                this@CrowCallSound.release()
                true
            }
            prepareAsync()
        }
        return true
    }

    fun release() {
        player?.release()
        player = null
    }
}

/** What the page's crow button drives: the video when it plays, otherwise the sound alone. */
@Stable
internal class WelcomeCrowState(context: Context) {
    internal var video: CrowVideoView? = null
    private val sound = CrowCallSound(context)

    fun call() {
        video?.playCall() ?: sound.play()
    }

    internal fun release() = sound.release()
}

@Composable
internal fun rememberWelcomeCrowState(): WelcomeCrowState {
    val context = LocalContext.current
    val state = remember(context) { WelcomeCrowState(context.applicationContext) }
    DisposableEffect(state) { onDispose(state::release) }
    return state
}

/**
 * The animated Yatagarasu behind the top of the welcome page (brand rollout spec, item 7): the idle
 * loop autoplays muted, aspect-filled and anchored to the top, and pauses while the page is in the
 * background or [onScreen] is false. Remove animations (animator scale 0) and the Reduced appearance
 * show the poster and never autoplay. Decorative: hidden from screen readers.
 */
@Composable
internal fun WelcomeCrowBackdrop(state: WelcomeCrowState, onScreen: Boolean, modifier: Modifier = Modifier) {
    val effects = LocalFwEffects.current
    val still = effects.reduceMotion || effects.tier == FwTier.Reduced
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var resumed by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> resumed = event.targetState.isAtLeast(Lifecycle.State.RESUMED) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    Box(modifier.clearAndSetSemantics { }) {
        // The loop's first pose: under the video until it renders, and alone under reduced motion.
        Image(
            painter = painterResource(R.drawable.fw_welcome_poster),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
            modifier = Modifier.fillMaxSize()
        )
        if (!still) {
            AndroidView(
                factory = { CrowVideoView(it).also { view -> state.video = view } },
                update = { it.setActive(resumed && onScreen) },
                onRelease = {
                    it.release()
                    if (state.video === it) state.video = null
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        // The sky fades into the brand ink behind the status bar instead of ending in a straight line.
        val ink = LocalFwColors.current.brandInk
        Box(
            Modifier
                .fillMaxWidth()
                .height(CROW_TOP_FADE)
                .background(Brush.verticalGradient(listOf(ink, ink.copy(alpha = 0f))))
        )
    }
}

private val CROW_TOP_FADE = 56.dp
