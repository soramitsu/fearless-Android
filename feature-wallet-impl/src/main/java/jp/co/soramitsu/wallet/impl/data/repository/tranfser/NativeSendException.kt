package jp.co.soramitsu.wallet.impl.data.repository.tranfser

/** Typed review and pending-state failures for localized send UI. */
class NativeSendException(val code: Code, val transactionId: String? = null) : IllegalStateException(code.name) {
    enum class Code { SIMULATION_FAILED, REVIEW_REQUIRED, WALLET_CHANGED, PAYMENT_PENDING, PAYMENT_FAILED, QUOTE_EXPIRED, STORAGE_UNAVAILABLE, UNSUPPORTED_ACCOUNT }
}
