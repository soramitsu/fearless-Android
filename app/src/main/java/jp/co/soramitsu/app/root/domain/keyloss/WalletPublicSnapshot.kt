package jp.co.soramitsu.app.root.domain.keyloss

/** Public database fields only. Unknown layouts and bounded reads are disclosed to the viewer. */
data class WalletPublicSnapshot(val wallets: List<Wallet>, val incomplete: Boolean) {
    data class Wallet(val name: String, val identities: List<Pair<String, String>>, val balances: List<Balance>)
    data class Balance(val network: String, val asset: String, val amount: String)
}
