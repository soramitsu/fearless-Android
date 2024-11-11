package co.jp.soramitsu.walletconnect.domain

import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain

interface TonConnectInteractor {
    suspend fun connectRemoteApp(pairingUri: String)
    suspend fun getChain(): Chain
}