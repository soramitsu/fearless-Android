package jp.co.soramitsu.nft.impl.domain.usecase.eth

import jp.co.soramitsu.nft.impl.domain.utils.map
import jp.co.soramitsu.nft.impl.domain.utils.nonNullWeb3j
import jp.co.soramitsu.runtime.multiNetwork.connection.EthereumChainConnection
import jp.co.soramitsu.shared_utils.encrypt.keypair.Keypair
import jp.co.soramitsu.shared_utils.extensions.toHexString
import kotlinx.coroutines.future.await
import org.web3j.crypto.Credentials
import org.web3j.crypto.RawTransaction
import org.web3j.crypto.TransactionEncoder

@Suppress("FunctionName")
suspend fun EthereumChainConnection.SendRawEthTransaction(keypair: Keypair, transaction: RawTransaction): String {
    val signedRawTransaction = SignTransaction(transaction, keypair)
    val txResult = nonNullWeb3j.ethSendRawTransaction(signedRawTransaction).sendAsync().await()

    return txResult.map { it }
}

@Suppress("FunctionName")
private fun SignTransaction(transaction: RawTransaction, keypair: Keypair): String {
    val encodedTx = TransactionEncoder.signMessage(
        transaction,
        Credentials.create(keypair.privateKey.toHexString(true))
    )

    return encodedTx.toHexString(true)
}
