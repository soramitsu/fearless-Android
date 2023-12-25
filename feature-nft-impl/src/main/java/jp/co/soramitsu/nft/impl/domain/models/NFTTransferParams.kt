package jp.co.soramitsu.nft.impl.domain.models

import java.math.BigDecimal
import java.math.BigInteger

internal sealed interface NFTTransferParams {

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