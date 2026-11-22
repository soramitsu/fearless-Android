package jp.co.soramitsu.app.root.presentation

import jp.co.soramitsu.core.models.ChainId
import jp.co.soramitsu.tonconnect.api.model.DappModel
import jp.co.soramitsu.tonconnect.api.model.TonConnectSignRequest

interface RootRouter {
    fun returnToWallet()

    fun openPincodeCheck()

    fun openNavGraph()

    /** Suspends until the screens dApp requests open on can be reached: not over the PIN, onboarding or start screens. */
    suspend fun awaitDappRoutes()

    fun openWalletConnectSessionProposal(pairingTopic: String?)

    /** Opens the IrohaConnect session screen, joining the session behind [link] when one is given. */
    fun openIrohaConnect(link: String?)
    fun openWalletConnectSessionRequest(sessionRequestTopic: String)
    suspend fun openTonSignRequestWithResult(dapp: DappModel, method: String, signRequest: TonConnectSignRequest): Result<Pair<String, String>>
    fun openOperationSuccess(operationHash: String?, chainId: ChainId?, customMessage: String?, customTitle: String?)
}
