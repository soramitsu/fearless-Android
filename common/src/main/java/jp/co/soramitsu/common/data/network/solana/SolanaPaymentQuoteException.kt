package jp.co.soramitsu.common.data.network.solana

/** A changed or expired payment returns to review instead of changing signed intent. */
class SolanaPaymentQuoteException(val code: Code) : IllegalStateException(code.name) {
    enum class Code { REVIEW_CHANGED, BLOCKHASH_EXPIRED, FEE_UNAVAILABLE, INSUFFICIENT_TOKEN_BALANCE, UNSUPPORTED_TOKEN, TOO_MANY_TOKEN_ACCOUNTS }
}
