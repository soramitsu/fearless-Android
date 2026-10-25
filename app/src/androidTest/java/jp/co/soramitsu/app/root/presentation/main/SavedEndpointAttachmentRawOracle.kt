package jp.co.soramitsu.app.root.presentation.main

import jp.co.soramitsu.coredb.recovery.EndpointInventorySource
import org.json.JSONArray
import org.json.JSONObject

/** Independent fixture oracle. Only expected typed cells change; no database writes or production mutation helper. */
internal object SavedEndpointAttachmentRawOracle {
    fun revised(sql: JSONObject, source: EndpointInventorySource, url: String, expectedRevision: Long = 1L): JSONObject = copy(sql).also { result ->
        check(expectedRevision in 1L..2L)
        val table = result.getJSONObject("tables").getJSONObject("endpoint_recovery_decisions")
        val rows = rows(table)
        val selected = rows.single { it.getJSONObject("sourceKind").getString("hex") == hex(source.name) }
        check(selected.getJSONObject("revision").getLong("integer") == expectedRevision)
        check(selected.getJSONObject("state").getString("hex") == hex("PENDING"))
        selected.put("revision", cell(expectedRevision + 1L)).put("candidateUrl", cell(url))
        sortedRows(table, rows)
    }

    fun added(sql: JSONObject, name: String, url: String): JSONObject = copy(sql).also { result ->
        val tables = result.getJSONObject("tables")
        val ownership = tables.getJSONObject("custom_endpoint_records")
        val owners = rows(ownership)
        val sequence = tables.getJSONObject("sqlite_sequence")
        val sequences = rows(sequence)
        val counter = sequences.single { it.getJSONObject("name").getString("hex") == hex("custom_endpoint_records") }
        val id = counter.getJSONObject("seq").getLong("integer") + 1
        check(id in 1..5)
        check(owners.none { it.getJSONObject("id").getLong("integer") == id })
        val values = mapOf("id" to id, "revision" to 1L, "chainId" to "solana-mainnet", "url" to url,
            "name" to name, "attached" to 1L, "deleted" to 0L, "originalSnapshotRowId" to null)
        check(columns(ownership) == values.keys)
        sortedRows(ownership, owners + row(values))
        counter.put("seq", cell(id)); sortedRows(sequence, sequences)
        val projection = tables.getJSONObject("chain_nodes")
        val nodes = rows(projection)
        check(nodes.none { it.getJSONObject("url").getString("hex") == hex(url) })
        val node = mapOf("chainId" to "solana-mainnet", "url" to url, "name" to name, "isActive" to 0L, "isDefault" to 0L)
        check(columns(projection) == node.keys)
        sortedRows(projection, nodes + row(node))
    }

    private fun columns(table: JSONObject): Set<String> = table.getJSONArray("columns").let { names ->
        (0 until names.length()).map(names::getString).toSet()
    }
    private fun rows(table: JSONObject): List<JSONObject> = table.getJSONArray("rows").let { values ->
        (0 until values.length()).map(values::getJSONObject)
    }
    private fun sortedRows(table: JSONObject, values: List<JSONObject>) {
        table.put("rows", JSONArray(values.sortedBy(SavedEndpointInspectionRawSnapshot::canonical)))
    }
    private fun row(values: Map<String, Any?>): JSONObject = JSONObject().also { row ->
        values.forEach { (key, value) -> row.put(key, cell(value)) }
    }
    private fun copy(value: JSONObject) = JSONObject(SavedEndpointInspectionRawSnapshot.canonical(value))
    private fun hex(value: String) = value.toByteArray().joinToString("") { "%02X".format(it.toInt() and 255) }
    private fun cell(value: Any?): JSONObject = JSONObject().also {
        when (value) {
            null -> it.put("storage", "null")
            is Long -> it.put("storage", "integer").put("integer", value)
            is String -> it.put("storage", "text").put("hex", hex(value))
            else -> error("Unsupported fixture value")
        }
    }
}
