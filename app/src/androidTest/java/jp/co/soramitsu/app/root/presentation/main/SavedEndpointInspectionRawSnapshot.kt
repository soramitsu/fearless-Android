package jp.co.soramitsu.app.root.presentation.main

import android.content.Context
import android.database.Cursor
import android.os.SystemClock
import android.util.Base64
import android.util.Xml
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser

/** Test-only raw evidence for one fresh public fixture. Never normalizes or writes preferences. */
internal object SavedEndpointInspectionRawSnapshot {
    fun archive(database: SupportSQLiteDatabase): JSONObject {
        val columns = listOf("id", "name", "link", "networkType", "isDefault", "isActive")
        return JSONObject()
            .put("schema", rows(database, "SELECT type,name,tbl_name,sql FROM sqlite_master " +
                "WHERE name='nodes' OR tbl_name='nodes' ORDER BY type,name"))
            .put("tableInfo", rows(database, "PRAGMA table_info(nodes)"))
            .put("indexList", rows(database, "PRAGMA index_list(nodes)"))
            .put("foreignKeyList", rows(database, "PRAGMA foreign_key_list(nodes)"))
            .put("sequence", rows(database, "SELECT name,typeof(seq),seq FROM sqlite_sequence WHERE name='nodes' ORDER BY name"))
            // SQL hex operates on the original TEXT/BLOB bytes; never getString on malformed text.
            .put("storageClassesAndHexValues", rows(database, "SELECT rowid," + columns.joinToString(",") {
                "typeof(\"$it\"),hex(CAST(\"$it\" AS BLOB))"
            } + " FROM nodes ORDER BY rowid"))
            .put("userVersion", scalar(database, "PRAGMA user_version"))
    }

    fun noWalletCounts(database: SupportSQLiteDatabase): JSONObject = JSONObject()
        .put("meta_accounts", scalar(database, "SELECT COUNT(*) FROM meta_accounts"))
        .put("chain_accounts", scalar(database, "SELECT COUNT(*) FROM chain_accounts"))

    /** Wait for apply() durability by observing typed memory and XML agree twice; perform no edit. */
    fun preferences(context: Context, name: String): JSONObject {
        val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        val file = File(context.applicationInfo.dataDir, "shared_prefs/$name.xml")
        val deadline = SystemClock.elapsedRealtime() + 10_000
        var lastFailure: Throwable? = null
        do {
            try {
                val memoryBefore = typedPreferences(preferences.all)
                val bytes = file.readBytes()
                val parsed = parsePreferences(bytes)
                val memoryAfter = typedPreferences(preferences.all)
                val confirmedBytes = file.readBytes()
                if (canonical(memoryBefore) == canonical(parsed) && canonical(parsed) == canonical(memoryAfter) &&
                    bytes.contentEquals(confirmedBytes)) {
                    return JSONObject().put("path", "shared_prefs/$name.xml")
                        .put("typedMap", parsed).put("rawXmlBase64", Base64.encodeToString(bytes, Base64.NO_WRAP))
                        .put("rawXmlSHA256", sha256(bytes)).put("rawXmlBytes", bytes.size)
                }
            } catch (failure: Exception) {
                lastFailure = failure
            }
            Thread.sleep(25)
        } while (SystemClock.elapsedRealtime() < deadline)
        throw IllegalStateException("Preference memory and durable XML did not agree: $name", lastFailure)
    }

    fun protectedProjection(snapshot: JSONObject): JSONObject {
        val preferences = snapshot.getJSONObject("preferences")
        val fearless = preferences.getJSONObject("fearless_prefs")
        val key = preferences.getJSONObject("key_alias")
        val marker = preferences.getJSONObject("saved_endpoint_inspection_fixture_v1")
        return JSONObject().put("archive", snapshot.getJSONObject("archive"))
            .put("noWalletCounts", snapshot.getJSONObject("noWalletCounts"))
            .put("selectedAccount", snapshot.getBoolean("selectedAccount"))
            .put("pinCode", fearless.getJSONObject("typedMap").getJSONObject("pin_code"))
            .put("wrappedKeyTypedMap", key.getJSONObject("typedMap"))
            .put("wrappedKeyXmlSHA256", key.getString("rawXmlSHA256"))
            .put("markerTypedMap", marker.getJSONObject("typedMap"))
            .put("markerXmlSHA256", marker.getString("rawXmlSHA256"))
    }

    fun writeNew(file: File, value: JSONObject): String {
        check(file.createNewFile()) { "Refuse to overwrite retained fixture evidence" }
        val bytes = (canonical(value) + "\n").toByteArray(Charsets.UTF_8)
        FileOutputStream(file).use { it.write(bytes); it.fd.sync() }
        check(file.readBytes().contentEquals(bytes))
        return sha256(bytes)
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }

    fun canonical(value: Any?): String = when (value) {
        null, JSONObject.NULL -> "null"
        is JSONObject -> value.keys().asSequence().toList().sorted().joinToString(",", "{", "}") {
            JSONObject.quote(it) + ":" + canonical(value.get(it))
        }
        is JSONArray -> (0 until value.length()).joinToString(",", "[", "]") { canonical(value.get(it)) }
        is String -> JSONObject.quote(value)
        is Boolean, is Number -> value.toString()
        else -> error("Unsupported evidence JSON type")
    }

    private fun scalar(database: SupportSQLiteDatabase, sql: String): Long = database.query(sql).use {
        check(it.moveToFirst()); val result = it.getLong(0); check(!it.moveToNext()); result
    }

    private fun rows(database: SupportSQLiteDatabase, sql: String): JSONArray = database.query(sql).use { cursor ->
        JSONArray().also { rows ->
            while (cursor.moveToNext()) rows.put(JSONArray().also { row ->
                repeat(cursor.columnCount) { index ->
                    row.put(when (cursor.getType(index)) {
                        Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
                        Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index)
                        Cursor.FIELD_TYPE_STRING -> cursor.getString(index)
                        else -> error("Unexpected storage class in evidence metadata query")
                    })
                }
            })
        }
    }

    private fun typedPreferences(values: Map<String, *>): JSONObject = JSONObject().also { result ->
        values.toSortedMap().forEach { (name, value) ->
            val type = when (value) {
                is String -> "string"
                is Boolean -> "boolean"
                is Int -> "int"
                is Long -> "long"
                is Float -> "float"
                is Set<*> -> "set"
                else -> error("Unexpected preference type")
            }
            val raw: Any = if (value is Set<*>) {
                check(value.all { it is String })
                JSONArray(value.map { it as String }.sorted())
            } else checkNotNull(value).toString()
            result.put(name, JSONObject().put("type", type).put("value", raw))
        }
    }

    private fun parsePreferences(bytes: ByteArray): JSONObject {
        val parser = Xml.newPullParser()
        parser.setInput(ByteArrayInputStream(bytes), "UTF-8")
        check(parser.nextTag() == XmlPullParser.START_TAG && parser.name == "map")
        val result = JSONObject()
        while (parser.nextTag() == XmlPullParser.START_TAG) {
            val type = parser.name
            val name = checkNotNull(parser.getAttributeValue(null, "name"))
            check(!result.has(name))
            val value: Any = when (type) {
                "string" -> parser.nextText()
                "set" -> {
                    val values = mutableListOf<String>()
                    while (parser.nextTag() == XmlPullParser.START_TAG) {
                        check(parser.name == "string"); values += parser.nextText()
                    }
                    check(parser.name == "set")
                    check(values.distinct().size == values.size)
                    JSONArray(values.sorted())
                }
                "boolean", "int", "long", "float" -> {
                    val raw = checkNotNull(parser.getAttributeValue(null, "value"))
                    check(parser.nextTag() == XmlPullParser.END_TAG && parser.name == type)
                    raw
                }
                else -> error("Unexpected XML preference type")
            }
            result.put(name, JSONObject().put("type", type).put("value", value))
        }
        check(parser.eventType == XmlPullParser.END_TAG && parser.name == "map")
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            check(parser.eventType == XmlPullParser.TEXT && parser.text.isBlank())
        }
        return result
    }
}
