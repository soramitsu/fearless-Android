package jp.co.soramitsu.coredb.recovery.decision

import android.database.Cursor
import android.os.CancellationSignal
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteDatabase
import jp.co.soramitsu.common.model.UniversalWalletRegistry
import jp.co.soramitsu.coredb.recovery.endpointInteger

/** Uses only bounded identity fields of a present local catalog row, never its endpoint URLs. */
internal object EndpointDecisionTargetReader {
    fun read(db: SupportSQLiteDatabase, signal: CancellationSignal, chainId: String): EndpointDecisionTarget? {
        check(db.inTransaction()) { "Endpoint target capture requires a transaction" }
        EndpointDecisionLimits.requireText(chainId, EndpointDecisionLimits.TARGET_ID_BYTES, nonempty = true)
        signal.throwIfCanceled()
        return db.query(SimpleSQLiteQuery(
            "SELECT ${decisionTextProjection("id", EndpointDecisionLimits.TARGET_ID_BYTES)}," +
                "${decisionTextProjection("ecosystem", 64)}," +
                "CASE WHEN typeof(isEthereumBased)='integer' THEN isEthereumBased END," +
                "CASE WHEN typeof(isEthereumChain)='integer' THEN isEthereumChain END," +
                "CASE WHEN typeof(isTestNet)='integer' THEN isTestNet END," +
                "typeof(history_type),${decisionTextProjection("history_type", 64)} " +
                "FROM main.chains WHERE id=? LIMIT 2", arrayOf(chainId)
        ), signal).use { cursor ->
            signal.throwIfCanceled()
            if (!cursor.moveToFirst()) return@use null
            check(cursor.columnCount == 7)
            val actualId = cursor.decisionText(0, EndpointDecisionLimits.TARGET_ID_BYTES)
            check(actualId == chainId) { "Endpoint target identity differs" }
            val ecosystem = cursor.decisionText(1, 64)
            val ethereumBased = cursor.endpointInteger(2)
            val ethereumChain = cursor.endpointInteger(3)
            val testnet = cursor.endpointInteger(4)
            check(listOf(ethereumBased, ethereumChain, testnet).all { it in 0L..1L }) {
                "Endpoint target boolean metadata differs"
            }
            check(cursor.getType(5) == Cursor.FIELD_TYPE_STRING)
            val historyType = when (cursor.getString(5)) {
                "null" -> { check(cursor.isNull(6)); null }
                "text" -> cursor.decisionText(6, 64)
                else -> error("Endpoint target history metadata differs")
            }
            check(!cursor.moveToNext()) { "Endpoint target identity is ambiguous" }
            signal.throwIfCanceled()
            capture(actualId, ecosystem, ethereumBased == 1L, ethereumChain == 1L, testnet == 1L, historyType)
        }
    }

    /** Pure trusted-catalog classification, shared with focused contract tests. */
    internal fun capture(
        chainId: String,
        ecosystem: String,
        ethereumBased: Boolean,
        ethereumChain: Boolean,
        testnet: Boolean,
        historyType: String?
    ): EndpointDecisionTarget {
        EndpointDecisionLimits.requireText(chainId, EndpointDecisionLimits.TARGET_ID_BYTES, nonempty = true)
        EndpointDecisionLimits.requireText(ecosystem, 64)
        historyType?.let { EndpointDecisionLimits.requireText(it, 64) }
        val digest = EndpointDecisionDigest().apply {
            text("endpoint-decision-target-v1")
            text(chainId)
            text(ecosystem)
            integer(if (ethereumBased) 1 else 0)
            integer(if (ethereumChain) 1 else 0)
            integer(if (testnet) 1 else 0)
            integer(if (historyType == null) 0 else 1)
            historyType?.let(::text)
        }.finish()
        val solana = listOf(UniversalWalletRegistry.solanaMainnet to false, UniversalWalletRegistry.solanaDevnet to true)
            .singleOrNull { (network, expectedTestnet) ->
                chainId in setOf(network.id, network.chainId) && testnet == expectedTestnet
            }?.first
        val substrateId = chainId.removePrefix("0x")
        val (family, genesis) = when {
            ecosystem == "Substrate" && !ethereumBased && !ethereumChain && historyType == "SOLANA" && solana != null ->
                EndpointDecisionTarget.Family.SOLANA to solana.genesisHash
            ecosystem == "Substrate" && !ethereumChain && historyType != "SOLANA" &&
                substrateId.length == 64 && substrateId.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' } ->
                EndpointDecisionTarget.Family.SUBSTRATE to ("0x" + substrateId.lowercase())
            else -> EndpointDecisionTarget.Family.UNSUPPORTED to null
        }
        return EndpointDecisionTarget(chainId, digest, family, genesis)
    }
}
