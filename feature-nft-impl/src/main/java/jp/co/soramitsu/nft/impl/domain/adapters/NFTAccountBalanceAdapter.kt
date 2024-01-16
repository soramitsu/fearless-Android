package jp.co.soramitsu.nft.impl.domain.adapters

import jp.co.soramitsu.nft.domain.models.NFTCollection
import jp.co.soramitsu.nft.impl.domain.models.NFTCall
import jp.co.soramitsu.shared_utils.extensions.requireHexPrefix
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.tx.Contract
import java.math.BigDecimal
import java.math.BigInteger

@Suppress("FunctionName")
fun NFTAccountBalanceAdapter(
    chainId: Long,
    sender: String,
    token: NFTCollection.NFT.Full,
): NFTCall.AccountBalance {
    val tokenId = token.tokenId?.requireHexPrefix()?.drop(2) ?: error(
        """
            TokenId supplied is null.
        """.trimIndent()
    )

    return when(token.tokenType) {
        "ERC721" -> {
            val functionCall = Function(
                "balanceOf",
                listOf(Address(160, sender)),
                listOf(TypeReference.create(Uint256::class.java))
            )

            Contract.BIN_NOT_PROVIDED

            NFTCall.AccountBalance(
                chainId = chainId,
                nonce = BigInteger.ZERO,
                sender = sender,
                receiver = "",
                amount = BigDecimal.ZERO,
                contractAddress = token.contractAddress!!,
                encodedFunction = FunctionEncoder.encode(functionCall),
                outputTypeRefs = functionCall.outputParameters
            )
        }

        "ERC1155" -> {
            val functionCall = Function(
                "balanceOf",
                listOf(
                    Address(160, sender),
                    Uint256(BigInteger(tokenId, 16))
                ),
                listOf(TypeReference.create(Uint256::class.java))
            )

            NFTCall.AccountBalance(
                chainId = chainId,
                nonce = BigInteger.ZERO,
                sender = sender,
                receiver = "",
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
