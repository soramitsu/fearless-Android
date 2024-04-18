package co.jp.soramitsu.walletconnect.domain

import com.walletconnect.web3.wallet.client.Wallet
import jp.co.soramitsu.core.models.ChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain

@Suppress("ComplexInterface")
interface WalletConnectInteractor {

    suspend fun getChains(): List<Chain>

    suspend fun checkChainsSupported(proposal: Wallet.Model.SessionProposal): Result<Boolean>

    suspend fun approveSession(
        proposal: Wallet.Model.SessionProposal,
        selectedWalletIds: Set<Long>,
        selectedOptionalChainIds: Set<String>,
        onSuccess: (Wallet.Params.SessionApprove) -> Unit = {},
        onError: (Wallet.Model.Error) -> Unit
    )

    fun rejectSession(
        proposal: Wallet.Model.SessionProposal,
        onSuccess: (Wallet.Params.SessionReject) -> Unit = {},
        onError: (Wallet.Model.Error) -> Unit
    )

    fun silentRejectSession(
        proposal: Wallet.Model.SessionProposal,
        onSuccess: (Wallet.Params.SessionReject) -> Unit = {},
        onError: (Wallet.Model.Error) -> Unit
    )

    suspend fun handleSignAction(
        chain: Chain,
        topic: String,
        recentSession: Wallet.Model.SessionRequest,
        onSignError: (Exception) -> Unit,
        onRequestSuccess: (operationHash: String?, chainId: ChainId?) -> Unit,
        onRequestError: (Wallet.Model.Error) -> Unit
    )

    fun rejectSessionRequest(
        sessionTopic: String,
        requestId: Long,
        onSuccess: (Wallet.Params.SessionRequestResponse) -> Unit = {},
        onError: (Wallet.Model.Error) -> Unit
    )

    fun getActiveSessionByTopic(topic: String): Wallet.Model.Session?

    fun getPendingListOfSessionRequests(topic: String): List<Wallet.Model.SessionRequest>
    fun disconnectSession(
        topic: String,
        onSuccess: (Wallet.Params.SessionDisconnect) -> Unit = {},
        onError: (Wallet.Model.Error) -> Unit
    )

    fun pair(
        pairingUri: String,
        onSuccess: (Wallet.Params.Pair) -> Unit = {},
        onError: (Wallet.Model.Error) -> Unit = {}
    )
}
