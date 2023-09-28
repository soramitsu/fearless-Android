package jp.co.soramitsu.walletconnect.impl.presentation

import co.jp.soramitsu.walletconnect.domain.WalletConnectInteractor
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain

class WalletConnectInteractorImpl(
    private val chainsRepository: ChainsRepository
) : WalletConnectInteractor {
    override fun getDapps() {

    }

    override fun start() {

    }

    override suspend fun getChains(): List<Chain> = chainsRepository.getChains()

}