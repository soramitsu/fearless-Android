package jp.co.soramitsu.wallet.impl.data.repository.tranfser

import jp.co.soramitsu.common.data.network.send.ReviewedWalletSend
import jp.co.soramitsu.common.data.network.send.PendingWalletSend

/** Public recovery presentation; signed transaction bytes stay inside the durable journal. */
data class NativeSendRecoverySnapshot(
    val walletId: Long,
    val chainId: String,
    val sender: String,
    val selectionEpoch: Long,
    val transactionId: String,
    val state: PendingWalletSend.State,
    val expired: Boolean = false,
    val reviewed: ReviewedWalletSend? = null,
    val feeUnits: String = "0",
    val accountRentUnits: String = "0",
    val kind: PendingWalletSend.Kind = PendingWalletSend.Kind.Bitcoin,
    val solanaNetwork: jp.co.soramitsu.common.data.network.send.SolanaSendNetworkIdentity? = null,
    val currentSolanaGenesisHash: String? = null
) {
    /** Legacy records remain visible but cannot authorize inferred-network I/O. */
    val networkAvailable: Boolean get() = kind != PendingWalletSend.Kind.Solana ||
        solanaNetwork != null && solanaNetwork.registryGenesisHash == currentSolanaGenesisHash
}
