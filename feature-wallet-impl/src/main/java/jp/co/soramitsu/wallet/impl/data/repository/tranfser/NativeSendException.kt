package jp.co.soramitsu.wallet.impl.data.repository.tranfser

/** Typed review and pending-state failures for localized send UI. */
class NativeSendException(val code: Code, val transactionId: String? = null) : IllegalStateException(code.name) {
    enum class Code {
        SIMULATION_FAILED, REVIEW_REQUIRED, WALLET_CHANGED, PAYMENT_PENDING, PAYMENT_FAILED, QUOTE_EXPIRED, STORAGE_UNAVAILABLE,
        UNSUPPORTED_ACCOUNT,
        /** No node received the payment; its record was removed and nothing left the wallet. */
        PAYMENT_NOT_SENT,
        /** A node refused the payment before relaying it; its record was removed and nothing left the wallet. */
        PAYMENT_REFUSED,
        /** The payment's blockhash expired with no trace on the network; its record was removed. */
        PAYMENT_EXPIRED,
        /** The payment expired with no trace, but too long ago for that to show nothing left the wallet; its record was removed. */
        PAYMENT_UNVERIFIED,
        /** An earlier payment expired with no trace and its record was removed; this payment was not sent. */
        PRIOR_PAYMENT_EXPIRED,
        /** An earlier payment's record was removed although its outcome could not be verified; this payment was not sent. */
        PRIOR_PAYMENT_UNVERIFIED
    }
}

/**
 * Proof that a retained payment can never land, raised by a network's submit or status step: nothing reached a
 * node, a node refused it before relaying, or it expired unseen. Only the executor decides what the proof allows.
 * For [Reason.EXPIRED], [corroborated] says whether at least two endpoints proved the expiry on their own; a single
 * node's proof never alone shows that nothing left the wallet.
 */
class NativeSendNeverLandedException(val reason: Reason, val corroborated: Boolean = false) : Exception(reason.name) {
    enum class Reason { NOT_SENT, REFUSED, EXPIRED }
}
