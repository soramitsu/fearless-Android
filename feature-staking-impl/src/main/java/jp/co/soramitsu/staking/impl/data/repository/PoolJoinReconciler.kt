package jp.co.soramitsu.staking.impl.data.repository

import jp.co.soramitsu.core.rpc.FinalizedExtrinsicReader
import java.math.BigInteger

/** Reads only the original operation. Neither a miss nor an acknowledged RPC reply frees its scope. */
class PoolJoinReconciler(private val journal: PoolJoinJournal, private val reader: FinalizedExtrinsicReader) {
    /** [onFinalized] consumes the exact caller claim synchronously, before cancellable dispatch-back. */
    suspend fun check(
        scope: PoolJoinJournal.Scope,
        attemptId: String,
        beforeHeight: BigInteger? = null,
        onFinalized: () -> Unit
    ): Result = journal.withScope(scope) {
        val payment = checkNotNull(journal.read(scope)) { "Pending pool transaction is unavailable" }
        check(payment.attemptId == attemptId) { "Pending pool transaction changed" }
        val anchor = checkNotNull(payment.anchor) { "Original finalized anchor is unavailable" }
        val search = reader.search(scope.chainId, payment.signedExtrinsic, beforeHeight, after = anchor)
        search.inclusion?.let { inclusion ->
            journal.finalize(payment, inclusion)
            onFinalized()
        }
        Result(payment, search)
    }

    data class Result(val payment: PoolJoinJournal.Payment, val search: FinalizedExtrinsicReader.SearchResult)
}
