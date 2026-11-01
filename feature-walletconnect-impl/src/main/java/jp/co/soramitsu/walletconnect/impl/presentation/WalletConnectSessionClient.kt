package jp.co.soramitsu.walletconnect.impl.presentation

import com.reown.walletkit.client.Wallet
import com.reown.walletkit.client.WalletKit

/** The Reown session calls signing depends on, kept apart so the signing rules run without the SDK. */
internal interface WalletConnectSessionClient {
    fun getActiveSessionByTopic(topic: String): Wallet.Model.Session?

    fun getPendingListOfSessionRequests(topic: String): List<Wallet.Model.SessionRequest>

    fun getVerifyContext(requestId: Long): Wallet.Model.VerifyContext?

    fun respondSessionRequest(
        params: Wallet.Params.SessionRequestResponse,
        onSuccess: (Wallet.Params.SessionRequestResponse) -> Unit,
        onError: (Wallet.Model.Error) -> Unit
    )
}

internal object WalletKitSessionClient : WalletConnectSessionClient {
    override fun getActiveSessionByTopic(topic: String): Wallet.Model.Session? {
        return WalletKit.getActiveSessionByTopic(topic)
    }

    override fun getPendingListOfSessionRequests(topic: String): List<Wallet.Model.SessionRequest> {
        return WalletKit.getPendingListOfSessionRequests(topic)
    }

    override fun getVerifyContext(requestId: Long): Wallet.Model.VerifyContext? {
        return WalletKit.getVerifyContext(requestId)
    }

    override fun respondSessionRequest(
        params: Wallet.Params.SessionRequestResponse,
        onSuccess: (Wallet.Params.SessionRequestResponse) -> Unit,
        onError: (Wallet.Model.Error) -> Unit
    ) {
        WalletKit.respondSessionRequest(params = params, onSuccess = onSuccess, onError = onError)
    }
}
