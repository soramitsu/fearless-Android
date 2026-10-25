package jp.co.soramitsu.app.root.presentation.main

import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import android.util.Base64
import org.json.JSONObject
import java.io.File

/** Keeps original raw snapshots and records each narrowly validated telemetry observation. */
internal class SavedEndpointHeartbeatEvidence(baselinePreferences: JSONObject, arguments: Bundle) {
    private val utcDate = checkNotNull(arguments.getString("fearlessFixtureUtcDate"))
    private val issuedAtMillis = checkNotNull(arguments.getString("fearlessFixtureIssuedAtMillis")).toLong()
    private val oracle = SavedEndpointHeartbeatOracle(
        utcDate, issuedAtMillis, decode(baselinePreferences.getJSONObject(SavedEndpointHeartbeatOracle.NAME))
    )

    fun observe(snapshot: JSONObject) {
        check(!snapshot.has("heartbeatObservation"))
        val file = snapshot.getJSONObject("preferences").getJSONObject(SavedEndpointHeartbeatOracle.NAME)
        val result = oracle.observe(decode(file), System.currentTimeMillis())
        snapshot.put(
            "heartbeatObservation",
            JSONObject()
            .put("path", SavedEndpointHeartbeatOracle.PATH)
            .put("expectedUtcDate", result.expectedUtcDate)
            .put("issuedAtMillis", result.issuedAtMillis)
            .put("observedAtMillis", result.observedAtMillis)
            .put("baselineSHA256", result.baselineSha256)
            .put("previousSHA256", result.previousSha256)
            .put("observedSHA256", result.observedSha256)
            .put("dailyAppend", result.dailyAppend)
        )
    }

    companion object {
        /** Wait only for initial asynchronous telemetry, before the fresh fixture's first raw baseline. */
        fun awaitFreshHeartbeat(context: Context, arguments: Bundle) {
            val deadline = SystemClock.elapsedRealtime() + 15_000
            val expectedDay = checkNotNull(arguments.getString("fearlessFixtureUtcDate"))
            val issuedAt = checkNotNull(arguments.getString("fearlessFixtureIssuedAtMillis")).toLong()
            val path = File(context.applicationInfo.dataDir, SavedEndpointHeartbeatOracle.PATH)
            do {
                val raw = if (path.isFile) {
                    SavedEndpointInspectionRawSnapshot.preferences(context, SavedEndpointHeartbeatOracle.NAME)
                } else {
                    null
                }
                // Only a missing or empty, not-yet-completed initial heartbeat is retryable.
                if (raw != null && raw.getJSONObject("typedMap").length() != 0) {
                    val state = decode(raw)
                    SavedEndpointHeartbeatOracle(expectedDay, issuedAt, state).observe(state, System.currentTimeMillis())
                    check(raw.getJSONObject("typedMap").getJSONObject("last-used-date").getString("value") == expectedDay)
                    return
                }
                Thread.sleep(25)
            } while (SystemClock.elapsedRealtime() < deadline)
            error("Fresh Firebase heartbeat did not finish within the bounded admission interval")
        }

        private fun decode(snapshot: JSONObject): SavedEndpointHeartbeatOracle.FileState {
            check(snapshot.keys().asSequence().toSet() == setOf("path", "typedMap", "rawXmlBase64", "rawXmlSHA256", "rawXmlBytes"))
            val bytes = Base64.decode(snapshot.getString("rawXmlBase64"), Base64.NO_WRAP)
            check(bytes.size == snapshot.getInt("rawXmlBytes"))
            check(SavedEndpointInspectionRawSnapshot.sha256(bytes) == snapshot.getString("rawXmlSHA256"))
            val values = snapshot.getJSONObject("typedMap")
            val decoded = values.keys().asSequence().associateWith { key ->
                val value = values.getJSONObject(key)
                check(value.keys().asSequence().toSet() == setOf("type", "value"))
                check(value.get("type") is String)
                val type = value.getString("type")
                val items = if (type == "set") {
                    val array = value.getJSONArray("value")
                    (0 until array.length()).map { index ->
                        check(array.get(index) is String)
                        array.getString(index)
                    }
                } else {
                    check(value.get("value") is String)
                    listOf(value.getString("value"))
                }
                SavedEndpointHeartbeatOracle.Value(type, items)
            }
            return SavedEndpointHeartbeatOracle.FileState(
                snapshot.getString("path"), snapshot.getString("rawXmlSHA256"), bytes.size, decoded
            )
        }
    }
}
