package jp.co.soramitsu.nft.impl.domain.usecase.eth

import jp.co.soramitsu.nft.impl.domain.models.transfer.EthCall
import jp.co.soramitsu.nft.impl.domain.utils.map
import jp.co.soramitsu.nft.impl.domain.utils.nonNullWeb3j
import jp.co.soramitsu.runtime.multiNetwork.connection.EthereumChainConnection
import kotlinx.coroutines.future.await
import org.web3j.protocol.core.methods.request.Transaction
import org.web3j.utils.Numeric
import java.math.BigInteger

@Suppress("FunctionName")
suspend fun EthereumChainConnection.EstimateEthTransactionGas(call: EthCall): BigInteger {
    val response = nonNullWeb3j.ethEstimateGas(
        call.convertToWeb3Transaction()
    ).sendAsync().await()

    return response.map { Numeric.decodeQuantity(it) }
}

@Suppress("UseIfInsteadOfWhen")
private fun EthCall.convertToWeb3Transaction(): Transaction {
    return when (this) {
        is EthCall.SmartContractCall ->
            Transaction.createFunctionCallTransaction(
                // from
                sender,
                // nonce
                nonce,
                // gasPrice
                null,
                // gasLimit
                null,
                // to
                contractAddress,
                // value
                null,
                // data
                encodedFunction
            )

        else -> error(
            """
                Unknown transfer type.
            """.trimIndent()
        )
    }
}
