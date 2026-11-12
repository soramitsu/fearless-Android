package jp.co.soramitsu.common.launch

import java.io.File
import jp.co.soramitsu.common.launch.LaunchIgniteDirector.Command
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LaunchIgniteDirectorTest {
    private val root = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
        .first { File(it, "design/fw.tokens.json").isFile }
    private val kitTimeline = File(root, "brand/yatagarasu/hero/launch-timeline.json").readText()
    private val timeline = LaunchTimeline.parse(kitTimeline)

    @Before
    fun freshProcess() = LaunchLanding.resetForTest()

    @Test
    fun shouldParseTheKitTimeline_whenTheCopyIsTheKitFile() {
        assertEquals(LaunchTimeline(2900, 300, 2000, 2000, 2750, 0.5f, 0.3292f), timeline)
        // The app's raw copy is the same file.
        assertEquals(kitTimeline, File(root, "app/src/main/res/raw/fw_launch_timeline.json").readText())
    }

    @Test(expected = IllegalArgumentException::class)
    fun shouldRefuseATimeline_whenItsTimesAreOutOfOrder() {
        LaunchTimeline.parse(kitTimeline.replace("\"holdSeconds\": 2", "\"holdSeconds\": 2.8"))
    }

    @Test
    fun shouldPlayThroughWithoutHolding_whenTheScreenIsAlreadyReady() {
        val director = LaunchIgniteDirector(timeline, 0)
        assertEquals(listOf(Command.Play), director.onPrepared(100))
        assertTrue(director.onFrame(1000, 1100, ready = true).isEmpty())
        assertTrue(director.onFrame(1990, 2090, ready = true).isEmpty())
        assertEquals(LaunchIgniteDirector.Phase.Playing, director.phase)
        val handOff = director.onFrame(2560, 2660, ready = true)
        assertEquals(2, handOff.size)
        val command = handOff[0] as Command.HandOff
        assertEquals(LaunchHandOff.CONTENT_FROM_SCALE, command.contentFromScale)
        assertEquals(LaunchHandOff.CONTENT_MS, command.contentMs)
        assertEquals(140L, command.overlayDelayMs)
        assertEquals(Command.Arrive, handOff[1])
        assertEquals(LaunchIgniteDirector.Phase.Leaving, director.phase)
        assertTrue(director.onFrame(2700, 2800, ready = true).isEmpty())
    }

    @Test
    fun shouldPauseOnTheHoldFrame_untilTheScreenIsReady() {
        val director = LaunchIgniteDirector(timeline, 0)
        director.onPrepared(0)
        assertTrue(director.onFrame(1900, 1900, ready = false).isEmpty())
        assertEquals(listOf(Command.Pause), director.onFrame(1970, 1970, ready = false))
        assertEquals(LaunchIgniteDirector.Phase.Holding, director.phase)
        assertTrue(director.onFrame(1970, 3000, ready = false).isEmpty())
        assertEquals(listOf(Command.Play), director.onFrame(1970, 3500, ready = true))
        // It never pauses a second time.
        assertTrue(director.onFrame(2010, 3540, ready = false).isEmpty())
        assertTrue(director.onFrame(2600, 4100, ready = false).first() is Command.HandOff)
    }

    @Test
    fun shouldLeaveTheHold_whenTheScreenNeverArrives() {
        val director = LaunchIgniteDirector(timeline, 0)
        director.onPrepared(0)
        assertEquals(listOf(Command.Pause), director.onFrame(1980, 2000, ready = false))
        assertTrue(director.onFrame(1980, 2000 + LaunchHandOff.HOLD_LIMIT_MS - 1, ready = false).isEmpty())
        assertEquals(listOf(Command.Play), director.onFrame(1980, 2000 + LaunchHandOff.HOLD_LIMIT_MS, ready = false))
    }

    @Test
    fun shouldSkipToTheZoom_whenTapped() {
        val director = LaunchIgniteDirector(timeline, 0)
        director.onPrepared(0)
        val commands = director.onTap()
        val handOff = commands[0] as Command.HandOff
        assertEquals(LaunchHandOff.SKIP_MS, handOff.contentMs)
        assertEquals(LaunchHandOff.SKIP_MS, handOff.overlayMs)
        assertEquals(0L, handOff.overlayDelayMs)
        assertEquals(Command.Arrive, commands[1])
        assertTrue(director.onTap().isEmpty())
    }

    @Test
    fun shouldDropTheClipWithAPlainFade_whenItCannotLoad() {
        val director = LaunchIgniteDirector(timeline, 0)
        assertTrue(director.onFrame(0, LaunchHandOff.STALL_MS - 1, ready = true).isEmpty())
        val fade = director.onFrame(0, LaunchHandOff.STALL_MS, ready = true).single() as Command.HandOff
        assertEquals(1f, fade.contentFromScale)
        assertEquals(0L, fade.contentMs)
        assertEquals(1f, fade.overlayToScale)
        assertEquals(LaunchHandOff.PLAIN_FADE_MS, fade.overlayMs)
    }

    @Test
    fun shouldGiveTheLoadItsOwnSecond_onceThePlayerStartsLoading() {
        val director = LaunchIgniteDirector(timeline, 0)
        director.onLoadStarted(800)
        assertTrue(director.onFrame(0, 1_799, ready = true).isEmpty())
        assertTrue(director.onFrame(0, 1_800, ready = true).single() is Command.HandOff)
        // A second report does not extend it.
        val again = LaunchIgniteDirector(timeline, 0)
        again.onLoadStarted(500)
        again.onLoadStarted(5_000)
        assertTrue(again.onFrame(0, 1_500, ready = true).single() is Command.HandOff)
    }

    @Test
    fun shouldDropTheClip_whenItStallsOrFails() {
        val stalled = LaunchIgniteDirector(timeline, 0)
        stalled.onPrepared(0)
        stalled.onFrame(500, 500, ready = true)
        assertTrue(stalled.onFrame(500, 1499, ready = true).isEmpty())
        assertTrue(stalled.onFrame(500, 1500, ready = true).single() is Command.HandOff)

        val failed = LaunchIgniteDirector(timeline, 0)
        failed.onPrepared(0)
        val fade = failed.onFailure().single() as Command.HandOff
        assertEquals(0L, fade.contentMs)
        assertTrue(failed.onFailure().isEmpty())
        assertTrue(failed.onTap().isEmpty())
    }

    @Test
    fun shouldPlayOncePerProcess_andReportReadinessOnce() {
        assertTrue(LaunchLanding.consumeClip())
        assertFalse(LaunchLanding.consumeClip())
        assertFalse(LaunchLanding.ready.value)
        LaunchLanding.screenReady()
        assertTrue(LaunchLanding.ready.value)
    }

    @Test
    fun shouldCentreTheFocus_underAspectFill() {
        // A 1080x2400 phone and the 720x1560 clip: the clip covers the view and the focus is centred horizontally.
        val placement = launchClipPlacement(1080f, 2400f, 720f, 1560f, 0.5f, 0.3292f)
        val scale = 2400f / 1560f
        assertEquals(720f * scale / 1080f, placement[0], 1e-4f)
        assertEquals(1f, placement[1], 1e-4f)
        assertEquals((1080f - 720f * scale) / 2f, placement[2], 1e-3f)
        assertEquals(0f, placement[3], 1e-3f)
        assertEquals(540f, placement[4], 1e-3f)
        assertEquals(0.3292f * 2400f, placement[5], 1e-2f)
        // The clip always covers the view, whatever the focus.
        val edge = launchClipPlacement(1080f, 2400f, 720f, 1560f, 0f, 0f)
        assertTrue(edge[2] <= 0f && edge[2] + edge[0] * 1080f >= 1080f)
    }
}
