package jp.co.soramitsu.wallet.impl.data.repository.tranfser

import jp.co.soramitsu.common.data.network.send.WalletSendJournal

/** Exact public wallet/network/asset intent; token symbols never establish payment identity. */
data class NativeSendBinding(
    val metaId: Long,
    val chainId: String,
    val assetId: String,
    val currencyId: String?,
    val precision: Int,
    val sender: String,
    val recipient: String,
    val requestedRawAmount: String,
    val sendMax: Boolean,
    val selectionEpoch: Long = jp.co.soramitsu.common.data.storage.WalletSelectionEpoch.snapshot(),
    val solanaNetwork: jp.co.soramitsu.common.data.network.send.SolanaSendNetworkIdentity? = null
) {
    /** Provider changes do not create another payment slot for the same wallet and chain. */
    val scope: String get() = "$metaId:${chainId.length}:$chainId"

    /** Length-prefixed fields avoid ambiguous string joins and omit untrusted ticker text. */
    val fingerprint: String get() = WalletSendJournal.scopeHash((listOf(metaId.toString(), chainId, assetId, currencyId.orEmpty(),
        precision.toString(), sender, recipient, if (sendMax) "MAX" else requestedRawAmount) +
        (solanaNetwork?.let { listOf(it.registryGenesisHash, it.executionGenesisHash) } ?: emptyList())).joinToString("") { "${it.length}:$it" })
}
