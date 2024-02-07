package jp.co.soramitsu.nft.domain.models

import jp.co.soramitsu.core.models.ChainId

sealed interface NFTCollection<T: NFT> {

    class Data<T: NFT>(
        val chainId: ChainId,
        val chainName: String,
        val collectionName: String,
        val contractAddress: String,
        val description: String,
        val imageUrl: String,
        val type: String,
        val tokens: List<T>,
        val balance: Int,
        val collectionSize: Int
    ): NFTCollection<T>

    class Empty<T: NFT>(
        val chainId: ChainId,
        val chainName: String
    ): NFTCollection<T>

    class Error<T: NFT>(
        val chainId: ChainId,
        val chainName: String,
        val throwable: Throwable
    ): NFTCollection<T>

}
