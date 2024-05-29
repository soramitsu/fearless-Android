package jp.co.soramitsu.nft.impl.domain.usecase.eth

import jp.co.soramitsu.nft.impl.domain.models.transfer.EIP1559Call
import jp.co.soramitsu.nft.impl.domain.models.transfer.EIP1559CallImpl
import jp.co.soramitsu.nft.impl.domain.models.transfer.EthCall
import jp.co.soramitsu.runtime.multiNetwork.connection.EthereumChainConnection
import org.web3j.crypto.RawTransaction
import java.math.BigInteger

@Suppress("FunctionName", "UseIfInsteadOfWhen")
suspend fun EthereumChainConnection.CreateRawEthTransaction(call: EthCall): RawTransaction {
    return when (call) {
        is EthCall.SmartContractCall ->
            EIP1559CallImpl.createAsync(
                ethConnection = this,
                call = call,
                estimateGas = estimateEthTransactionGas(
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

@Suppress("UseIfInsteadOfWhen")
private fun <T : EthCall> EIP1559Call<T>.convertToWeb3RawTransaction(): RawTransaction {
    return when (call) {
        is EthCall.SmartContractCall ->
            RawTransaction.createTransaction(
                chainId,
                call.nonce,
                estimateGas,
                (call as EthCall.SmartContractCall).contractAddress,
                BigInteger.ZERO,
                (call as EthCall.SmartContractCall).encodedFunction,
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
