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
        assertEquals(listOf(Command.PlainFade(LaunchHandOff.PLAIN_FADE_MS)), commands)
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
    fun shouldStartTheFlightAtTheDive_whenTheScreenIsAlreadyReady() {
        val director = playing(preparedAt = 100, firstFrameAt = 150)
        assertTrue(director.onFrame(1000, 1100, ready = true).isEmpty())
        assertTrue(director.onFrame(1990, 2090, ready = true).isEmpty())
        // SPEC v4 part 1: the flight starts at the clip's zoomStartSeconds (2.0 s), replacing v3's 2.55 s zoom.
        assertEquals(listOf(Command.Flight(skipped = false)), director.onFrame(2010, 2110, ready = true))
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
        // The flight starts as soon as the released clip reaches its dive (SPEC v4 part 1: "or whenever the hold releases").
        assertEquals(listOf(Command.Flight(skipped = false)), director.onFrame(2010, 3540, ready = false))
        assertTrue(director.onFrame(2040, 4100, ready = false).isEmpty())
    }

    @Test
    fun shouldLeaveTheHold_whenTheScreenNeverArrives() {
        val director = playing()
        assertEquals(listOf(Command.Pause), director.onFrame(1980, 2000, ready = false))
        assertTrue(director.onFrame(1980, 2000 + LaunchHandOff.HOLD_LIMIT_MS - 1, ready = false).isEmpty())
        assertEquals(listOf(Command.Play), director.onFrame(1980, 2000 + LaunchHandOff.HOLD_LIMIT_MS, ready = false))
    }

    @Test
    fun shouldStartTheFlightSkipped_whenTappedBeforeIt_overAReadyScreen() {
        val director = playing()
        assertEquals(listOf(Command.Flight(skipped = true)), director.onTap(500, ready = true))
        // Already at the skip speed: a second tap changes nothing.
        assertTrue(director.onTap(600, ready = true).isEmpty())
        assertTrue(director.onFrame(2100, 700, ready = true).isEmpty())
    }

    @Test
    fun shouldSpeedUpTheRestOfTheFlight_whenTappedDuringIt() {
        val director = playing()
        assertEquals(listOf(Command.Flight(skipped = false)), director.onFrame(2000, 2000, ready = true))
        assertEquals(listOf(Command.SkipFlight), director.onTap(2300, ready = true))
        assertTrue(director.onTap(2400, ready = true).isEmpty())
    }

    @Test
    fun shouldPlayTheFlightOnItsOwnClock_andTheSkipAtTwoAndAHalfTimes() {
        val clock = LaunchFlightClock(durationMs = 1270, skipMs = 510, startedAtMs = 10_000, skipped = false)
        assertEquals(0f, clock.elapsed(9_000), 0f)
        assertEquals(500f, clock.elapsed(10_500), 0f)
        clock.skip(10_500)
        // The rest (770 ms of flight) plays at 1270 / 510 the speed: no jump at the tap.
        assertEquals(500f, clock.elapsed(10_500), 1e-3f)
        assertEquals(500f + 100f * 1270f / 510f, clock.elapsed(10_600), 1e-2f)
        assertTrue(clock.finished(10_500 + 310))
        assertEquals(1270f, clock.elapsed(20_000), 0f)
        val skipped = LaunchFlightClock(1270, 510, 0, skipped = true)
        assertTrue(skipped.skipped)
        assertTrue(!skipped.finished(509) && skipped.finished(510))
        // The launchArrive touch lands 1000 ms into the flight (SPEC v4 part 1, "Touch").
        assertEquals(1000, LaunchHandOff.FLIGHT_ARRIVE_MS)
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
    fun shouldShowNoFlight_underReducedMotionOrReducedEffects() {
        val full = jp.co.soramitsu.common.compose.theme.fw.FwEffects(jp.co.soramitsu.common.compose.theme.fw.FwTier.Full, highContrast = false, reduceMotion = false)
        assertTrue(launchMotionAllowed(animatorsEnabled = true, effects = full))
        assertFalse(launchMotionAllowed(animatorsEnabled = false, effects = full))
        assertFalse(launchMotionAllowed(true, full.copy(reduceMotion = true)))
        assertFalse(launchMotionAllowed(true, full.copy(tier = jp.co.soramitsu.common.compose.theme.fw.FwTier.Reduced)))
        // High contrast alone selects the Reduced paint tier but keeps the launch, as in kit v3.
        assertTrue(launchMotionAllowed(true, full.copy(tier = jp.co.soramitsu.common.compose.theme.fw.FwTier.Reduced, highContrast = true)))
        assertTrue(launchMotionAllowed(true, full.copy(tier = jp.co.soramitsu.common.compose.theme.fw.FwTier.Lite)))
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
