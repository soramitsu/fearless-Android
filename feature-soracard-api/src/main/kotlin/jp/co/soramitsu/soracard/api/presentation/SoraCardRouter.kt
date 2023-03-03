package jp.co.soramitsu.soracard.api.presentation

interface SoraCardRouter {
    fun back()

    fun openGetMoreXor()

    fun openSwapTokensScreen(assetId: String, chainId: String)

    fun showBuyCrypto()

    fun openWebViewer(title: String, url: String)
}
