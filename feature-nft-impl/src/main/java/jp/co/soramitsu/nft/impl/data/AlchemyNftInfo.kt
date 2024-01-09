package jp.co.soramitsu.nft.impl.data

import jp.co.soramitsu.core.models.ChainId

data class NftCollection(
    val contractAddress: String,
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

