package jp.co.soramitsu.wallet.api.presentation

interface WalletRouter {

    fun backWithResult(vararg results: Pair<String, Any?>)

    companion object {
        const val KEY_CHAIN_ID = "chain_id"
        const val KEY_ASSET_ID = "asset_id"
    }
}
