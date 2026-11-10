package jp.co.soramitsu.tonconnect.impl.domain

/** Decides which wallet a new TON Connect connection belongs to. */
object TonConnectWalletBinding {

    /**
     * The wallet whose TON key the connect reply gave the dApp, which is the
     * wallet the user picked, not necessarily the selected one. [wallets] pairs
     * each wallet id with its TON public key. When several wallets share the
     * key the selected one wins; they sign identically.
     */
    fun boundWalletId(
        replyPublicKey: ByteArray,
        wallets: List<Pair<Long, ByteArray?>>,
        selectedWalletId: Long
    ): Long? {
        val matching = wallets
            .filter { (_, tonPublicKey) -> tonPublicKey != null && tonPublicKey.contentEquals(replyPublicKey) }
            .map { (walletId, _) -> walletId }
        return matching.firstOrNull { it == selectedWalletId } ?: matching.firstOrNull()
    }
}
