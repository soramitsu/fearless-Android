package jp.co.soramitsu.walletconnect.impl.presentation

import co.jp.soramitsu.walletconnect.domain.WalletConnectInteractor
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.impl.data.network.blockchain.EthereumRemoteSource
import org.web3j.crypto.RawTransaction

class WalletConnectInteractorImpl(
    private val chainsRepository: ChainsRepository,
    private val ethereumSource: EthereumRemoteSource
) : WalletConnectInteractor {

    override suspend fun getChains(): List<Chain> = chainsRepository.getChains()

    override suspend fun signRawTransaction(
        chain: Chain,
        rawTransaction: RawTransaction,
        privateKey: String
    ): Result<String> = ethereumSource.signRawTransaction(
        chain, rawTransaction, privateKey
    )

    override suspend fun sendRawTransaction(
        chain: Chain,
        rawTransaction: RawTransaction,
        privateKey: String
    ): Result<String> = ethereumSource.sendRawTransaction(
        chain, rawTransaction, privateKey
    )
}
