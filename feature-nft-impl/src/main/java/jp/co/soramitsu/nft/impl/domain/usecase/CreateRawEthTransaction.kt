package jp.co.soramitsu.nft.impl.domain.usecase

import jp.co.soramitsu.nft.impl.domain.models.EIP1559Call
import jp.co.soramitsu.nft.impl.domain.models.EIP1559CallImpl
import jp.co.soramitsu.nft.impl.domain.models.EthCall
import jp.co.soramitsu.runtime.multiNetwork.connection.EthereumWebSocketConnection
import org.web3j.crypto.RawTransaction
import org.web3j.crypto.transaction.type.Transaction1559
import java.math.BigInteger

@Suppress("FunctionName")
suspend fun EthereumWebSocketConnection.CreateRawEthTransaction(
    call: EthCall
): RawTransaction {
    return when(call) {
        is EthCall.SmartContractCall ->
            EIP1559CallImpl.createAsync(
                ethConnection = this,
                call = call,
                estimateGas = EstimateEthTransactionGas(
                    call = call
                )
            )

        else -> error(
            """
                Unknown transfer type.
            """.trimIndent()
        )
    }.convertToWeb3RawTransaction()
}

private fun <T: EthCall> EIP1559Call<T>.convertToWeb3RawTransaction(): RawTransaction {
    return when(call) {
        is EthCall.SmartContractCall ->
            RawTransaction.createTransaction(
                call.chainId,
                call.nonce,
                estimateGas,
                (call as EthCall.SmartContractCall).contractAddress,
                BigInteger.ZERO,
                (call as EthCall.SmartContractCall).encodedFunction,
                maxPriorityFeePerGas,
                baseFeePerGas.plus(maxPriorityFeePerGas)
            ).apply {
                println("This is checkpoint: nonce - $nonce, estimateGas - $estimateGas, maxPriorityFeePerGas - ${(transaction as? Transaction1559)?.maxPriorityFeePerGas}, maxFeePerGas - ${(transaction as? Transaction1559)?.maxFeePerGas}")
            }

        else -> error(
            """
                Unknown transfer type.
            """.trimIndent()
        )
    }
}