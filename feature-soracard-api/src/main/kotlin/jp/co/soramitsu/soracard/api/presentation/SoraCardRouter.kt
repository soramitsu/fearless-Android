package jp.co.soramitsu.soracard.api.presentation

interface SoraCardRouter {
    fun back()

    fun openGetMoreXor()

    fun openSwapTokensScreen(chainId: String, assetIdFrom: String?, assetIdTo: String?)

    fun showBuyCrypto()

    fun openWebViewer(title: String, url: String)
}
