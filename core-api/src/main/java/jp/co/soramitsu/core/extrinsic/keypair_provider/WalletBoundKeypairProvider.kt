package jp.co.soramitsu.core.extrinsic.keypair_provider

/** Binds both public signing metadata and physical secret lookup to one reviewed wallet. */
interface WalletBoundKeypairProvider : GuardedKeypairProvider {
    fun forWallet(walletId: Long): GuardedKeypairProvider
}
