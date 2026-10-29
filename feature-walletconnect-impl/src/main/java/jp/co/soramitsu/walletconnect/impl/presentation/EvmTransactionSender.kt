package jp.co.soramitsu.walletconnect.impl.presentation

import jp.co.soramitsu.runtime.multiNetwork.connection.EthereumConnectionPool
import jp.co.soramitsu.wallet.impl.data.network.blockchain.resultOrThrow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Broadcasts an already signed EVM transaction, so a retry repeats these bytes instead of signing anew. */
internal interface EvmTransactionSender {
    suspend fun send(chainId: String, signedTransaction: String): String
}

internal class PoolEvmTransactionSender(private val connectionPool: EthereumConnectionPool) : EvmTransactionSender {
    override suspend fun send(chainId: String, signedTransaction: String): String = withContext(Dispatchers.IO) {
        val web3 = connectionPool.await(chainId).web3j
            ?: error("There is no connection established for chain with id = $chainId")
        web3.ethSendRawTransaction(signedTransaction).send().resultOrThrow()
    }
}
