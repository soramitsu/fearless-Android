package jp.co.soramitsu.app.root.domain.keyloss

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.os.CancellationSignal
import dagger.hilt.android.qualifiers.ApplicationContext
import java.math.BigDecimal
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/** Reads bounded public columns through SQLite OPEN_READONLY. Never opens Room or reads secrets. */
class WalletPublicSnapshotReader @Inject constructor(@ApplicationContext private val context: Context) {
    fun read(): WalletPublicSnapshot {
        val file = context.getDatabasePath("app.db")
        if (!file.exists()) return WalletPublicSnapshot(emptyList(), false)
        // Bound the public projections and query deadline, not unrelated cache bytes.
        val cancellation = CancellationSignal()
        val deadline = deadlines.schedule({ cancellation.cancel() }, 3, TimeUnit.SECONDS)
        try {
            return SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { database ->
                readWallets(database, cancellation)
            }
        } finally { deadline.cancel(false) }
    }

    private fun readWallets(database: SQLiteDatabase, cancellation: CancellationSignal): WalletPublicSnapshot {
        var incomplete = false
        val wallets = mutableListOf<WalletPublicSnapshot.Wallet>()
        var remainingBalances = 1000
        for (table in listOf("meta_accounts", "users", "legacy_users_recovery")) {
            val columns = columns(database, table, cancellation)
            if (columns.isEmpty()) continue
            if (table != "meta_accounts" && !knownLegacyPublicSnapshotColumns(table, columns)) incomplete = true
            val name = if ("name" in columns) "name" else "username"
            val fields = listOf(name, "id", "address", "substrateAccountId", "ethereumAddress", "tonPublicKey", "publicKey")
                .filter { it in columns }
            if (name !in columns || fields.size < 2) { incomplete = true; continue }
            val projection = fields.joinToString(transform = ::bounded)
            val truncation = fields.joinToString(" OR ") { "length(CAST(`$it` AS BLOB))>2048" }
            database.rawQuery("SELECT $projection, ($truncation) AS too_large FROM `$table` LIMIT 1001", null, cancellation).use { cursor ->
                while (cursor.moveToNext()) {
                    if (wallets.size >= 1000) { incomplete = true; break }
                    if (cursor.getInt(cursor.getColumnIndexOrThrow("too_large")) != 0) incomplete = true
                    val identities = fields.filterNot { it == name || it == "id" }.mapNotNull { field ->
                        val index = cursor.getColumnIndexOrThrow(field)
                        if (cursor.getType(index) == Cursor.FIELD_TYPE_BLOB && cursor.getBlob(index).size > 128) incomplete = true
                        cursor.value(field)?.let { field to it }
                    }
                    if (identities.isEmpty()) incomplete = true
                    if (table != "meta_accounts" && !hasRequiredLegacyPublicSnapshotValues(cursor.value(name), identities)) {
                        incomplete = true
                    }
                    val metaId = if (table == "meta_accounts" && "id" in fields) cursor.value("id") else null
                    if (table == "meta_accounts" && metaId == null) incomplete = true
                    val balances = try { readBalances(database, metaId, remainingBalances, cancellation) } catch (_: Exception) {
                        cancellation.throwIfCanceled()
                        incomplete = true
                        emptyList()
                    }
                    if (balances.size > remainingBalances) incomplete = true
                    val shownBalances = balances.take(remainingBalances)
                    remainingBalances -= shownBalances.size
                    wallets += WalletPublicSnapshot.Wallet(cursor.value(name).orEmpty(), identities, shownBalances)
                }
            }
        }
        if (wallets.isEmpty()) incomplete = true
        return WalletPublicSnapshot(wallets, incomplete)
    }

    private fun readBalances(
        database: SQLiteDatabase, metaId: String?, remaining: Int, cancellation: CancellationSignal
    ): List<WalletPublicSnapshot.Balance> {
        if (metaId == null) return emptyList()
        val columns = columns(database, "assets", cancellation)
        if (columns.isEmpty()) return emptyList()
        require(columns.containsAll(listOf("metaId", "chainId", "id", "freeInPlanks", "reservedInPlanks")))
        val metadata = columns(database, "chain_assets", cancellation).containsAll(listOf("chainId", "id", "symbol", "precision"))
        val fields = listOf("a.chainId", "a.id", "a.freeInPlanks", "a.reservedInPlanks") +
            if (metadata) listOf("c.symbol", "c.precision") else emptyList()
        val from = if (metadata) "assets a LEFT JOIN chain_assets c ON a.chainId=c.chainId AND a.id=c.id" else "assets a"
        val sql = "SELECT ${fields.joinToString(transform = ::bounded)} FROM $from WHERE a.metaId=? LIMIT ${remaining + 1}"
        return database.rawQuery(sql, arrayOf(metaId), cancellation).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val free = cursor.value("freeInPlanks")?.toBigIntegerOrNull()
                    val reserved = cursor.value("reservedInPlanks")?.toBigIntegerOrNull()
                    val total = if (free != null && free.signum() >= 0 && reserved != null && reserved.signum() >= 0) free + reserved else null
                    val precision = if (metadata) cursor.value("precision")?.toIntOrNull()?.takeIf { it in 0..38 } else null
                    val symbol = if (metadata && precision != null) cursor.value("symbol") else null
                    val amount = if (total != null && precision != null && symbol != null) {
                        "${BigDecimal(total, precision).stripTrailingZeros().toPlainString()} $symbol"
                    } else "${total ?: "?"}"
                    add(WalletPublicSnapshot.Balance(cursor.value("chainId").orEmpty(), cursor.value("id").orEmpty(), amount))
                }
            }
        }
    }

    private fun columns(database: SQLiteDatabase, table: String, cancellation: CancellationSignal): Set<String> =
        database.rawQuery("PRAGMA table_info(`$table`)", null, cancellation).use { cursor ->
            buildSet { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name"))) }
        }

    private fun bounded(column: String): String =
        "CASE WHEN length(CAST($column AS BLOB))<=2048 THEN $column ELSE NULL END AS `${column.substringAfterLast('.')}`"

    private fun Cursor.value(field: String): String? {
        val index = getColumnIndexOrThrow(field)
        return when (getType(index)) {
            Cursor.FIELD_TYPE_NULL -> null
            Cursor.FIELD_TYPE_BLOB -> getBlob(index).takeIf { it.size <= 128 }?.joinToString("") { "%02x".format(it) }
            else -> getString(index)
        }
    }

    private companion object {
        val deadlines = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "wallet-public-snapshot-deadline").apply { isDaemon = true }
        }
    }
}

/** Known public-reader column families only; this does not validate or migrate an archive. */
internal fun knownLegacyPublicSnapshotColumns(table: String, columns: Set<String>): Boolean {
    val identity = setOf("address", "username", "publicKey", "cryptoType", "position")
    return when (table) {
        "users" -> columns == identity || columns == identity + "networkType"
        "legacy_users_recovery" -> columns == identity + "recoveryState"
        else -> false
    }
}

/** A partial legacy identity stays visible but must not be presented as complete. */
internal fun hasRequiredLegacyPublicSnapshotValues(name: String?, identities: List<Pair<String, String>>): Boolean =
    name != null && identities.any { it.first == "address" } && identities.any { it.first == "publicKey" }
