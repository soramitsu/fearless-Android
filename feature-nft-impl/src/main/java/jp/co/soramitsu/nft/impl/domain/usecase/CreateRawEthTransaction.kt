package jp.co.soramitsu.nft.impl.domain.usecase

import jp.co.soramitsu.nft.impl.domain.models.EIP1559Call
import jp.co.soramitsu.nft.impl.domain.models.EIP1559CallImpl
import jp.co.soramitsu.nft.impl.domain.models.EthCall
import jp.co.soramitsu.runtime.multiNetwork.connection.EthereumWebSocketConnection
import org.web3j.crypto.RawTransaction
import java.math.BigInteger

@Suppress("FunctionName")
suspend fun EthereumWebSocketConnection.CreateRawEthTransaction(
    transfer: EthCall
): RawTransaction {
    return when(transfer) {
        is EthCall.SmartContractCall ->
            EIP1559CallImpl.createAsync(
                ethConnection = this,
                transfer = transfer,
                estimateGas = EstimateEthTransactionGas(
                    transfer = transfer
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
    return when(transfer) {
        is EthCall.SmartContractCall ->
            RawTransaction.createTransaction(
                transfer.chainId,
                transfer.nonce,
                estimateGas,
                (transfer as EthCall.SmartContractCall).contractAddress,
                BigInteger.ZERO,
                (transfer as EthCall.SmartContractCall).encodedFunction,
                maxPriorityFeePerGas,
                baseFeePerGas.plus(maxPriorityFeePerGas)
            )

        else -> error(
            """
                Unknown transfer type.
            """.trimIndent()
        )
    }
}