package jp.co.soramitsu.walletconnect.impl.presentation

import co.jp.soramitsu.walletconnect.domain.WalletConnectInteractor
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.impl.data.network.blockchain.EthereumRemoteSource
import jp.co.soramitsu.wallet.impl.domain.model.Transfer

class WalletConnectInteractorImpl(
    private val chainsRepository: ChainsRepository,
    private val ethereumSource: EthereumRemoteSource
) : WalletConnectInteractor {

    override suspend fun getChains(): List<Chain> = chainsRepository.getChains()

    override suspend fun performTransfer(
        chain: Chain,
        transfer: Transfer,
        privateKey: String
    ): Result<String> = ethereumSource.performTransfer(
        chain, transfer, privateKey
    )
}