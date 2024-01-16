package jp.co.soramitsu.nft.impl.domain.usecase

import jp.co.soramitsu.nft.impl.domain.models.EthCall
import jp.co.soramitsu.nft.impl.domain.utils.map
import jp.co.soramitsu.nft.impl.domain.utils.nonNullWeb3j
import jp.co.soramitsu.runtime.multiNetwork.connection.EthereumWebSocketConnection
import kotlinx.coroutines.future.await
import org.web3j.protocol.core.methods.request.Transaction
import org.web3j.utils.Numeric
import java.math.BigInteger

@Suppress("FunctionName")
suspend fun EthereumWebSocketConnection.EstimateEthTransactionGas(
    transfer: EthCall
): BigInteger {
    val gasPrice = nonNullWeb3j.ethGasPrice().sendAsync().await().gasPrice

    val response = nonNullWeb3j.ethEstimateGas(
        transfer.convertToWeb3Transaction(gasPrice)
    ).sendAsync().await()

    return response.map { Numeric.decodeQuantity(it) }.also { println("This is checkpoint: estimatedGas - $it") }
}

private fun EthCall.convertToWeb3Transaction(gasPrice: BigInteger): Transaction {
    return when(this) {
        is EthCall.SmartContractCall ->
            Transaction.createFunctionCallTransaction(
                /* from */ contractAddress, // TODO should from always be contractAddress?
                /* nonce */ nonce,
                /* gasPrice */ gasPrice,
                /* gasLimit */ null,
                /* to */ receiver,
                /* value */ null,
                /* data */ encodedFunction
            )

        else -> error(
            """
                Unknown transfer type.
            """.trimIndent()
        )
    }
}