package jp.co.soramitsu.nft.impl.data.model

import java.math.BigInteger
import jp.co.soramitsu.core.models.ChainId

data class NftCollection(
    val name: String, //AlchemyNftInfo.contractMetadata.openSea.collectionName
    val image: String,
    val description: String?,
    val chainId: ChainId,
    val chainName: String,
    val type: String?,
    val nfts: List<Nft>,
    val collectionSize: Int
)

data class Nft(
    val title: String,
    val description: String,
    val thumbnail: String,
    val owned: String?,
    val tokenId: String?,

    )

data class AlchemyNftResponse(
    val ownedNfts: List<AlchemyNftInfo>
)

data class AlchemyNftInfo(
    val title: String?,
    val description: String?,
    val media: List<AlchemyNftMediaInfo>?,
    val id: AlchemyNftId?,
    val balance: String?,
    val contract: AlchemyNftContractInfo?,
    val metadata: AlchemyNftMetadata?,
    val spamInfo: AlchemyNftSpamInfo?,
    val contractMetadata: AlchemyNftCollection?,
)

data class AlchemyNftSpamInfo(
    val isSpam: Boolean?,
    val classifications: List<String>?,
)

data class AlchemyNftContractInfo(
    val address: String?,
)

data class AlchemyNftTokenMetadata(
    val tokenType: String?,
)

data class AlchemyNftId(
    val tokenId: String?,
    val tokenMetadata: AlchemyNftTokenMetadata?,
)

data class AlchemyNftMetadata(
    val name: String?,
    val description: String?,
    val backgroundColor: String?,
    val poster: String?,
    val image: String?,
    val externalUrl: String?
)

data class AlchemyNftMediaInfo(
    val gateway: String?,
    val thumbnail: String?,
    val raw: String?,
    val format: String?,
    val bytes: BigInteger?,
)

data class AlchemyNftCollectionsResponse(
    val contracts: List<AlchemyNftCollection>?,
    val totalCount: Int,
)

data class AlchemyNftCollection(
    val address: String?,
    val totalBalance: Int?,
    val numDistinctTokensOwned: Int?,
    val isSpam: String?,
    val tokenId: String?,
    val name: String?,
    val title: String?,
    val symbol: String?,
    val totalSupply: String?,
    val tokenType: String?,
    val contractDeployer: String?,
    val deployedBlockNumber: BigInteger?,
    val openSea: AlchemyNftOpenseaInfo?,
    val media: List<AlchemyNftMediaInfo>?,
)

data class AlchemyNftOpenseaInfo(
    val floorPrice: Float?,
    val collectionName: String?,
    val collectionSlug: String?,
    val safelistRequestStatus: String?,
    val imageUrl: String?,
    val description: String?,
    val externalUrl: String?,
    val twitterUsername: String?,
    val bannerImageUrl: String?,
    val lastIngestedAt: String?,
)