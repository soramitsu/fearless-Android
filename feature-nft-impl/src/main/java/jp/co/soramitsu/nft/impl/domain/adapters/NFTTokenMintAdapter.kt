package jp.co.soramitsu.nft.impl.domain.adapters

import jp.co.soramitsu.nft.domain.models.NFTCollection
import jp.co.soramitsu.nft.impl.domain.models.NFTCall
import jp.co.soramitsu.shared_utils.extensions.requireHexPrefix
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.DynamicBytes
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.generated.Uint256
import java.math.BigDecimal
import java.math.BigInteger

@Suppress("FunctionName")
fun NFTTokenMintAdapter(
    chainId: Long,
    sender: String,
    receiver: String,
    amountToMint: BigInteger,
    token: NFTCollection.NFT.Full
): NFTCall.TokenMint {
    val tokenId = token.tokenId?.requireHexPrefix()?.drop(2) ?: error(
        """
            TokenId supplied is null.
        """.trimIndent()
    )

    return when(token.tokenType) {

        "ERC721" -> {
            val functionCall = Function(
                "_safeMint",
                listOf(
                    Address(160, receiver),
                    Uint256(BigInteger(tokenId, 16))
                ),
                emptyList()
            )

            NFTCall.TokenMint(
                chainId = chainId,
                nonce = BigInteger.ZERO,
                sender = sender,
                receiver = receiver,
                amount = BigDecimal.ZERO,
                contractAddress = token.contractAddress!!,
                encodedFunction = FunctionEncoder.encode(functionCall),
                outputTypeRefs = functionCall.outputParameters
            )
        }

        "ERC1155" -> {
            val functionCall = Function(
                "_safeMint",
                listOf(
                    Address(160, receiver),
                    Uint256(BigInteger(tokenId, 16)),
                    Uint256(amountToMint),
                    DynamicBytes(ByteArray(0))
                ),
                emptyList()
            )

            NFTCall.TokenMint(
                chainId = chainId,
                nonce = BigInteger.ZERO,
                sender = sender,
                receiver = receiver,
                amount = BigDecimal.ZERO,
                contractAddress = token.contractAddress!!,
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
