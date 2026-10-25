package jp.co.soramitsu.app.root.presentation.main

import android.content.Context
import android.database.Cursor
import java.io.File
import jp.co.soramitsu.coredb.recovery.EndpointInventorySource
import org.json.JSONArray
import org.json.JSONObject

/** Typed, bounded public-fixture evidence. No production data or preference writes. */
internal object SavedEndpointDecisionRawSnapshot {
    fun sql(query: (String) -> Cursor): JSONObject {
        val schema = metadata(query, "SELECT type,name,tbl_name,sql FROM main.sqlite_master ORDER BY type,name")
        val tables = JSONObject()
        query("SELECT name FROM main.sqlite_master WHERE type='table' ORDER BY name LIMIT 101").use { cursor ->
            var count = 0
            while (cursor.moveToNext()) {
                check(++count <= 100)
                val name = cursor.getString(0)
                val identifier = quote(name)
                val columns = mutableListOf<String>()
                query("PRAGMA main.table_info($identifier)").use { info ->
                    while (info.moveToNext()) columns += info.getString(1)
                }
                check(columns.isNotEmpty() && columns.size <= 100)
                val projection = columns.joinToString(",") { column ->
                    val c = quote(column)
                    "typeof($c),CASE WHEN typeof($c) IN ('text','blob') AND length(CAST($c AS BLOB))<=65536 THEN hex(CAST($c AS BLOB)) END," +
                        "CASE WHEN typeof($c)='integer' THEN $c END,CASE WHEN typeof($c)='real' THEN $c END"
                }
                val rows = mutableListOf<JSONObject>()
                query("SELECT $projection FROM main.$identifier LIMIT 201").use { data ->
                    while (data.moveToNext()) {
                        check(rows.size < 200) { "Unexpected fixture row count" }
                        val row = JSONObject()
                        columns.forEachIndexed { index, column ->
                            val i = index * 4
                            val type = data.getString(i)
                            val value = JSONObject().put("storage", type)
                            when (type) {
                                "null" -> Unit
                                "integer" -> value.put("integer", data.getLong(i + 2))
                                "real" -> value.put("real", data.getDouble(i + 3))
                                "text", "blob" -> { check(!data.isNull(i + 1)); value.put("hex", data.getString(i + 1)) }
                                else -> error("Unexpected fixture storage class")
                            }
                            row.put(column, value)
                        }
                        rows += row
                    }
                }
                tables.put(name, JSONObject().put("columns", JSONArray(columns))
                    .put("rows", JSONArray(rows.sortedBy(SavedEndpointInspectionRawSnapshot::canonical))))
            }
        }
        return JSONObject().put("schema", schema).put("tables", tables)
            .put("userVersion", metadata(query, "PRAGMA user_version"))
            .put("foreignKeys", metadata(query, "PRAGMA foreign_keys"))
    }


    /** Capture every existing preference file; no key or file is silently excluded. */
    fun preferences(context: Context): JSONObject {
        val folder = File(context.applicationInfo.dataDir, "shared_prefs")
        val files = folder.listFiles().orEmpty().sortedBy { it.name }
        check(files.size in 3..64 && files.all { it.isFile && it.name.endsWith(".xml") && it.length() in 1..65_536 })
        check(files.sumOf { it.length() } <= 1_048_576)
        val result = JSONObject()
        for (file in files) result.put(file.name.removeSuffix(".xml"),
            SavedEndpointInspectionRawSnapshot.preferences(context, file.name.removeSuffix(".xml")))
        check(folder.listFiles().orEmpty().map { it.name }.sorted() == files.map { it.name })
        return result
    }

    /** Only connection-local foreign_keys is omitted from persistent SQL equality. */
    fun persistedSql(snapshot: JSONObject): JSONObject = copy(snapshot).also { it.remove("foreignKeys") }

    /** Exact shipped 82-to-83 delta; every old schema object and typed cell is retained. */
    fun upgraded(sql82: JSONObject): JSONObject {
        check(sql82.getJSONArray("userVersion").toString() == "[[82]]")
        val result = copy(sql82)
        val tables = result.getJSONObject("tables")
        check(!tables.has(TABLE))
        val oldIdentity = tables.getJSONObject("room_master_table").getJSONArray("rows")
        check(oldIdentity.length() == 1)
        val identity = oldIdentity.getJSONObject(0)
        check(identity.getJSONObject("id").getLong("integer") == 42L)
        check(identity.getJSONObject("identity_hash").getString("hex") == hex(IDENTITY_82))
        identity.put("identity_hash", cell(IDENTITY_83))
        tables.put(TABLE, JSONObject().put("columns", JSONArray(COLUMNS)).put("rows", JSONArray()))
        val schema = result.getJSONArray("schema")
        val rows = (0 until schema.length()).map { schema.getJSONArray(it) }.toMutableList()
        check(rows.none { it.getString(1) == TABLE || it.getString(1) == INDEX })
        rows += JSONArray(listOf("table", TABLE, TABLE, CREATE))
        rows += JSONArray(listOf("index", INDEX, TABLE, CREATE_INDEX))
        result.put("schema", JSONArray(rows.sortedWith(compareBy<JSONArray> { it.getString(0) }.thenBy { it.getString(1) })))
        result.put("userVersion", JSONArray("[[83]]"))
        return result
    }

    /** Predict the complete SQL delta of one actual UI save from independent retained-source hashes. */
    fun inserted(sql: JSONObject, source: EndpointInventorySource, name: String, url: String): JSONObject {
        val result = copy(sql)
        val tables = result.getJSONObject("tables")
        val decision = tables.getJSONObject(TABLE)
        check((0 until decision.getJSONArray("columns").length()).map(decision.getJSONArray("columns")::getString) == COLUMNS)
        val rows = decision.getJSONArray("rows")
        val previous = (0 until rows.length()).map(rows::getJSONObject)
        check(previous.none { it.getJSONObject("sourceKind").getString("hex") == hex(source.name) })
        val id = previous.size + 1L
        check(previous.map { it.getJSONObject("id").getLong("integer") }.toSet() == (1L until id).toSet())
        val row = JSONObject()
        val values = listOf<Any?>(id, 1L, source.name, if (source == EndpointInventorySource.LEGACY) 9001L else -9003L,
            if (source == EndpointInventorySource.OWNED) 8L else null, FINGERPRINTS.getValue(source), name, url,
            "solana-mainnet", TARGET_FINGERPRINT, "SOLANA", "5eykt4UsFv8P8NJdTREpY1vzqKqZKvdpKuc147dw2N9d", "PENDING")
        COLUMNS.zip(values).forEach { (column, value) -> row.put(column, cell(value)) }
        decision.put("rows", JSONArray((previous + row).sortedBy(SavedEndpointInspectionRawSnapshot::canonical)))
        val sequence = tables.getJSONObject("sqlite_sequence")
        check(sequence.getJSONArray("columns").toString() == "[\"name\",\"seq\"]")
        val seqRows = sequence.getJSONArray("rows")
        val all = (0 until seqRows.length()).map(seqRows::getJSONObject)
        val matching = all.filter { it.getJSONObject("name").optString("hex") == hex(TABLE) }
        if (id == 1L) check(matching.isEmpty()) else {
            check(matching.size == 1 && matching.single().getJSONObject("seq").getLong("integer") == id - 1)
        }
        val updated = all.filterNot { it in matching } + JSONObject().put("name", cell(TABLE)).put("seq", cell(id))
        sequence.put("rows", JSONArray(updated.sortedBy(SavedEndpointInspectionRawSnapshot::canonical)))
        return result
    }

    private fun copy(value: JSONObject): JSONObject = JSONObject(SavedEndpointInspectionRawSnapshot.canonical(value))
    private fun hex(value: String): String = value.toByteArray(Charsets.UTF_8).joinToString("") { "%02X".format(it.toInt() and 255) }
    private fun cell(value: Any?): JSONObject = JSONObject().also { cell ->
        when (value) {
            null -> cell.put("storage", "null")
            is Long -> cell.put("storage", "integer").put("integer", value)
            is String -> cell.put("storage", "text").put("hex", hex(value))
            else -> error("Unsupported fixture oracle value")
        }
    }

    const val TABLE = "endpoint_recovery_decisions"
    private const val INDEX = "index_endpoint_recovery_decisions_sourceKind_sourceRowId"
    private const val IDENTITY_82 = "48f19eba63747d19b0d1b01f68302d69"
    private const val IDENTITY_83 = "7dcfe60d9e078af3223a27e8b74e784f"
    private val COLUMNS = listOf("id", "revision", "sourceKind", "sourceRowId", "sourceRevision", "sourceFingerprint",
        "candidateName", "candidateUrl", "targetChainId", "targetIdentityFingerprint", "targetFamily", "targetExpectedGenesis", "state")
    private const val CREATE = "CREATE TABLE `endpoint_recovery_decisions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
        "`revision` INTEGER NOT NULL, `sourceKind` TEXT NOT NULL, `sourceRowId` INTEGER NOT NULL, `sourceRevision` INTEGER, " +
        "`sourceFingerprint` TEXT NOT NULL, `candidateName` TEXT NOT NULL, `candidateUrl` TEXT NOT NULL, " +
        "`targetChainId` TEXT NOT NULL, `targetIdentityFingerprint` TEXT NOT NULL, `targetFamily` TEXT NOT NULL, " +
        "`targetExpectedGenesis` TEXT, `state` TEXT NOT NULL)"
    private const val CREATE_INDEX = "CREATE UNIQUE INDEX `index_endpoint_recovery_decisions_sourceKind_sourceRowId` " +
        "ON `endpoint_recovery_decisions` (`sourceKind`, `sourceRowId`)"
    private const val TARGET_FINGERPRINT = "159f5491f3354aeb82496ab62e5b26e9f03bfcc15cd87dc0d7974ede72000bd0"
    private val FINGERPRINTS = mapOf(
        EndpointInventorySource.LEGACY to "3f2831a8dad4ba778bbc47d62c14c7490672b1c27a92cc48ad13441437680835",
        EndpointInventorySource.HISTORICAL to "7e86a852f4c5ee64fa9ac69f6b37c17f1bc4559d680fa9006c580ea711e531de",
        EndpointInventorySource.UPGRADE_ENTRY to "0e8416cb3dff036836d5140d6189902ed29905f324e1d19185e26d88cd7485ea",
        EndpointInventorySource.CURRENT to "20e0fba5c32938de8c0f070fb27b390d4c27c662a432baded080243f1955d0d4",
        EndpointInventorySource.OWNED to "07d86a7f6937e0c88e92e93dfe97bedaf78370d411dcf25ccef4f026f5dc0a37"
    )
    private fun metadata(read: (String) -> Cursor, query: String): JSONArray = read(query).use { cursor ->
        JSONArray().also { rows ->
            while (cursor.moveToNext()) rows.put(JSONArray().also { row ->
                repeat(cursor.columnCount) { i -> row.put(when (cursor.getType(i)) {
                    Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
                    Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(i)
                    Cursor.FIELD_TYPE_STRING -> cursor.getString(i)
                    else -> error("Unexpected fixture metadata")
                }) }
            })
        }
    }
    private fun quote(name: String): String = "\"${name.replace("\"", "\"\"")}\""
}
