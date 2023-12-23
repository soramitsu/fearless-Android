package co.jp.soramitsu.walletconnect.domain

import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import org.web3j.crypto.RawTransaction

interface WalletConnectInteractor {

    suspend fun getChains(): List<Chain>

    suspend fun signRawTransaction(
        chain: Chain,
        rawTransaction: RawTransaction,
        privateKey: String
    ): Result<String>

    suspend fun sendRawTransaction(
        chain: Chain,
        rawTransaction: RawTransaction,
        privateKey: String
    ): Result<String>
}
