package jp.co.soramitsu.common.data.network.solana

/** Immutable encodings of an unsigned legacy message and its transaction with one empty signature. */
data class SolanaPaymentMessage(val messageBase64: String, val transactionBase64: String)
