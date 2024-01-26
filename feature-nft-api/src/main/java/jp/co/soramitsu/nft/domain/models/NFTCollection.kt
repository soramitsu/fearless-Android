package jp.co.soramitsu.nft.domain.models

import jp.co.soramitsu.core.models.ChainId

data class NFTCollection<T: NFTCollection.NFT>(
    val chainId: ChainId,
    val chainName: String,
    val collectionName: String,
    val contractAddress: String?,
    val description: String?,
    val imageUrl: String,
    val type: String?,
    val tokens: List<T>,
    val collectionSize: Int
) {

    sealed interface NFT {

        data class Light(
            val tokenId: String?,
            val balance: String?
        ): NFT

        data class Full(
            val title: String?,
            val thumbnail: String,
            val description: String?,
            val collectionName: String?,
            val contractAddress: String?,
            val isUserOwnedToken: Boolean,
            val tokenId: String?,
            val chainName: String,
            val chainId: ChainId,
            val tokenType: String?,
            val date: String?,
            val price: String
        ): NFT

    }

}