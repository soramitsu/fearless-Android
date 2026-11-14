package jp.co.soramitsu.common.feedback

import java.io.File
import java.math.BigDecimal
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

private const val FORMAT_ARGUMENT = "%1\$s"

class TransferFeedbackTest {
    private val root = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
        .first { File(it, "design/fw.tokens.json").isFile }

    // ---- Haptic patterns: the kit file, copied byte for byte into common/raw.

    @Test
    fun shouldReadEveryPattern_whenParsingTheKitFile() {
        val kit = File(root, "brand/yatagarasu/tokens/haptics.json").readText()
        assertEquals(kit, File(root, "common/src/main/res/raw/fw_haptics.json").readText())
        val patterns = HapticPatterns.parse(kit)
        assertEquals(setOf("send", "receive", "launchArrive", "commit", "failure"), patterns.patterns.keys)
        val send = patterns["send"]!!
        assertEquals(listOf(HapticPrimitive("QUICK_RISE", 0.7f, 0), HapticPrimitive("CLICK", 1f, 40)), send.primitives)
        assertArrayEquals(longArrayOf(0, 90, 90, 80, 30, 26), send.waveform!!.timingsMs)
        assertArrayEquals(intArrayOf(0, 40, 90, 150, 0, 255), send.waveform!!.amplitudes)
        assertTrue(patterns["failure"]!!.platformOnly)
        for (name in listOf(TransferFeedbackRules.PATTERN_COMMIT, TransferFeedbackRules.PATTERN_SEND, TransferFeedbackRules.PATTERN_RECEIVE,
            TransferFeedbackRules.PATTERN_LAUNCH_ARRIVE)) {
            assertTrue(name, patterns[name]!!.waveform != null)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun shouldRefuseAPattern_whenItsWaveformLengthsDiffer() {
        HapticPatterns.parse("""{"formatVersion":1,"patterns":{"x":{"android":{"waveform":{"timingsMs":[0,10],"amplitudes":[0]}}}}}""")
    }

    @Test(expected = IllegalArgumentException::class)
    fun shouldRefuseAPattern_whenAPrimitiveIsUnknown() {
        HapticPatterns.parse("""{"formatVersion":1,"patterns":{"x":{"android":{"primitives":[{"id":"BUZZ","scale":1,"delayMs":0}]}}}}""")
    }

    @Test
    fun shouldKeepTheRhythm_whenTheVibratorHasNoAmplitudeControl() {
        val send = HapticWaveform(longArrayOf(0, 90, 90, 80, 30, 26), intArrayOf(0, 40, 90, 150, 0, 255))
        assertArrayEquals(longArrayOf(0, 260, 30, 26), send.onOffTimings())
        val receive = HapticWaveform(longArrayOf(30, 22, 138, 28), intArrayOf(0, 130, 0, 255))
        assertArrayEquals(longArrayOf(30, 22, 138, 28), receive.onOffTimings())
    }

    // ---- Rules: which cue plays what, and when the device or the user says no.

    @Test
    fun shouldPlaySoundAndTouch_whenBothAreOnAndTheRingerIsNormal() {
        assertEquals(FeedbackPlan(FeedbackSound.Send, "send"), TransferFeedbackRules.plan(FeedbackCue.Sent, true, true, true, true))
        assertEquals(FeedbackPlan(FeedbackSound.Receive, "receive"), TransferFeedbackRules.plan(FeedbackCue.Received, true, true, true, true))
        assertEquals(FeedbackPlan(null, "commit"), TransferFeedbackRules.plan(FeedbackCue.Commit, true, true, true, true))
        assertEquals(FeedbackPlan(null, "commit"), TransferFeedbackRules.plan(FeedbackCue.Copied, true, true, true, true))
        assertEquals(FeedbackPlan(null, "failure"), TransferFeedbackRules.plan(FeedbackCue.Failed, true, true, true, true))
        assertEquals(FeedbackPlan(null, "launchArrive"), TransferFeedbackRules.plan(FeedbackCue.LaunchArrive, true, true, true, true))
    }

    @Test
    fun shouldStaySilent_whenTheRingerIsSilentOrTheSwitchIsOff() {
        assertEquals(FeedbackPlan(null, "send"), TransferFeedbackRules.plan(FeedbackCue.Sent, true, true, ringerNormal = false, systemTouchFeedback = true))
        assertEquals(FeedbackPlan(null, "receive"), TransferFeedbackRules.plan(FeedbackCue.Received, soundOn = false, hapticsOn = true, ringerNormal = true, systemTouchFeedback = true))
    }

    @Test
    fun shouldNotVibrate_whenTheUserOrTheSystemTurnedTouchOff() {
        assertEquals(FeedbackPlan(FeedbackSound.Send, null), TransferFeedbackRules.plan(FeedbackCue.Sent, true, hapticsOn = false, ringerNormal = true, systemTouchFeedback = true))
        assertEquals(FeedbackPlan(FeedbackSound.Send, null), TransferFeedbackRules.plan(FeedbackCue.Sent, true, true, true, systemTouchFeedback = false))
        assertEquals(FeedbackPlan(null, null), TransferFeedbackRules.plan(FeedbackCue.Commit, true, false, true, true))
    }

    @Test
    fun shouldNeverMakeASound_forConfirmationsFailuresOrTheLaunch() {
        for (cue in listOf(FeedbackCue.Commit, FeedbackCue.Copied, FeedbackCue.Failed, FeedbackCue.LaunchArrive)) {
            assertNull(cue.name, TransferFeedbackRules.plan(cue, true, true, true, true).sound)
        }
    }

    // ---- Preferences: both switches on by default; ordinary storage.

    @Test
    fun shouldTurnBothOn_whenNothingWasStored() = runBlocking {
        val store = MemoryStore()
        val preferences = FeedbackPreferences(store)
        assertEquals(FeedbackSettings(soundEffects = true, hapticFeedback = true), preferences.read())
        assertTrue(preferences.setSoundEffects(false))
        assertEquals(FeedbackSettings(soundEffects = false, hapticFeedback = true), preferences.observe().first())
        assertTrue(preferences.setHapticFeedback(false))
        assertEquals(FeedbackSettings(soundEffects = false, hapticFeedback = false), preferences.read())
        assertEquals(mapOf(FeedbackPreferences.SOUND to false, FeedbackPreferences.HAPTICS to false), store.values)
        assertEquals("fw_feedback", FeedbackPreferences.STORE)
    }

    @Test
    fun shouldReportAFailure_whenTheValueCannotBeStored() {
        val preferences = FeedbackPreferences(MemoryStore(writable = false))
        assertFalse(preferences.setSoundEffects(false))
        assertTrue(preferences.read().soundEffects)
    }

    // ---- The success screen's hand-off.

    @Test
    fun shouldIgniteOnce_forASuccessScreenRightAfterASend() {
        TransferMoments.markSent(1_000)
        assertTrue(TransferMoments.consumeRecentSend(1_500))
        assertFalse(TransferMoments.consumeRecentSend(1_600))
        TransferMoments.markSent(1_000)
        assertFalse(TransferMoments.consumeRecentSend(1_000 + TransferMoments.DEFAULT_WINDOW_MS + 1))
        assertFalse(TransferMoments.consumeRecentSend(5_000))
    }

    // ---- Strings: every shipped locale names both switches and the arrival message.

    @Test
    fun shouldTranslateEveryFeedbackString_inEveryShippedLocale() {
        val res = File(root, "common/src/main/res")
        val locales = res.listFiles { file -> file.isDirectory && File(file, "strings.xml").isFile && file.name.startsWith("values") }
            .orEmpty().map { it.name }.toSet()
        assertEquals(setOf("values", "values-in", "values-ja", "values-pt", "values-ru", "values-tr", "values-vi", "values-zh"), locales)
        val keys = setOf("fw_feedback_sound_effects", "fw_feedback_haptic_feedback", "fw_feedback_tokens_received")
        for (locale in locales) {
            val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(res, "$locale/strings_feedback.xml"))
                .getElementsByTagName("string")
            val strings = (0 until nodes.length).associate { (nodes.item(it) as Element).let { e -> e.getAttribute("name") to e.textContent } }
            assertEquals(locale, keys, strings.keys)
            assertTrue(locale, strings.values.all { it.isNotBlank() })
            assertEquals(locale, 2, strings.getValue("fw_feedback_tokens_received").split(FORMAT_ARGUMENT).size)
        }
    }

    private class MemoryStore(private val writable: Boolean = true) : FeedbackPreferences.Store {
        val values = linkedMapOf<String, Boolean>()
        private val listeners = mutableListOf<() -> Unit>()
        override fun getBoolean(key: String, default: Boolean) = values[key] ?: default
        override fun putBoolean(key: String, value: Boolean): Boolean {
            if (!writable) return false
            values[key] = value
            listeners.toList().forEach { it() }
            return true
        }
        override fun listen(onChange: () -> Unit): AutoCloseable {
            listeners += onChange
            return AutoCloseable { listeners -= onChange }
        }
    }
}

class ArrivalDetectorTest {
    private fun holding(key: String, amount: String?, hidden: Boolean = false) =
        ArrivalDetector.Holding(key, amount?.let(::BigDecimal), hidden, key.substringAfter(':').uppercase())

    @Test
    fun shouldStayQuiet_onTheFirstValuesOfAWallet() {
        val detector = ArrivalDetector()
        assertNull(detector.observe(1, listOf(holding("dot:dot", "5")), 0, foreground = true))
    }

    @Test
    fun shouldAnnounce_whenAKnownBalanceGrows() {
        val detector = ArrivalDetector()
        detector.observe(1, listOf(holding("dot:dot", "5"), holding("ksm:ksm", "1")), 0, true)
        val arrival = detector.observe(1, listOf(holding("dot:dot", "5.5"), holding("ksm:ksm", "1")), 10_000, true)
        assertEquals(listOf("dot:dot"), arrival!!.assetKeys)
        assertEquals(listOf("DOT"), arrival.symbols)
    }

    @Test
    fun shouldStayQuiet_forDecreasesNewAssetsHiddenAssetsAndUnreadValues() {
        val detector = ArrivalDetector()
        detector.observe(1, listOf(holding("dot:dot", "5"), holding("eth:usdt", "3", hidden = true), holding("sol:sol", null)), 0, true)
        // Spent, a newly added network, a hidden deposit, a balance read for the first time.
        assertNull(detector.observe(1, listOf(holding("dot:dot", "4"), holding("ksm:ksm", "9"), holding("eth:usdt", "30", hidden = true),
            holding("sol:sol", "2")), 10_000, true))
        // Unread values never erase what is known.
        assertNull(detector.observe(1, listOf(holding("dot:dot", null)), 20_000, true))
        assertEquals(listOf("dot:dot"), detector.observe(1, listOf(holding("dot:dot", "4.1")), 30_000, true)!!.assetKeys)
    }

    @Test
    fun shouldStayQuiet_inTheBackground_andNotReplayLater() {
        val detector = ArrivalDetector()
        detector.observe(1, listOf(holding("dot:dot", "5")), 0, true)
        assertNull(detector.observe(1, listOf(holding("dot:dot", "6")), 10_000, foreground = false))
        assertNull(detector.observe(1, listOf(holding("dot:dot", "6")), 20_000, foreground = true))
    }

    @Test
    fun shouldKeepWalletsApart() {
        val detector = ArrivalDetector()
        detector.observe(1, listOf(holding("dot:dot", "5")), 0, true)
        // Switching to another wallet shows its first values: nothing arrived.
        assertNull(detector.observe(2, listOf(holding("dot:dot", "50")), 10_000, true))
        assertEquals(listOf("dot:dot"), detector.observe(1, listOf(holding("dot:dot", "6")), 20_000, true)!!.assetKeys)
    }

    @Test
    fun shouldPlayAtMostOneEffect_inAnyThreeSeconds() {
        val detector = ArrivalDetector()
        detector.observe(1, listOf(holding("dot:dot", "1")), 0, true)
        assertTrue(detector.observe(1, listOf(holding("dot:dot", "2")), 10_000, true) != null)
        assertNull(detector.observe(1, listOf(holding("dot:dot", "3")), 12_999, true))
        assertTrue(detector.observe(1, listOf(holding("dot:dot", "4")), 13_000, true) != null)
        detector.reset()
        assertNull(detector.observe(1, listOf(holding("dot:dot", "5")), 20_000, true))
    }
}
