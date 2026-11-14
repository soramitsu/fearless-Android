package jp.co.soramitsu.common.launch

import java.io.File
import jp.co.soramitsu.common.launch.LaunchIgniteDirector.Command
import jp.co.soramitsu.common.launch.LaunchIgniteDirector.DropReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    /** A director whose clip is prepared at [preparedAt] and shows its first frame at [firstFrameAt]. */
    private fun playing(preparedAt: Long = 0, firstFrameAt: Long = 50): LaunchIgniteDirector =
        LaunchIgniteDirector(timeline, 0).also {
            it.onPrepared(preparedAt)
            assertEquals(listOf(Command.RevealClip), it.onClipFrame(firstFrameAt))
        }

    private fun assertPlainHandOff(commands: List<Command>) {
        val handOff = commands.single() as Command.HandOff
        assertEquals(1f, handOff.contentFromScale)
        assertEquals(0L, handOff.contentMs)
        assertEquals(1f, handOff.overlayToScale)
        assertEquals(LaunchHandOff.PLAIN_FADE_MS, handOff.overlayMs)
    }

    @Test
    fun shouldParseTheKitTimeline_whenTheCopyIsTheKitFile() {
        assertEquals(LaunchTimeline(2900, 300, 2000, 2000, 2750, 0.5f, 0.3292f), timeline)
        assertEquals(kitTimeline, File(root, "app/src/main/res/raw/fw_launch_timeline.json").readText())
    }

    @Test(expected = IllegalArgumentException::class)
    fun shouldRefuseATimeline_whenItsTimesAreOutOfOrder() {
        LaunchTimeline.parse(kitTimeline.replace("\"holdSeconds\": 2", "\"holdSeconds\": 2.8"))
    }

    @Test
    fun shouldRevealTheClip_onlyWhenItsFirstFrameIsOnScreen() {
        val director = LaunchIgniteDirector(timeline, 1_000)
        assertTrue(director.onClipFrame(1_100).isEmpty()) // not prepared: a stray frame changes nothing
        assertEquals(listOf(Command.Play), director.onPrepared(1_200))
        assertEquals(listOf(Command.RevealClip), director.onClipFrame(1_450))
        assertTrue(director.onClipFrame(1_480).isEmpty())
        assertEquals(450L, director.firstFrameAfterMs)
        assertNull(director.dropReason)
    }

    @Test
    fun shouldPlayThroughWithoutHolding_whenTheScreenIsAlreadyReady() {
        val director = playing(preparedAt = 100, firstFrameAt = 150)
        assertTrue(director.onFrame(1000, 1100, ready = true).isEmpty())
        assertTrue(director.onFrame(1990, 2090, ready = true).isEmpty())
        val handOff = director.onFrame(2560, 2660, ready = true)
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
        val director = playing()
        assertTrue(director.onFrame(1900, 1900, ready = false).isEmpty())
        assertEquals(listOf(Command.Pause), director.onFrame(1970, 1970, ready = false))
        assertTrue(director.onFrame(1970, 3000, ready = false).isEmpty())
        assertEquals(listOf(Command.Play), director.onFrame(1970, 3500, ready = true))
        assertTrue(director.onFrame(2010, 3540, ready = false).isEmpty())
        assertTrue(director.onFrame(2600, 4100, ready = false).first() is Command.HandOff)
    }

    @Test
    fun shouldLeaveTheHold_whenTheScreenNeverArrives() {
        val director = playing()
        assertEquals(listOf(Command.Pause), director.onFrame(1980, 2000, ready = false))
        assertTrue(director.onFrame(1980, 2000 + LaunchHandOff.HOLD_LIMIT_MS - 1, ready = false).isEmpty())
        assertEquals(listOf(Command.Play), director.onFrame(1980, 2000 + LaunchHandOff.HOLD_LIMIT_MS, ready = false))
    }

    @Test
    fun shouldSkipToTheZoom_whenTappedOverAReadyScreen() {
        val director = playing()
        val commands = director.onTap(500, ready = true)
        val handOff = commands[0] as Command.HandOff
        assertEquals(LaunchHandOff.SKIP_MS, handOff.contentMs)
        assertEquals(0L, handOff.overlayDelayMs)
        assertEquals(Command.Arrive, commands[1])
        assertTrue(director.onTap(600, ready = true).isEmpty())
    }

    @Test
    fun shouldCoverUntilReady_whenTappedBeforeTheScreenIsThere() {
        val director = playing()
        assertEquals(listOf(Command.Cover(DropReason.Skipped)), director.onTap(500, ready = false))
        assertTrue(director.onFrame(600, 900, ready = false).isEmpty())
        assertPlainHandOff(director.onFrame(600, 1200, ready = true))
    }

    @Test
    fun shouldKeepTheArtUntilTheScreenIsReady_whenTheClipCannotLoad() {
        val director = LaunchIgniteDirector(timeline, 0)
        assertTrue(director.onFrame(0, LaunchHandOff.STALL_MS - 1, ready = false).isEmpty())
        assertEquals(listOf(Command.Cover(DropReason.NotStarted)), director.onFrame(0, LaunchHandOff.STALL_MS, ready = false))
        assertEquals(DropReason.NotStarted, director.dropReason)
        // The art stays over the host while its first screen is not ready: never an empty screen.
        assertTrue(director.onFrame(0, 3_000, ready = false).isEmpty())
        assertTrue(director.onFrame(0, 6_000, ready = false).isEmpty())
        assertPlainHandOff(director.onFrame(0, 6_100, ready = true))
        assertTrue(director.onFrame(0, 6_200, ready = true).isEmpty())
    }

    @Test
    fun shouldGiveTheLoadItsOwnSecond_onceThePlayerStartsLoading() {
        val director = LaunchIgniteDirector(timeline, 0)
        director.onLoadStarted(800)
        assertTrue(director.onFrame(0, 1_799, ready = true).isEmpty())
        assertEquals(listOf(Command.Cover(DropReason.NotStarted)), director.onFrame(0, 1_800, ready = true))
        val again = LaunchIgniteDirector(timeline, 0)
        again.onLoadStarted(500)
        again.onLoadStarted(5_000)
        assertTrue(again.onFrame(0, 1_500, ready = true).single() is Command.Cover)
    }

    @Test
    fun shouldDrop_whenThePlayerRunsButNoFrameReachesTheScreen() {
        val director = LaunchIgniteDirector(timeline, 0)
        director.onPrepared(100)
        assertTrue(director.onFrame(400, 1_099, ready = true).isEmpty())
        assertEquals(listOf(Command.Cover(DropReason.NotStarted)), director.onFrame(900, 1_100, ready = true))
        assertTrue(director.onClipFrame(1_200).isEmpty()) // too late: the art stays
        assertPlainHandOff(director.onFrame(1_000, 1_200, ready = true))
    }

    @Test
    fun shouldDrop_whenTheClipStallsOrFails() {
        val stalled = playing()
        stalled.onFrame(500, 500, ready = true)
        assertTrue(stalled.onFrame(500, 1499, ready = true).isEmpty())
        assertEquals(listOf(Command.Cover(DropReason.Stalled)), stalled.onFrame(500, 1500, ready = true))

        val failed = playing()
        assertEquals(listOf(Command.Cover(DropReason.PlayerError)), failed.onFailure(DropReason.PlayerError, 700))
        assertTrue(failed.onFailure(DropReason.SurfaceLost, 800).isEmpty())
        assertTrue(failed.onTap(900, ready = true).isEmpty())
        assertPlainHandOff(failed.onFrame(0, 1_000, ready = true))
    }

    @Test
    fun shouldStopCovering_afterItsLimit_whenTheScreenNeverArrives() {
        val director = LaunchIgniteDirector(timeline, 0)
        director.onFailure(DropReason.Background, 100)
        assertTrue(director.onFrame(0, 100 + LaunchHandOff.COVER_LIMIT_MS - 1, ready = false).isEmpty())
        assertPlainHandOff(director.onFrame(0, 100 + LaunchHandOff.COVER_LIMIT_MS, ready = false))
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
        val placement = launchClipPlacement(1080f, 2400f, 720f, 1560f, 0.5f, 0.3292f)
        val scale = 2400f / 1560f
        assertEquals(720f * scale / 1080f, placement[0], 1e-4f)
        assertEquals(1f, placement[1], 1e-4f)
        assertEquals((1080f - 720f * scale) / 2f, placement[2], 1e-3f)
        assertEquals(0f, placement[3], 1e-3f)
        assertEquals(540f, placement[4], 1e-3f)
        assertEquals(0.3292f * 2400f, placement[5], 1e-2f)
        val edge = launchClipPlacement(1080f, 2400f, 720f, 1560f, 0f, 0f)
        assertTrue(edge[2] <= 0f && edge[2] + edge[0] * 1080f >= 1080f)
    }
}
