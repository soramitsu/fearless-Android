package co.jp.soramitsu.walletconnect.domain

import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain

interface WalletConnectInteractor {
    fun getDapps()
    fun start()

    suspend fun getChains(): List<Chain>
}
