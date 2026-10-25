package jp.co.soramitsu.app.root.presentation.main

import org.json.JSONArray
import org.json.JSONObject

/** Expected typed SQL delta for this fixed public fixture, independent of selection production code. */
internal object SavedEndpointSelectionRawOracle {
    fun selected(sql: JSONObject, url: String): JSONObject {
        val result = JSONObject(SavedEndpointInspectionRawSnapshot.canonical(sql))
        val table = result.getJSONObject("tables").getJSONObject("chain_nodes")
        val rows = table.getJSONArray("rows")
        val values = (0 until rows.length()).map(rows::getJSONObject)
        fun hex(value: String) = value.toByteArray(Charsets.UTF_8).joinToString("") { "%02X".format(it.toInt() and 255) }
        val candidates = values.filter { it.getJSONObject("chainId").getString("hex") == hex("solana-mainnet") }
        check(candidates.size == 2)
        val selected = candidates.single { it.getJSONObject("url").getString("hex") == hex(url) }
        val previous = candidates.single { it !== selected }
        check(previous.getJSONObject("url").getString("hex") == hex("https://api.mainnet-beta.solana.com"))
        check(previous.getJSONObject("isDefault").getLong("integer") == 1L)
        check(previous.getJSONObject("isActive").getLong("integer") == 1L)
        check(selected.getJSONObject("url").getString("hex") == hex(url))
        check(selected.getJSONObject("isDefault").getLong("integer") == 0L)
        check(selected.getJSONObject("isActive").getLong("integer") == 0L)
        previous.put("isActive", JSONObject().put("storage", "integer").put("integer", 0L))
        selected.put("isActive", JSONObject().put("storage", "integer").put("integer", 1L))
        table.put("rows", JSONArray(values.sortedBy(SavedEndpointInspectionRawSnapshot::canonical)))
        return result
    }
}
