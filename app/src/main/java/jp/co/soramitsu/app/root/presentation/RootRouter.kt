package jp.co.soramitsu.app.root.presentation

interface RootRouter {
    fun returnToWallet()

    fun openPincodeCheck()

    fun openNavGraph()
    fun openWalletConnectSessionProposal(pairingTopic: String?)
    fun openWalletConnectSessionRequest(sessionRequestTopic: String)
}
