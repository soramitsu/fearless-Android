package jp.co.soramitsu.feature_account_impl.domain.walletconnect

import com.walletconnect.walletconnectv2.client.WalletConnect
import com.walletconnect.walletconnectv2.client.WalletConnectClient

class WalletConnectInteractor(
) {
    fun connect(init: WalletConnect.Params.Init, wc: String) {
        WalletConnectClient.initialize(init)
        WalletConnectClient.setWalletDelegate(walletConnectDelegate)
        val pair = WalletConnect.Params.Pair(wc)
        val pairListener = object: WalletConnect.Listeners.Pairing {
            override fun onSuccess(settledPairing: WalletConnect.Model.SettledPairing) {
                hashCode()
            }

            override fun onError(error: Throwable) {
                hashCode()
            }
        }
        WalletConnectClient.pair(pair, pairListener)
    }

    private val walletConnectDelegate = object : WalletConnectClient.WalletDelegate {
        override fun onSessionDelete(deletedSession: WalletConnect.Model.DeletedSession) {
            hashCode()
        }

        override fun onSessionNotification(sessionNotification: WalletConnect.Model.SessionNotification) {
            hashCode()
        }

        override fun onSessionProposal(sessionProposal: WalletConnect.Model.SessionProposal) {
            hashCode()
        }

        override fun onSessionRequest(sessionRequest: WalletConnect.Model.SessionRequest) {
            hashCode()
        }

    }
}
