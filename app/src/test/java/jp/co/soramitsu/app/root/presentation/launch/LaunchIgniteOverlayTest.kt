package jp.co.soramitsu.app.root.presentation.launch

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.SurfaceTexture
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import java.time.Duration
import jp.co.soramitsu.app.HiltShimApplication
import jp.co.soramitsu.app.HiltShimHostActivity
import jp.co.soramitsu.app.R
import jp.co.soramitsu.common.launch.LaunchFlightStage
import jp.co.soramitsu.common.launch.LaunchLanding
import jp.co.soramitsu.common.launch.LaunchTimeline
import jp.co.soramitsu.common.shikiban.Shikiban
import org.junit.After
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
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowMediaPlayer
import org.robolectric.shadows.ShadowSystemClock

/** The cold-start clip over the first screen: every way it hands the screen over leaves it shown and untransformed. */
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltShimApplication::class)
class LaunchIgniteOverlayTest {
    private val activity = Robolectric.buildActivity(HiltShimHostActivity::class.java).setup().get()
    private val parent = activity.findViewById<ViewGroup>(android.R.id.content)
    private val content = FrameLayout(activity)
    private var ready = false
    private var arrivals = 0
    private var finishes = 0
    private lateinit var overlay: LaunchIgniteOverlay

    @Before
    fun setUp() {
        LaunchFlightStage.resetForTest()
        LaunchLanding.resetForTest()
        parent.addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        overlay = LaunchIgniteOverlay(
            context = activity,
            timeline = activity.resources.openRawResource(R.raw.fw_launch_timeline).bufferedReader().use { LaunchTimeline.parse(it.readText()) },
            tokens = requireNotNull(Shikiban.load(activity)),
            content = content,
            isReady = { ready },
            onArrive = { arrivals++ },
            onFinished = { finishes++ }
        )
        parent.addView(overlay, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        parent.layout(0, 0, 720, 1560)
        overlay.layout(0, 0, 720, 1560)
        content.layout(0, 0, 720, 1560)
    }

    @After
    fun tearDown() {
        ShadowMediaPlayer.resetStaticState()
        LaunchFlightStage.resetForTest()
        LaunchLanding.resetForTest()
    }

    @Test
    fun theScreenWaitsHiddenAndTheOverlayIsDecorative() {
        assertEquals(0f, content.alpha)
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS, overlay.importantForAccessibility)
        assertSame(parent, overlay.parent)
    }

    @Test
    fun aTapOnAReadyScreenFliesItInAndLeavesNoTransform() {
        ready = true
        overlay.performClick()
        assertEquals("A second tap speeds the running flight up", true, overlay.performClick())
        draw()
        fly()
        assertEquals(1, arrivals)
        assertEquals(1, finishes)
        assertNull(overlay.parent)
        assertEquals(1f, content.alpha)
        assertEquals(1f, content.scaleX)
        assertEquals(1f, content.scaleY)
    }

    @Test
    fun aTapBeforeTheScreenIsReadyCoversItThenFadesToIt() {
        overlay.performClick()
        overlay.doFrame(0)
        assertEquals(0f, content.alpha)
        ready = true
        overlay.doFrame(0)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))
        assertEquals(1, finishes)
        assertEquals(0, arrivals)
        assertEquals(1f, content.alpha)
        assertNull(overlay.parent)
    }

    @Test
    fun aClipThatCannotPlayIsDroppedAndBackgroundingAlsoDropsIt() {
        overlay.onSurfaceTextureAvailable(SurfaceTexture(0), 720, 1560)
        overlay.onSurfaceTextureSizeChanged(SurfaceTexture(0), 720, 1560)
        overlay.onSurfaceTextureUpdated(SurfaceTexture(0))
        overlay.dispatchWindowVisibilityChanged(View.GONE)
        assertTrue(overlay.onSurfaceTextureDestroyed(SurfaceTexture(0)))
        ready = true
        overlay.doFrame(0)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))
        assertEquals(1, finishes)
        assertEquals(1f, content.alpha)
    }

    @Test
    fun aPlayingClipRevealsItselfAndFliesAtItsZoomPoint() {
        ShadowMediaPlayer.setMediaInfoProvider { ShadowMediaPlayer.MediaInfo(5_000, 0) }
        ready = true
        overlay.onSurfaceTextureAvailable(SurfaceTexture(0), 720, 1560)
        shadowOf(Looper.getMainLooper()).idle()
        overlay.onSurfaceTextureUpdated(SurfaceTexture(0))
        repeat(400) {
            if (finishes > 0) return@repeat
            ShadowSystemClock.advanceBy(Duration.ofMillis(20))
            shadowOf(Looper.getMainLooper()).idle()
            overlay.doFrame(0)
        }
        assertEquals(1, finishes)
        assertEquals(1f, content.alpha)
        assertNull(overlay.parent)
    }

    @Test
    fun removingTheOverlayEarlyNeverLeavesTheScreenHidden() {
        ready = true
        overlay.performClick()
        parent.removeView(overlay)
        assertEquals(1f, content.alpha)
        assertEquals(1f, content.scaleX)
        assertEquals(0, finishes)
    }

    @Test
    fun clearingThePivotReturnsToTheCentre() {
        content.pivotX = 3f
        content.pivotY = 4f
        assertTrue(content.isPivotSet)
        content.clearPivot()
        assertFalse("Back to the default, centred pivot", content.isPivotSet)
    }

    private fun fly() {
        repeat(600) {
            if (finishes > 0) return
            ShadowSystemClock.advanceBy(Duration.ofMillis(16))
            overlay.doFrame(0)
            if (it % 20 == 0) draw()
        }
    }

    private fun draw() {
        val bitmap = Bitmap.createBitmap(720, 1560, Bitmap.Config.ARGB_8888)
        overlay.draw(Canvas(bitmap))
    }
}
