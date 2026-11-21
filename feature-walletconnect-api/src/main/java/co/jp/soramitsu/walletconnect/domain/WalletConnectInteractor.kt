package co.jp.soramitsu.walletconnect.domain

import co.jp.soramitsu.walletconnect.model.WalletConnectProposal
import co.jp.soramitsu.walletconnect.model.WalletConnectRequest
import co.jp.soramitsu.walletconnect.model.WalletConnectSession
import co.jp.soramitsu.walletconnect.model.WalletConnectVerifyContext
import jp.co.soramitsu.core.models.ChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain

@Suppress("ComplexInterface")
interface WalletConnectInteractor {

    suspend fun getChains(): List<Chain>

    suspend fun checkChainsSupported(proposal: WalletConnectProposal): Result<Boolean>

    suspend fun approveSession(
        proposal: WalletConnectProposal,
        selectedWalletIds: Set<Long>,
        selectedOptionalChainIds: Set<String>,
        onSuccess: () -> Unit = {},
        onError: (Throwable) -> Unit
    )

    fun rejectSession(
        proposal: WalletConnectProposal,
        onSuccess: () -> Unit = {},
        onError: (Throwable) -> Unit
    )

    fun silentRejectSession(
        proposal: WalletConnectProposal,
        onSuccess: () -> Unit = {},
        onError: (Throwable) -> Unit
    )

    suspend fun handleSignAction(
        chain: Chain,
        topic: String,
        recentSession: WalletConnectRequest,
        onSignError: (Exception) -> Unit,
        onRequestSuccess: (operationHash: String?, chainId: ChainId?) -> Unit,
        onRequestError: (Throwable) -> Unit
    )

    fun rejectSessionRequest(
        sessionTopic: String,
        requestId: Long,
        onSuccess: () -> Unit = {},
        onError: (Throwable) -> Unit
    )

    fun getActiveSessionByTopic(topic: String): WalletConnectSession?

    fun getPendingListOfSessionRequests(topic: String): List<WalletConnectRequest>

    /** Where the dApp that sent request [requestId] runs, as WalletConnect Verify attested it. */
    fun getVerifyContext(topic: String, requestId: Long): WalletConnectVerifyContext?

    fun disconnectSession(
        topic: String,
        onSuccess: () -> Unit = {},
        onError: (Throwable) -> Unit
    )

    fun pair(
        pairingUri: String,
        onSuccess: () -> Unit = {},
        onError: (Throwable) -> Unit = {}
    )
}
