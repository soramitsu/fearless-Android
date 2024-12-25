package jp.co.soramitsu.app.root.presentation

import co.jp.soramitsu.tonconnect.model.DappModel
import co.jp.soramitsu.tonconnect.model.TonConnectSignRequest

interface RootRouter {
    fun returnToWallet()

    fun openPincodeCheck()

    fun openNavGraph()
    fun openWalletConnectSessionProposal(pairingTopic: String?)
    fun openWalletConnectSessionRequest(sessionRequestTopic: String)
    suspend fun openTonSignRequestWithResult(dapp: DappModel, method: String, signRequest: TonConnectSignRequest): Result<String>
}
