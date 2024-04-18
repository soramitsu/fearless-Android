package jp.co.soramitsu.nft.domain.models

import jp.co.soramitsu.core.models.ChainId
import java.math.BigInteger

@Suppress("ComplexInterface")
interface NFT {

    val title: String

    val thumbnail: String

    val description: String

    val collectionName: String

    val contractAddress: String

    val creatorAddress: String?

    val isUserOwnedToken: Boolean

    val tokenId: BigInteger

    val chainName: String

    val chainId: ChainId

    val tokenType: String

    val date: String?

    val price: String
}
