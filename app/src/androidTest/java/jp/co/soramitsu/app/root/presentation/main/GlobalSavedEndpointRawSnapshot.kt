package jp.co.soramitsu.app.root.presentation.main

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import jp.co.soramitsu.common.model.UniversalWalletRegistry
import org.json.JSONArray
import org.json.JSONObject

/** Full typed SQL evidence exclusively for the guarded fresh public global-inventory fixture. */
internal object GlobalSavedEndpointRawSnapshot {
    fun sql(database: SupportSQLiteDatabase): JSONObject {
        val schema = metadata(database, "SELECT type,name,tbl_name,sql FROM main.sqlite_master ORDER BY type,name")
        val tables = JSONObject()
        database.query("SELECT name FROM main.sqlite_master WHERE type='table' ORDER BY name LIMIT 101").use { cursor ->
            var count = 0
            while (cursor.moveToNext()) {
                check(++count <= 100)
                val name = cursor.getString(0)
                val identifier = quote(name)
                val columns = mutableListOf<String>()
                database.query("PRAGMA main.table_info($identifier)").use { info ->
                    while (info.moveToNext()) columns += info.getString(1)
                }
                check(columns.isNotEmpty() && columns.size <= 100)
                val projection = columns.joinToString(",") { column ->
                    val c = quote(column)
                    "typeof($c),CASE WHEN typeof($c) IN ('text','blob') THEN hex(CAST($c AS BLOB)) END," +
                        "CASE WHEN typeof($c)='integer' THEN $c END,CASE WHEN typeof($c)='real' THEN $c END"
                }
                val rows = mutableListOf<JSONObject>()
                database.query("SELECT $projection FROM main.$identifier LIMIT 201").use { data ->
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
                                "text", "blob" -> value.put("hex", data.getString(i + 1))
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
            .put("userVersion", metadata(database, "PRAGMA user_version"))
            .put("foreignKeys", metadata(database, "PRAGMA foreign_keys"))
    }

    /** Complete startup delta from the app-owned BTC/SOL definitions; every other cell stays exact. */
    fun expectedNativeBootstrap(snapshot: JSONObject): JSONObject {
        val expected = JSONObject(SavedEndpointInspectionRawSnapshot.canonical(snapshot))
        val tables = expected.getJSONObject("tables")
        val bitcoin = UniversalWalletRegistry.bitcoinMainnet
        val solana = UniversalWalletRegistry.solanaMainnet
        val chains = listOf(
            expectedNativeChain(bitcoin.id, bitcoin.name, "bitcoin", bitcoin.indexerBaseUrl, "BITCOIN"),
            expectedNativeChain(solana.id, solana.name, "solana", solana.indexerBaseUrl, "SOLANA")
        )
        val assets = listOf(
            expectedNativeAsset(bitcoin.id, bitcoin.name, bitcoin.nativeAsset.id, bitcoin.nativeAsset.symbol,
                bitcoin.nativeAsset.decimals, "bitcoin"),
            expectedNativeAsset(solana.id, solana.name, solana.nativeAsset.id, solana.nativeAsset.symbol,
                solana.nativeAsset.decimals, "solana")
        )
        val nodes = listOf(mapOf<String, Any?>("chainId" to solana.id, "url" to solana.rpcUrl,
            "name" to solana.name, "isActive" to 1, "isDefault" to 1))
        for ((table, additions) in listOf("chains" to chains, "chain_assets" to assets, "chain_nodes" to nodes)) {
            val existing = tables.getJSONObject(table)
            check(existing.getJSONArray("rows").length() == 0) { "Bootstrap oracle requires an empty catalog" }
            val columns = existing.getJSONArray("columns")
            val names = (0 until columns.length()).map(columns::getString).toSet()
            val rows = additions.map { values ->
                check(values.keys == names) { "Native bootstrap oracle column set differs" }
                JSONObject().also { row ->
                    values.forEach { (name, value) ->
                        row.put(name, JSONObject().also { cell ->
                            when (value) {
                                null -> cell.put("storage", "null")
                                is Int -> cell.put("storage", "integer").put("integer", value)
                                is String -> cell.put("storage", "text").put("hex", value.toByteArray(Charsets.UTF_8)
                                    .joinToString("") { "%02X".format(it.toInt() and 0xff) })
                                else -> error("Unsupported bootstrap oracle type")
                            }
                        })
                    }
                }
            }
            existing.put("rows", JSONArray(rows.sortedBy(SavedEndpointInspectionRawSnapshot::canonical)))
        }
        return expected
    }

    private fun expectedNativeChain(id: String, name: String, icon: String, history: String, historyType: String): Map<String, Any?> =
        mapOf("id" to id, "paraId" to null, "parentId" to null, "rank" to 1, "name" to name,
            "minSupportedVersion" to null, "icon" to "file:///android_asset/native-icons/$icon.svg", "prefix" to 0,
            "isEthereumBased" to 0, "isTestNet" to 0, "hasCrowdloans" to 0, "supportStakingPool" to 0,
            "isEthereumChain" to 0, "isChainlinkProvider" to 0, "supportNft" to 0, "isUsesAppId" to 0,
            "identityChain" to null, "ecosystem" to "Substrate", "androidMinAppVersion" to null,
            "remoteAssetsSource" to null, "tonBridgeUrl" to null, "xcm" to null,
            "staking_url" to null, "staking_type" to null, "history_url" to history, "history_type" to historyType,
            "crowdloans_url" to null, "crowdloans_type" to null)

    private fun expectedNativeAsset(chainId: String, name: String, id: String, symbol: String, precision: Int, icon: String): Map<String, Any?> =
        mapOf("id" to id, "name" to name, "symbol" to symbol, "chainId" to chainId,
            "icon" to "file:///android_asset/native-icons/$icon.svg", "priceId" to icon, "staking" to "UNSUPPORTED",
            "precision" to precision, "purchaseProviders" to null, "isUtility" to 1, "type" to "Normal",
            "currencyId" to null, "existentialDeposit" to null, "color" to null, "isNative" to 1,
            "priceProvider" to null, "coinbaseUrl" to null)

    /** Exact expected delta from the one declared test-only optimistic revision increment. */
    fun incrementedRevision(snapshot: JSONObject, rowId: Long, old: Long, new: Long): JSONObject {
        check(new == Math.addExact(old, 1L))
        val expected = JSONObject(SavedEndpointInspectionRawSnapshot.canonical(snapshot))
        val rows = expected.getJSONObject("tables").getJSONObject("custom_endpoint_records").getJSONArray("rows")
        check(rows.length() == 1)
        val row = rows.getJSONObject(0)
        check(row.getJSONObject("id").getString("storage") == "integer")
        check(row.getJSONObject("id").getLong("integer") == rowId)
        val revision = row.getJSONObject("revision")
        check(revision.getString("storage") == "integer" && revision.getLong("integer") == old)
        revision.put("integer", new)
        return expected
    }

    fun protectedProjection(snapshot: JSONObject, marker: String): JSONObject {
        val preferences = snapshot.getJSONObject("preferences")
        val fearless = preferences.getJSONObject("fearless_prefs")
        val key = preferences.getJSONObject("key_alias")
        val fixture = preferences.getJSONObject(marker)
        return JSONObject().put("sql", snapshot.getJSONObject("sql"))
            .put("noWalletCounts", snapshot.getJSONObject("noWalletCounts"))
            .put("selectedAccount", snapshot.getBoolean("selectedAccount"))
            .put("pinCode", fearless.getJSONObject("typedMap").getJSONObject("pin_code"))
            .put("wrappedKeyTypedMap", key.getJSONObject("typedMap"))
            .put("wrappedKeyXmlSHA256", key.getString("rawXmlSHA256"))
            .put("markerTypedMap", fixture.getJSONObject("typedMap"))
            .put("markerXmlSHA256", fixture.getString("rawXmlSHA256"))
    }

    private fun metadata(database: SupportSQLiteDatabase, query: String): JSONArray = database.query(query).use { cursor ->
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
