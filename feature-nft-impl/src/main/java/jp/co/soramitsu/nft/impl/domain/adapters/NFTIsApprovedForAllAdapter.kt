package jp.co.soramitsu.nft.impl.domain.adapters

import jp.co.soramitsu.nft.domain.models.NFTCollection
import jp.co.soramitsu.nft.impl.domain.models.NFTCall
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Bool
import org.web3j.abi.datatypes.Function
import java.math.BigDecimal
import java.math.BigInteger

@Suppress("FunctionName")
fun NFTIsApprovedForAll(
    chainId: Long,
    sender: String,
    receiver: String,
    token: NFTCollection.NFT.Full
): NFTCall.IsApprovedForAll {
    val contractAddress = token.contractAddress ?: error(
        """
            TokenId supplied is null.
        """.trimIndent()
    )

    val functionCall = Function(
        "isApprovedForAll",
        listOf(
            Address(160, contractAddress),
            Address(160, receiver)
        ),
        listOf(TypeReference.create(Bool::class.java))
    )

    return NFTCall.IsApprovedForAll(
        chainId = chainId,
        nonce = BigInteger.ZERO,
        sender = sender,
        receiver = receiver,
        amount = BigDecimal.ZERO,
        contractAddress = contractAddress,
        encodedFunction = FunctionEncoder.encode(functionCall),
        outputTypeRefs = functionCall.outputParameters
    )
}