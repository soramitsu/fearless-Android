package jp.co.soramitsu.common.data.network.send

/** Public signed bytes retained before the first submission; contains no seed or private key. */
data class PendingWalletSend(
    val scopeHash: String,
    val quoteId: String,
    val fingerprint: String,
    val kind: Kind,
    val transactionId: String,
    val signedPayload: String,
    val feeUnits: String,
    val accountRentUnits: String,
    val lastValidBlockHeight: Long?,
    val createdAtMillis: Long,
    val state: State = State.Pending,
    val reviewed: ReviewedWalletSend? = null,
    val solanaNetwork: SolanaSendNetworkIdentity? = null
) {
    enum class Kind { Bitcoin, Solana }
    enum class State { Pending, Confirmed, Failed }
}
