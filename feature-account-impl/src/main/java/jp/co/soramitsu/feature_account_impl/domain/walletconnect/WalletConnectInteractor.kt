package jp.co.soramitsu.feature_account_impl.domain.walletconnect

import com.walletconnect.walletconnectv2.client.WalletConnect
import com.walletconnect.walletconnectv2.client.WalletConnectClient

class WalletConnectInteractor(
) {
    fun connect(init: WalletConnect.Params.Init) {
        WalletConnectClient.initialize(init)
    }

}
