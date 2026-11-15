package jp.co.soramitsu.common.feedback

import java.io.File
import java.math.BigDecimal
import jp.co.soramitsu.common.compose.theme.fw.fwDisplayedNumber
import jp.co.soramitsu.common.shikiban.Shikiban
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The seal draws only what happened (brand kit SPEC v4 part 3), on the kit's timing. */
class TransferSealTest {
    private val root = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
        .first { File(it, "design/fw.tokens.json").isFile }
    private val tokens = Shikiban.parse(File(root, "common/src/main/res/raw/fw_shikiban.json").readText())
    private val timeline = tokens.sealTimeline()

    @Before
    fun fresh() = TransferSeal.clear()

    @Test
    fun shouldDrawEachStageOverItsTime_andWaitWithTheCometBetweenStages() {
        TransferSeal.ready(1_000)
        val record = TransferSeal.record.value
        assertEquals(TransferSeal.Stage.Ready, record.stage)
        // Both rings draw over 360 ms with easing.settle; then the figure waits for the next stage.
        assertEquals(0f, timeline.frame(record, 1_000).q, 0f)
        val half = timeline.frame(record, 1_180)
        assertEquals(tokens.settle(0.5f), half.q, 1e-4f)
        assertFalse(half.waiting)
        val waiting = timeline.frame(record, 2_000)
        assertEquals(1f, waiting.q, 0f)
        assertTrue(waiting.waiting)
        assertEquals(0f, waiting.broken, 0f)
    }

    @Test
    fun shouldLightIndistinguishableStagesTogether_whenAcceptanceArrives() {
        TransferSeal.ready(0)
        TransferSeal.accepted(1_000)
        val record = TransferSeal.record.value
        assertEquals(1_000L, record.signedAtMs)
        assertEquals(1_000L, record.sentAtMs)
        assertEquals(TransferSeal.Stage.Accepted, record.stage)
        val windows = timeline.strokeWindows(record)
        // signed, sent and accepted were one event: their three sides draw together, in 180 ms.
        assertEquals(1_000L to 1_180L, windows[1])
        assertEquals(windows[1], windows[2])
        assertEquals(windows[1], windows[3])
        assertEquals(4f, timeline.frame(record, 1_180).q, 1e-5f)
        // Then the ignite runs from 0.41 to 1 over 720 ms and rests at a quarter.
        assertEquals(0.41f, timeline.frame(record, 1_180).progress, 1e-5f)
        assertEquals(1f, timeline.frame(record, 1_180 + 720).progress, 1e-5f)
        assertEquals(0.25f, timeline.frame(record, 1_180 + 720 + 700).strength, 1e-5f)
        assertTrue(timeline.frame(record, 5_000).complete)
        assertEquals(1_180L + 720 + 700, timeline.restsAtMs(record))
    }

    @Test
    fun shouldDrawTheNextStageQuickly_whenItIsAlreadyKnown() {
        TransferSeal.ready(0)
        TransferSeal.signed(100) // learnt while the rings draw: its side waits for them
        TransferSeal.sent(200) // already known when the signed side starts: that side takes 180 ms
        val windows = timeline.strokeWindows(TransferSeal.record.value)
        assertEquals(0L to 360L, windows[0])
        assertEquals(360L to 540L, windows[1])
        assertEquals(540L to 900L, windows[2])
        assertNull(windows[3])
    }

    @Test
    fun shouldBreak_onFailure_andKeepTheStageReached() {
        TransferSeal.ready(0)
        TransferSeal.failed(200)
        val record = TransferSeal.record.value
        assertEquals(TransferSeal.Stage.Failed, record.stage)
        val frame = timeline.frame(record, 200 + 255)
        assertEquals(0.5f, frame.broken, 1e-3f)
        assertEquals(tokens.settle(200f / 360f), frame.q, 1e-3f)
        assertFalse(frame.waiting)
        assertEquals(1f, timeline.frame(record, 10_000).broken, 0f)
        // Nothing after a failure changes it.
        TransferSeal.accepted(300)
        assertNull(TransferSeal.record.value.acceptedAtMs)
    }

    @Test
    fun shouldShowNothing_whenAValidationStopsItBeforeSigning() {
        TransferSeal.ready(0)
        TransferSeal.cancelIfPending()
        assertEquals(TransferSeal.Stage.Idle, TransferSeal.record.value.stage)
        assertEquals(SealFrame.Idle, timeline.frame(TransferSeal.record.value, 1_000))
        TransferSeal.ready(0)
        TransferSeal.accepted(10)
        TransferSeal.cancelIfPending()
        assertEquals(TransferSeal.Stage.Accepted, TransferSeal.record.value.stage)
        assertEquals(10L, TransferSeal.recentAccepted(20)?.acceptedAtMs)
        assertNull(TransferSeal.recentAccepted(10 + TransferMoments.DEFAULT_WINDOW_MS + 1))
    }

    @Test
    fun shouldTrackASubmission_readyWhileItRuns_acceptedTogetherOnSuccess_andReturnTheResultAsIs() = kotlinx.coroutines.runBlocking {
        var stageWhileRunning: TransferSeal.Stage? = null
        val result = TransferSeal.track { stageWhileRunning = TransferSeal.record.value.stage; Result.success("0xhash") }
        assertEquals(TransferSeal.Stage.Ready, stageWhileRunning)
        assertEquals(Result.success("0xhash"), result)
        val record = TransferSeal.record.value
        assertEquals(TransferSeal.Stage.Accepted, record.stage)
        assertEquals(record.acceptedAtMs, record.signedAtMs)
        assertEquals(record.acceptedAtMs, record.sentAtMs)
    }

    @Test
    fun shouldBreakOnlyOnADefiniteFailure_andClearOnAnUnknownOrCancelledOne() = kotlinx.coroutines.runBlocking {
        val refused = IllegalStateException("refused")
        assertEquals(Result.failure<String>(refused), TransferSeal.track<String> { Result.failure(refused) })
        assertEquals(TransferSeal.Stage.Failed, TransferSeal.record.value.stage)
        TransferSeal.track<String>(definiteFailure = { false }) { Result.failure(refused) }
        assertEquals(TransferSeal.Stage.Idle, TransferSeal.record.value.stage)
        TransferSeal.track<String> { Result.failure(kotlinx.coroutines.CancellationException("gone")) }
        assertEquals(TransferSeal.Stage.Idle, TransferSeal.record.value.stage)
        val thrown = runCatching { TransferSeal.track<String> { throw IllegalArgumentException("threw") } }
        assertTrue(thrown.isFailure)
        assertEquals(TransferSeal.Stage.Idle, TransferSeal.record.value.stage)
    }

    @Test
    fun shouldHandTheAcceptedSealToOneSuccessScreenOnly() {
        TransferSeal.ready(0)
        TransferSeal.accepted(10)
        assertEquals(10L, TransferSeal.consumeRecentAccepted(20)?.acceptedAtMs)
        assertNull(TransferSeal.consumeRecentAccepted(30))
        TransferSeal.ready(0)
        TransferSeal.failed(10)
        assertNull(TransferSeal.consumeRecentAccepted(20))
    }

    @Test
    fun shouldReadTheDirectionOfADisplayedValue() {
        assertEquals(BigDecimal("1234.56"), fwDisplayedNumber("1,234.56 DOT"))
        assertEquals(BigDecimal("0.0861901"), fwDisplayedNumber("$0.0861901"))
        assertEquals(BigDecimal("1234.5"), fwDisplayedNumber("1.234,5 €"))
        assertEquals(BigDecimal("0.5"), fwDisplayedNumber("0,5"))
        assertEquals(BigDecimal("-3.98"), fwDisplayedNumber("-3.98%"))
        assertNull(fwDisplayedNumber("—"))
        assertNull(fwDisplayedNumber("Price unavailable"))
    }
}
