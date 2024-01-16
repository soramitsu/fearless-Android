package jp.co.soramitsu.nft.impl.domain.adapters

import jp.co.soramitsu.nft.domain.models.NFTCollection
import jp.co.soramitsu.nft.impl.domain.models.NFTCall
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Bool
import org.web3j.abi.datatypes.Function
import java.math.BigDecimal
import java.math.BigInteger

@Suppress("FunctionName")
fun NFTSetApproveForAll(
    chainId: Long,
    sender: String,
    receiver: String,
    isApproved: Boolean,
    token: NFTCollection.NFT.Full
): NFTCall {
    val contractAddress = token.contractAddress ?: error(
        """
            TokenId supplied is null.
        """.trimIndent()
    )

    val functionCall = Function(
        "setApprovalForAll",
        listOf(
            Address(160, receiver),
            Bool(isApproved)
        ),
        emptyList()
    )

    return NFTCall.SetApproveForAll(
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