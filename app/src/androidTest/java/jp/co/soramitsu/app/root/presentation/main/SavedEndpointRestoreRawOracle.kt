package jp.co.soramitsu.app.root.presentation.main

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

/** Independent typed-cell expectation for the public Restore fixture; never calls production stores. */
internal object SavedEndpointRestoreRawOracle {
    fun saved(sql: JSONObject, ownerId: Long, revision: Long, name: String, url: String): JSONObject = copy(sql).also { result ->
        val tables = result.getJSONObject("tables")
        val owner = rows(tables.getJSONObject("custom_endpoint_records")).single()
        check(owner.getJSONObject("id").getLong("integer") == ownerId)
        check(owner.getJSONObject("revision").getLong("integer") == revision)
        check(owner.getJSONObject("name").getString("hex") == hex(name))
        check(owner.getJSONObject("url").getString("hex") == hex(url))
        val decisions = tables.getJSONObject("endpoint_recovery_decisions")
        val previous = rows(decisions)
        check(previous.size <= 1)
        val decisionRevision = if (previous.isEmpty()) 1L else {
            check(previous.single().getJSONObject("id").getLong("integer") == 1L)
            check(previous.single().getJSONObject("revision").getLong("integer") == 1L)
            2L
        }
        val values = linkedMapOf<String, Any?>(
            "id" to 1L, "revision" to decisionRevision, "sourceKind" to "OWNED", "sourceRowId" to ownerId,
            "sourceRevision" to revision, "sourceFingerprint" to sourceFingerprint(owner),
            "candidateName" to name, "candidateUrl" to url, "targetChainId" to "solana-mainnet",
            "targetIdentityFingerprint" to "159f5491f3354aeb82496ab62e5b26e9f03bfcc15cd87dc0d7974ede72000bd0",
            "targetFamily" to "SOLANA", "targetExpectedGenesis" to "5eykt4UsFv8P8NJdTREpY1vzqKqZKvdpKuc147dw2N9d",
            "state" to "PENDING"
        )
        check(columns(decisions) == values.keys)
        sortedRows(decisions, listOf(row(values)))
        val sequence = tables.getJSONObject("sqlite_sequence")
        val counters = rows(sequence)
        val existing = counters.filter { it.getJSONObject("name").optString("hex") == hex("endpoint_recovery_decisions") }
        if (decisionRevision == 1L) {
            check(existing.isEmpty())
            sortedRows(sequence, counters + row(mapOf("name" to "endpoint_recovery_decisions", "seq" to 1L)))
        } else {
            check(existing.size == 1 && existing.single().getJSONObject("seq").getLong("integer") == 1L)
        }
    }

    fun restored(sql: JSONObject, ownerId: Long, revision: Long): JSONObject = copy(sql).also { result ->
        val tables = result.getJSONObject("tables")
        val ownership = tables.getJSONObject("custom_endpoint_records")
        val owners = rows(ownership)
        val owner = owners.single()
        check(owner.getJSONObject("id").getLong("integer") == ownerId)
        check(owner.getJSONObject("revision").getLong("integer") == revision && revision in 1 until Long.MAX_VALUE)
        check(owner.getJSONObject("attached").getLong("integer") == 0L)
        check(owner.getJSONObject("deleted").getLong("integer") == 0L)
        check(owner.getJSONObject("originalSnapshotRowId").getString("storage") == "integer")
        val projection = tables.getJSONObject("chain_nodes")
        val nodes = rows(projection)
        check(nodes.none { it.getJSONObject("url").toString() == owner.getJSONObject("url").toString() })
        val node = JSONObject().put("chainId", copy(owner.getJSONObject("chainId")))
            .put("url", copy(owner.getJSONObject("url"))).put("name", copy(owner.getJSONObject("name")))
            .put("isActive", cell(0L)).put("isDefault", cell(0L))
        check(columns(projection) == node.keys().asSequence().toSet())
        owner.put("revision", cell(revision + 1)).put("attached", cell(1L))
        sortedRows(ownership, owners)
        sortedRows(projection, nodes + node)
        // Neither the decision nor any sequence is changed by restoring an existing owner.
    }

    /** Independent fixed fixture framing, with all cells read from the captured SQL snapshot. */
    private fun sourceFingerprint(owner: JSONObject): String {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            fun text(value: String) {
                val encoded = value.toByteArray(Charsets.UTF_8)
                out.writeLong(encoded.size.toLong())
                out.write(encoded)
            }
            text("endpoint-decision-source-v1")
            text("OWNED")
            out.writeLong(owner.getJSONObject("id").getLong("integer"))
            out.writeLong(owner.getJSONObject("revision").getLong("integer"))
            out.writeLong(0L)
            out.writeLong(owner.getJSONObject("attached").getLong("integer"))
            out.writeLong(owner.getJSONObject("deleted").getLong("integer"))
            val origin = owner.getJSONObject("originalSnapshotRowId")
            check(origin.getString("storage") == "integer")
            out.writeLong(1L)
            out.writeLong(origin.getLong("integer"))
            for ((field, column) in listOf("NAME" to "name", "ENDPOINT" to "url", "CHAIN_REFERENCE" to "chainId")) {
                val cell = owner.getJSONObject(column)
                check(cell.getString("storage") == "text")
                text(field)
                text("TEXT")
                val encoded = cell.getString("hex").chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                out.writeLong(encoded.size.toLong())
                out.write(encoded)
            }
        }
        return MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) }
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
    private fun hex(value: String) = value.toByteArray(Charsets.UTF_8).joinToString("") { "%02X".format(it.toInt() and 255) }
    private fun cell(value: Any?): JSONObject = JSONObject().also {
        when (value) {
            null -> it.put("storage", "null")
            is Long -> it.put("storage", "integer").put("integer", value)
            is String -> it.put("storage", "text").put("hex", hex(value))
            else -> error("Unsupported fixture value")
        }
    }
}
