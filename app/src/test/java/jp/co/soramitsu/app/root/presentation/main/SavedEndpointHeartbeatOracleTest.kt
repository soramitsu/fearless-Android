package jp.co.soramitsu.app.root.presentation.main

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SavedEndpointHeartbeatOracleTest {
    private val day = "2026-10-21"
    private val issued = Instant.parse("2026-10-21T02:00:00Z").toEpochMilli()
    private val agent = "device-name/sdk_gslim_arm64 fire-core/21.0.0 fire-android/34"
    private val baseline = file(listOf("2026-10-20"), "a".repeat(64))
    private val appended = file(listOf("2026-10-20", day), "b".repeat(64))

    @Test fun unchangedRetainedFileIsAcceptedWithoutAppend() {
        val result = oracle().observe(baseline, issued)
        assertFalse(result.dailyAppend)
        assertEquals(baseline.rawSha256, result.previousSha256)
    }

    @Test fun exactCurrentUtcDailyAppendIsAccepted() {
        val result = oracle().observe(appended, issued + 1)
        assertTrue(result.dailyAppend)
        assertEquals(baseline.rawSha256, result.baselineSha256)
        assertEquals(appended.rawSha256, result.observedSha256)
        assertEquals(day, result.expectedUtcDate)
    }

    @Test fun asynchronousAppendAfterEarlierUnchangedSnapshotIsAccepted() {
        val oracle = oracle()
        assertFalse(oracle.observe(baseline, issued).dailyAppend)
        assertTrue(oracle.observe(appended, issued + 100).dailyAppend)
        assertFalse(oracle.observe(appended, issued + 101).dailyAppend)
    }

    @Test fun alreadyCurrentBaselineRemainsExact() {
        assertFalse(oracle(appended).observe(appended, issued).dailyAppend)
    }

    @Test fun delayedStartupDoesNotInventMissedDates() {
        val old = file(listOf("2026-10-01"), "a".repeat(64))
        val current = file(listOf("2026-10-01", day), "b".repeat(64))
        assertTrue(oracle(old).observe(current, issued).dailyAppend)
    }

    @Test fun repeatedDailyAppendIsRejected() {
        val oracle = oracle()
        oracle.observe(appended, issued)
        reject { oracle.observe(appended.copy(rawSha256 = "c".repeat(64)), issued + 1) }
    }

    @Test fun rollbackAfterAcceptedAppendIsRejected() {
        val oracle = oracle()
        oracle.observe(appended, issued)
        reject { oracle.observe(baseline, issued + 1) }
    }

    @Test fun sameValuesWithChangedRawXmlAreRejected() {
        reject { oracle().observe(baseline.copy(rawSha256 = "b".repeat(64)), issued) }
    }

    @Test fun changedRawLengthWithoutTransitionIsRejected() {
        reject { oracle().observe(baseline.copy(rawBytes = baseline.rawBytes + 1), issued) }
    }

    @Test fun changedValuesWithUnchangedXmlDigestAreRejected() {
        reject { oracle().observe(appended.copy(rawSha256 = baseline.rawSha256), issued) }
    }

    @Test fun walletPreferencePathIsRejected() {
        reject { oracle().observe(appended.copy(path = "shared_prefs/fearless_prefs.xml"), issued) }
    }

    @Test fun similarHeartbeatFilenameIsRejected() {
        reject { oracle().observe(appended.copy(path = SavedEndpointHeartbeatOracle.PATH + ".bak"), issued) }
    }

    @Test fun extraKeyIsRejected() {
        rejectValues(appended.values + ("fire-global" to value("long", "1")))
    }

    @Test fun missingKeyIsRejected() {
        rejectValues(appended.values - "last-used-date")
    }

    @Test fun changedUserAgentIsRejected() {
        rejectValues(appended.values - agent + ("changed fire-core/21.0.0" to appended.values.getValue(agent)))
    }

    @Test fun addedUserAgentIsRejected() {
        rejectValues(appended.values + ("another fire-core/21.0.0" to value("set", day)))
    }

    @Test fun integerCountInsteadOfLongIsRejected() {
        rejectValues(appended.values + ("fire-count" to value("int", "2")))
    }

    @Test fun countWithoutDateAppendIsRejected() {
        rejectValues(baseline.values + ("fire-count" to value("long", "2")))
    }

    @Test fun dateAppendWithoutCountIsRejected() {
        rejectValues(appended.values + ("fire-count" to value("long", "1")))
    }

    @Test fun nonCanonicalAndExcessiveCountsAreRejected() {
        listOf("02", "-1", "0", "29", "9223372036854775808").forEach {
            rejectValues(appended.values + ("fire-count" to value("long", it)))
        }
    }

    @Test fun stringInsteadOfSetIsRejected() {
        rejectValues(appended.values + (agent to value("string", day)))
    }

    @Test fun duplicateOrUnsortedSetDatesAreRejected() {
        rejectValues(appended.values + (agent to value("set", day, day)))
        rejectValues(appended.values + (agent to value("set", day, "2026-10-20")))
    }

    @Test fun removedHistoricalDateIsRejected() {
        reject { oracle().observe(file(listOf(day), "b".repeat(64)), issued) }
    }

    @Test fun historicalOrFutureDateAppendIsRejected() {
        listOf("2026-10-19", "2026-10-22").forEach {
            reject { oracle().observe(file(listOf("2026-10-20", it).sorted(), "b".repeat(64)), issued) }
        }
    }

    @Test fun malformedDateAndMismatchedLastDateAreRejected() {
        listOf("2026-10-2", "2026-02-30", "2026-10-20").forEach {
            rejectValues(appended.values + ("last-used-date" to value("string", it)))
        }
    }

    @Test fun emptyOrFutureBaselineIsRejected() {
        reject { oracle(baseline.copy(values = emptyMap())) }
        reject { oracle(file(listOf("2026-10-22"), "a".repeat(64))) }
    }

    @Test fun wrongIssuedDayIsRejected() {
        reject { SavedEndpointHeartbeatOracle("2026-10-20", issued, baseline) }
    }

    @Test fun utcMidnightDuringJourneyIsRejected() {
        val late = Instant.parse("2026-10-21T23:59:59Z").toEpochMilli()
        reject { SavedEndpointHeartbeatOracle(day, late, baseline).observe(appended, late + 2_000) }
    }

    @Test fun clockSkewAndExcessiveDurationAreRejected() {
        reject { oracle().observe(baseline, issued - 10_001) }
        reject { oracle().observe(baseline, issued + 900_001) }
    }

    @Test fun backwardsObservationClockIsRejected() {
        val oracle = oracle()
        oracle.observe(baseline, issued + 2)
        reject { oracle.observe(baseline, issued + 1) }
    }

    @Test fun exactTimeBoundsAreAccepted() {
        assertFalse(oracle().observe(baseline, issued - 10_000).dailyAppend)
        assertTrue(oracle().observe(appended, issued + 900_000).dailyAppend)
    }

    @Test fun malformedDigestOrOversizedXmlIsRejected() {
        reject { oracle().observe(appended.copy(rawSha256 = "not-a-hash"), issued) }
        reject { oracle().observe(appended.copy(rawBytes = 16_385), issued) }
    }

    @Test fun rejectedMutationDoesNotAdvanceOracle() {
        val oracle = oracle()
        reject { oracle.observe(appended.copy(path = "wrong"), issued + 1) }
        assertTrue(oracle.observe(appended, issued + 2).dailyAppend)
    }

    private fun oracle(state: SavedEndpointHeartbeatOracle.FileState = baseline) =
        SavedEndpointHeartbeatOracle(day, issued, state)

    private fun file(dates: List<String>, digest: String) = SavedEndpointHeartbeatOracle.FileState(
        SavedEndpointHeartbeatOracle.PATH, digest, 500 + dates.size * 36,
        mapOf(
            agent to SavedEndpointHeartbeatOracle.Value("set", dates),
            "fire-count" to value("long", dates.size.toString()),
            "last-used-date" to value("string", dates.last())
        )
    )

    private fun value(type: String, vararg values: String) = SavedEndpointHeartbeatOracle.Value(type, values.toList())

    private fun rejectValues(values: Map<String, SavedEndpointHeartbeatOracle.Value>) {
        reject { oracle().observe(appended.copy(values = values), issued) }
    }

    private fun reject(block: () -> Unit) {
        assertThrows(RuntimeException::class.java, block)
    }
}
