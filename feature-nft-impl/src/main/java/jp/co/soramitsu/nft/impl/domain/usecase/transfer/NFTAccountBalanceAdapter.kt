package jp.co.soramitsu.nft.impl.domain.usecase.transfer

import jp.co.soramitsu.nft.domain.models.NFT
import jp.co.soramitsu.nft.impl.domain.models.transfer.NFTCall
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.generated.Uint256
import java.math.BigInteger

private const val DEFAULT_ETH_ADDRESS_LENGTH = 160

@Suppress("FunctionName")
fun NFTAccountBalanceAdapter(sender: String, token: NFT): NFTCall.AccountBalance {
    if (token.contractAddress.isBlank()) {
        error(
            """
                ContractAddress supplied is incorrect.
            """.trimIndent()
        )
    }

    if (token.tokenId < BigInteger.ZERO) {
        error(
            """
                TokenId supplied is incorrect.
            """.trimIndent()
        )
    }

    return when (token.tokenType) {
        "ERC721" -> {
            val functionCall = Function(
                "balanceOf",
                listOf(Address(DEFAULT_ETH_ADDRESS_LENGTH, sender)),
                listOf(TypeReference.create(Uint256::class.java))
            )

            NFTCall.AccountBalance(
                nonce = BigInteger.ZERO,
                sender = sender,
                receiver = "",
                contractAddress = token.contractAddress,
                encodedFunction = FunctionEncoder.encode(functionCall),
                outputTypeRefs = functionCall.outputParameters
            )
        }

        "ERC1155" -> {
            val functionCall = Function(
                "balanceOf",
                listOf(
                    Address(DEFAULT_ETH_ADDRESS_LENGTH, sender),
                    Uint256(token.tokenId)
                ),
                listOf(TypeReference.create(Uint256::class.java))
            )

            NFTCall.AccountBalance(
                nonce = BigInteger.ZERO,
                sender = sender,
                receiver = "",
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
