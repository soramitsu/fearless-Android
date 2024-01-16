package jp.co.soramitsu.nft.impl.domain.usecase

import jp.co.soramitsu.nft.impl.domain.models.EthCall
import jp.co.soramitsu.nft.impl.domain.utils.map
import jp.co.soramitsu.nft.impl.domain.utils.nonNullWeb3j
import jp.co.soramitsu.runtime.multiNetwork.connection.EthereumWebSocketConnection
import kotlinx.coroutines.future.await
import org.web3j.abi.FunctionReturnDecoder
import org.web3j.abi.datatypes.Type
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.methods.request.Transaction

@Suppress("FunctionName")
suspend inline fun <T> EthereumWebSocketConnection.ExecuteEthFunction(
    transfer: EthCall,
    crossinline transform: (result: Type<*>?) -> T
): T {
    val response = when(transfer) {
        is EthCall.SmartContractCall ->
            nonNullWeb3j.ethCall(
                Transaction.createEthCallTransaction(
                    null,
                    transfer.contractAddress,
                    transfer.encodedFunction
                ),
                DefaultBlockParameterName.LATEST
            ).sendAsync().await()

        else -> error(
            """
                Token type unsupported.
            """.trimIndent()
        )
    }

    return response.map { result ->
        val decodedOutputTypes = FunctionReturnDecoder.decode(
            result,
            transfer.outputTypeRefs
        )

        if (transfer.outputTypeRefs.size != decodedOutputTypes.size)
            error(
                """
                    Result obtained is not decodable.
                """.trimIndent()
            )

        transform.invoke(decodedOutputTypes.firstOrNull())
    }
}