package jp.co.soramitsu.app.root.presentation.main

import android.content.Context
import android.os.SystemClock
import android.util.Base64
import org.json.JSONObject
import java.io.File

/**
 * Records each observation of the Firebase heartbeat preferences file. Firebase is not part of the
 * app since 4.3.2, so a fresh fixture never gets one and a file an older version left behind must
 * stay byte-identical.
 */
internal class SavedEndpointHeartbeatEvidence(baselinePreferences: JSONObject) {
    private val baseline: SavedEndpointHeartbeatOracle.FileState? =
        baselinePreferences.optJSONObject(SavedEndpointHeartbeatOracle.NAME)?.let(::decode)

    fun observe(snapshot: JSONObject) {
        check(!snapshot.has("heartbeatObservation"))
        val file = snapshot.getJSONObject("preferences").optJSONObject(SavedEndpointHeartbeatOracle.NAME)
        val observation = JSONObject()
            .put("path", SavedEndpointHeartbeatOracle.PATH)
            .put("observedAtMillis", System.currentTimeMillis())
        val retained = baseline
        if (retained == null) {
            check(file == null) { "A Firebase heartbeat appeared; Firebase is not part of the app" }
            observation.put("present", false)
        } else {
            val current = decode(checkNotNull(file) { "The retained Firebase heartbeat file disappeared" })
            check(current == retained) { "The retained Firebase heartbeat changed; Firebase is not part of the app" }
            observation
                .put("present", true)
                .put("baselineSHA256", retained.rawSha256)
                .put("observedSHA256", current.rawSha256)
        }
        snapshot.put("heartbeatObservation", observation)
    }

    companion object {
        private const val ABSENCE_SETTLE_MILLIS = 3_000L

        /** A fresh fixture must not get a Firebase heartbeat: Firebase is not part of the app. */
        fun assertNoFreshFirebaseHeartbeat(context: Context) {
            val path = File(context.applicationInfo.dataDir, SavedEndpointHeartbeatOracle.PATH)
            val deadline = SystemClock.elapsedRealtime() + ABSENCE_SETTLE_MILLIS
            do {
                check(!path.exists()) { "A Firebase heartbeat appeared; Firebase is not part of the app" }
                Thread.sleep(25)
            } while (SystemClock.elapsedRealtime() < deadline)
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
