package jp.co.soramitsu.nft.impl.domain.usecase.eth

import jp.co.soramitsu.nft.impl.domain.models.EthCall
import jp.co.soramitsu.nft.impl.domain.utils.map
import jp.co.soramitsu.nft.impl.domain.utils.nonNullWeb3j
import jp.co.soramitsu.runtime.multiNetwork.connection.EthereumWebSocketConnection
import kotlinx.coroutines.future.await
import org.web3j.abi.FunctionReturnDecoder
import org.web3j.abi.datatypes.Type
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.methods.request.Transaction

@Suppress("FunctionName", "UseIfInsteadOfWhen")
suspend inline fun <T> EthereumWebSocketConnection.ExecuteEthFunction(
    call: EthCall,
    crossinline transform: (result: Type<*>?) -> T
): T {
    val response = when (call) {
        is EthCall.SmartContractCall ->
            nonNullWeb3j.ethCall(
                Transaction.createEthCallTransaction(
                    null,
                    call.contractAddress,
                    call.encodedFunction
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
            call.outputTypeRefs
        )

        if (call.outputTypeRefs.size != decodedOutputTypes.size) {
            error(
                """
                    Result obtained is not decodable.
                """.trimIndent()
            )
        }

        transform.invoke(decodedOutputTypes.firstOrNull())
    }
}
