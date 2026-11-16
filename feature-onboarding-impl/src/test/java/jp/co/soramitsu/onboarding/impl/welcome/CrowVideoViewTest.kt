package jp.co.soramitsu.onboarding.impl.welcome

import android.app.Activity
import android.content.Context
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Looper
import android.view.TextureView
import androidx.test.core.app.ApplicationProvider
import java.time.Duration
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowMediaPlayer
import org.robolectric.shadows.ShadowMediaPlayer.State

@RunWith(RobolectricTestRunner::class)
class CrowVideoViewTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val players = mutableListOf<Pair<MediaPlayer, ShadowMediaPlayer>>()

    @Before
    fun setUp() {
        ShadowMediaPlayer.setMediaInfoProvider { ShadowMediaPlayer.MediaInfo(CLIP_MS, 0) }
        ShadowMediaPlayer.setCreateListener { player, shadow -> players += player to shadow }
        context.getSystemService(AudioManager::class.java).ringerMode = AudioManager.RINGER_MODE_NORMAL
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun idleFor(millis: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))

    private val CrowVideoView.idleView get() = getChildAt(0) as TextureView
    private val CrowVideoView.callView get() = getChildAt(1) as TextureView

    private fun TextureView.offerSurface() =
        surfaceTextureListener!!.onSurfaceTextureAvailable(SurfaceTexture(0), 1080, 1200)

    private fun TextureView.dropSurface(): Boolean =
        surfaceTextureListener!!.onSurfaceTextureDestroyed(SurfaceTexture(0))

    private class Crow(val view: CrowVideoView, val idle: ShadowMediaPlayer, val call: ShadowMediaPlayer)

    /** Both clips opened on their surfaces and prepared. */
    private fun preparedCrow(): Crow {
        val view = CrowVideoView(context)
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.setContentView(view)
        view.idleView.offerSurface()
        val idle = players.last().second
        view.callView.offerSurface()
        val call = players.last().second
        idle()
        return Crow(view, idle, call)
    }

    @Test
    fun idleLoopIsMutedLoopsAndPlaysOnlyWhileActive() {
        val crow = preparedCrow()
        assertEquals(2, players.size)
        assertTrue(players[0].first.isLooping)
        assertEquals(0f, crow.idle.leftVolume, 0f)
        assertEquals(State.PREPARED, crow.idle.state)

        crow.view.setActive(true)
        assertEquals(State.STARTED, crow.idle.state)
        crow.view.setActive(true)
        assertEquals(State.STARTED, crow.idle.state)

        crow.view.setActive(false)
        assertEquals(State.PAUSED, crow.idle.state)
    }

    @Test
    fun aLoopPreparedWhileActiveStartsByItself() {
        val view = CrowVideoView(context)
        view.setActive(true)
        view.idleView.offerSurface()
        val idle = players.single().second
        assertEquals(State.PREPARING, idle.state)

        idle()

        assertEquals(State.STARTED, idle.state)
    }

    @Test
    fun theCallNeedsAPreparedClipAndAnActiveView() {
        val view = CrowVideoView(context)
        assertFalse(view.playCall())

        val crow = preparedCrow()
        assertFalse(crow.view.playCall())
        crow.view.setActive(true)

        assertTrue(crow.view.playCall())
        assertTrue(crow.view.calling)
        assertEquals(State.STARTED, crow.call.state)
        assertEquals(1f, crow.call.leftVolume, 0f)
        assertFalse("A tap during a call is ignored", crow.view.playCall())
    }

    @Test
    fun aSilentPhoneHearsNoCall() {
        context.getSystemService(AudioManager::class.java).ringerMode = AudioManager.RINGER_MODE_VIBRATE
        val crow = preparedCrow()
        crow.view.setActive(true)

        assertTrue(crow.view.playCall())

        assertEquals(0f, crow.call.leftVolume, 0f)
    }

    @Test
    fun firstFramesRevealTheLoopAndCrossfadeIntoTheCall() {
        val crow = preparedCrow()
        crow.view.setActive(true)
        assertEquals(0f, crow.view.idleView.alpha, 0f)

        crow.idle.invokeInfoListener(MediaPlayer.MEDIA_INFO_BUFFERING_START, 0)
        idleFor(500)
        assertEquals(0f, crow.view.idleView.alpha, 0f)
        crow.idle.invokeInfoListener(MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START, 0)
        idleFor(500)
        assertEquals(1f, crow.view.idleView.alpha, 0f)

        crow.call.invokeInfoListener(MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START, 0)
        idleFor(500)
        assertEquals("No call is playing yet", 0f, crow.view.callView.alpha, 0f)

        assertTrue(crow.view.playCall())
        idleFor(500)
        assertEquals(1f, crow.view.callView.alpha, 0f)
        assertEquals("The loop pauses under the call", State.PAUSED, crow.idle.state)
    }

    @Test
    fun theFirstCallWaitsForItsFirstFrame() {
        val crow = preparedCrow()
        crow.view.setActive(true)
        assertTrue(crow.view.playCall())
        idleFor(500)
        assertEquals(0f, crow.view.callView.alpha, 0f)

        crow.call.invokeInfoListener(MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START, 0)
        idleFor(500)

        assertEquals(1f, crow.view.callView.alpha, 0f)
    }

    @Test
    fun theEndOfTheCallReturnsToTheRestartedLoop() {
        val crow = preparedCrow()
        crow.view.setActive(true)
        crow.view.playCall()

        crow.call.invokeCompletionListener()
        crow.idle.invokeSeekCompleteListener()
        idleFor(500)

        assertFalse(crow.view.calling)
        assertEquals(0f, crow.view.callView.alpha, 0f)
        assertEquals(State.STARTED, crow.idle.state)
    }

    @Test
    fun aCallWithoutALoopEndsAtOnce() {
        val view = CrowVideoView(context)
        view.callView.offerSurface()
        val call = players.single().second
        idle()
        view.setActive(true)
        assertTrue(view.playCall())

        call.invokeCompletionListener()

        assertFalse(view.calling)
        assertEquals(0f, view.callView.alpha, 0f)
    }

    @Test
    fun leavingThePageDuringACallEndsIt() {
        val crow = preparedCrow()
        crow.view.setActive(true)
        crow.view.playCall()

        crow.view.setActive(false)

        assertFalse(crow.view.calling)
        assertEquals(State.PAUSED, crow.call.state)
        assertEquals(0f, crow.view.callView.alpha, 0f)
    }

    @Test
    fun clipsThatCannotBeDecodedKeepThePoster() {
        val crow = preparedCrow()
        crow.view.setActive(true)
        crow.idle.invokeInfoListener(MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START, 0)
        idleFor(500)

        crow.idle.invokeErrorListener(MediaPlayer.MEDIA_ERROR_UNKNOWN, 0)
        assertEquals(0f, crow.view.idleView.alpha, 0f)

        assertTrue(crow.view.playCall())
        crow.call.invokeErrorListener(MediaPlayer.MEDIA_ERROR_UNKNOWN, 0)
        assertFalse(crow.view.calling)
        assertEquals(0f, crow.view.callView.alpha, 0f)
    }

    @Test
    fun lostSurfacesCloseTheirPlayersAndNewOnesReopenThem() {
        val crow = preparedCrow()
        crow.view.setActive(true)
        crow.view.playCall()

        assertTrue(crow.view.callView.dropSurface())
        assertEquals(State.END, crow.call.state)
        assertFalse(crow.view.calling)
        assertEquals(State.STARTED, crow.idle.state)

        crow.view.idleView.dropSurface()
        assertEquals(State.END, crow.idle.state)

        crow.view.idleView.offerSurface()
        val reopened = players.last().second
        crow.view.idleView.offerSurface()
        assertEquals("Offering a second surface replaces the first player", State.END, reopened.state)
        assertEquals(4, players.size)

        crow.view.release()
        assertEquals(State.END, players.last().second.state)
    }

    @Test
    fun videoIsAspectFilledAndAnchoredToTheTop() {
        val crow = preparedCrow()
        crow.view.layout(0, 0, 1080, 2400)
        assertArrayEquals(IDENTITY, crow.view.idleView.transformValues(), 1e-4f)

        val listener = MediaPlayer::class.java.getDeclaredField("mOnVideoSizeChangedListener")
            .apply { isAccessible = true }
            .get(players[0].first) as MediaPlayer.OnVideoSizeChangedListener
        listener.onVideoSizeChanged(players[0].first, 720, 1560)

        val crop = crowCropTransform(1080f, 2400f, 720f, 1560f)
        val expected = floatArrayOf(crop[0], 0f, crop[2], 0f, crop[1], crop[3], 0f, 0f, 1f)
        assertArrayEquals(expected, crow.view.idleView.transformValues(), 1e-3f)
        assertArrayEquals(expected, crow.view.callView.transformValues(), 1e-3f)
    }

    @Test
    fun crowButtonPlaysTheVideoCallWhenThereIsOne() {
        val state = WelcomeCrowState(context)
        val crow = preparedCrow()
        crow.view.setActive(true)
        state.video = crow.view

        state.call()

        assertTrue(crow.view.calling)
        assertEquals("No separate sound player", 2, players.size)
    }

    @Test
    fun withoutVideoTheCrowButtonPlaysTheSoundOnce() {
        val state = WelcomeCrowState(context)

        state.call()
        val sound = players.single().second
        idle()
        assertEquals(State.STARTED, sound.state)

        state.call()
        assertSame("A second tap while the call sounds is ignored", sound, players.single().second)

        sound.invokeCompletionListener()
        assertEquals(State.END, sound.state)
        state.call()
        assertEquals(2, players.size)

        players.last().second.invokeErrorListener(MediaPlayer.MEDIA_ERROR_UNKNOWN, 0)
        assertEquals(State.END, players.last().second.state)

        state.call()
        state.release()
        assertEquals(State.END, players.last().second.state)
    }

    @Test
    fun aSilentPhoneSkipsTheSound() {
        context.getSystemService(AudioManager::class.java).ringerMode = AudioManager.RINGER_MODE_SILENT
        val state = WelcomeCrowState(context)

        state.call()

        assertTrue(players.isEmpty())
        assertNull(state.video)
    }

    private fun TextureView.transformValues(): FloatArray = FloatArray(9).also { getTransform(Matrix()).getValues(it) }

    private companion object {
        const val CLIP_MS = 2_000
        val IDENTITY = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
    }
}
