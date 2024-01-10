package jp.co.soramitsu.nft.impl.domain.models

import jp.co.soramitsu.nft.domain.models.NFTCollection
import jp.co.soramitsu.shared_utils.extensions.requireHexPrefix
import java.math.BigDecimal
import java.math.BigInteger

sealed interface NFTTransferParams {

    companion object {
        fun create(
            sender: String,
            receiver: String,
            token: NFTCollection.NFT.Full,
            erc1155TransferAmount: BigDecimal? = null,
        ): NFTTransferParams {
            val tokenId = token.tokenId?.requireHexPrefix()?.drop(2) ?: error(
                """
                    TokenId supplied is null.
                """.trimIndent()
            )

            return when(token.tokenType) {

                "ERC721" -> ERC721(
                    sender = sender,
                    receiver = receiver,
                    tokenId = BigInteger(tokenId, 16),
                    data = ByteArray(0)
                )

                "ERC1155" -> ERC1155(
                    sender = sender,
                    receiver = receiver,
                    tokenId = BigInteger(tokenId, 16),
                    amount = erc1155TransferAmount!!,
                    data = ByteArray(0)
                )

                else -> throw IllegalArgumentException(
                    """
                        Token provided is not supported.
                    """.trimIndent()
                )

            }
        }
    }

    val sender: String

    val receiver: String

    val tokenId: BigInteger

    val data: ByteArray

    class ERC721(
        override val sender: String,
        override val receiver: String,
        override val tokenId: BigInteger,
        override val data: ByteArray
    ): NFTTransferParams

    class ERC1155(
        override val sender: String,
        override val receiver: String,
        override val tokenId: BigInteger,
        override val data: ByteArray,
        val amount: BigDecimal
    ): NFTTransferParams

}