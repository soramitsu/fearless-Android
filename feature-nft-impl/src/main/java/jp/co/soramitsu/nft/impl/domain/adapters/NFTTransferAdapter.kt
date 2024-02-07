package jp.co.soramitsu.nft.impl.domain.adapters

import jp.co.soramitsu.nft.domain.models.NFT
import jp.co.soramitsu.nft.domain.models.NFTCollection
import jp.co.soramitsu.nft.impl.domain.models.NFTCall
import jp.co.soramitsu.nft.impl.domain.utils.getNonce
import jp.co.soramitsu.shared_utils.extensions.requireHexPrefix
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Array
import org.web3j.abi.datatypes.DynamicBytes
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.Type
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.protocol.Web3j
import java.math.BigDecimal
import java.math.BigInteger

@Suppress("FunctionName")
suspend fun NFTTransferAdapter(
    web3j: Web3j,
    sender: String,
    receiver: String,
    token: NFT.Full,
    canReceiverAcceptToken: Boolean
): NFTCall.Transfer {
    val nonce = web3j.getNonce(sender)

    if (token.tokenId < BigInteger.ZERO)
        error(
            """
                TokenId supplied is null.
            """.trimIndent()
        )

    return when(token.tokenType) {

        "ERC721" -> {
            /*
                safeTransferFrom ensures that tokens will not be sent to a user who can accept them
                but this comes with higher Gas(fees) prices

                So, if we are sure that tokens will be accept by received then we can reduce Gas price

                Applicable case: user has already transferred to an account and transaction succeeded,
                meaning that we can omit unnecessary check
             */
            val tokenTransferMethod = if (canReceiverAcceptToken)
                "transferFrom" else "safeTransferFrom"

            val argsList = mutableListOf<Type<*>>(
                Address(160, sender),
                Address(160, receiver),
                Uint256(token.tokenId)
            ).apply {
                if (!canReceiverAcceptToken)
                    add(DynamicBytes(ByteArray(0)))
            }

            val functionCall =
                Function(
                    tokenTransferMethod,
                    argsList,
                    emptyList()
                )

            NFTCall.Transfer(
                nonce = nonce,
                sender = sender,
                receiver = receiver,
                contractAddress = token.contractAddress,
                encodedFunction = FunctionEncoder.encode(functionCall),
                outputTypeRefs = functionCall.outputParameters
            )
        }

        "ERC1155" -> {
            val functionCall =
                Function(
                    "safeTransferFrom",
                    listOf(
                        Address(160, sender),
                        Address(160, receiver),
                        Uint256(token.tokenId),
                        Uint256(BigInteger.ONE),
                        DynamicBytes(byteArrayOf())
                    ),
                    emptyList()
                )

            NFTCall.Transfer(
                nonce = nonce,
                sender = sender,
                receiver = receiver,
                contractAddress = token.contractAddress,
                encodedFunction = FunctionEncoder.encode(functionCall),
                outputTypeRefs = functionCall.outputParameters
            )
        }

        else ->
            throw IllegalArgumentException(
                """
                    Token provided is not supported.
                """.trimIndent()
            )

    }
}