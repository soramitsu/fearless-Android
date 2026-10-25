package jp.co.soramitsu.app.root.presentation.main

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Pure test oracle for one retained Firebase 21 heartbeat file, within one host-bound UTC day. */
internal class SavedEndpointHeartbeatOracle(
    private val expectedUtcDate: String,
    private val issuedAtMillis: Long,
    private val baseline: FileState
) {
    data class Value(val type: String, val values: List<String>)

    data class FileState(
        val path: String,
        val rawSha256: String,
        val rawBytes: Int,
        val values: Map<String, Value>
    )

    data class Observation(
        val expectedUtcDate: String,
        val issuedAtMillis: Long,
        val observedAtMillis: Long,
        val baselineSha256: String,
        val previousSha256: String,
        val observedSha256: String,
        val dailyAppend: Boolean
    )

    private val baselineShape = shape(baseline)
    private var previous = baseline
    private var lastObservedAtMillis = Long.MIN_VALUE

    init {
        check(LocalDate.parse(expectedUtcDate).toString() == expectedUtcDate)
        check(utcDate(issuedAtMillis) == expectedUtcDate)
        check(baselineShape.lastDate <= expectedUtcDate)
    }

    /** No mutation is performed. A repeated snapshot must retain its exact heartbeat XML digest. */
    fun observe(current: FileState, nowMillis: Long): Observation {
        check(nowMillis >= issuedAtMillis - CLOCK_TOLERANCE_MILLIS)
        check(nowMillis <= issuedAtMillis + MAX_RUN_MILLIS)
        check(nowMillis >= lastObservedAtMillis)
        check(utcDate(nowMillis) == expectedUtcDate) { "UTC day changed; retain packet and fail this run" }
        val currentShape = shape(current)
        check(currentShape.userAgent == baselineShape.userAgent)
        val appended = current != previous
        if (appended) {
            check(previous == baseline) { "Only one daily append is allowed" }
            check(baselineShape.lastDate < expectedUtcDate)
            check(
                current.values == baseline.values + mapOf(
                baselineShape.userAgent to Value("set", (baselineShape.dates + expectedUtcDate).sorted()),
                "fire-count" to Value("long", listOf((baselineShape.count + 1).toString())),
                "last-used-date" to Value("string", listOf(expectedUtcDate))
            )
            )
            check(current.rawSha256 != previous.rawSha256)
        }
        return Observation(
            expectedUtcDate, issuedAtMillis, nowMillis, baseline.rawSha256,
            previous.rawSha256, current.rawSha256, appended
        ).also {
            previous = current
            lastObservedAtMillis = nowMillis
        }
    }

    private data class Shape(val userAgent: String, val dates: List<String>, val count: Long, val lastDate: String)

    private fun shape(file: FileState): Shape {
        check(file.path == PATH)
        check(file.rawSha256.matches(Regex("[0-9a-f]{64}")))
        check(file.rawBytes in 1..MAX_XML_BYTES)
        check(file.values.size == 3)
        val countValue = checkNotNull(file.values["fire-count"])
        check(countValue.type == "long" && countValue.values.size == 1)
        val count = countValue.values.single().toLong()
        check(count in 1..28 && count.toString() == countValue.values.single())
        val lastValue = checkNotNull(file.values["last-used-date"])
        check(lastValue.type == "string" && lastValue.values.size == 1)
        val lastDate = lastValue.values.single()
        check(LocalDate.parse(lastDate).toString() == lastDate)
        val userAgent = file.values.keys.single { it != "fire-count" && it != "last-used-date" }
        check(userAgent.length in 1..4096 && "fire-core/21.0.0" in userAgent.split(' '))
        val dateValue = file.values.getValue(userAgent)
        check(dateValue.type == "set")
        val dates = dateValue.values
        check(dates.size.toLong() == count && dates == dates.distinct().sorted())
        check(dates.all { LocalDate.parse(it).toString() == it && it <= lastDate })
        check(dates.last() == lastDate)
        return Shape(userAgent, dates, count, lastDate)
    }

    companion object {
        const val NAME = "FirebaseHeartBeatW0RFRkFVTFRd+MTowMDAwMDAwMDAwMDA6YW5kcm9pZDowMDAwMDAwMDAwMDAwMDAwMDAwMDAx"
        const val PATH = "shared_prefs/$NAME.xml"
        private const val CLOCK_TOLERANCE_MILLIS = 10_000L
        private const val MAX_RUN_MILLIS = 900_000L
        private const val MAX_XML_BYTES = 16_384

        private fun utcDate(millis: Long): String =
            Instant.ofEpochMilli(millis).atOffset(ZoneOffset.UTC).toLocalDate().toString()
    }
}
