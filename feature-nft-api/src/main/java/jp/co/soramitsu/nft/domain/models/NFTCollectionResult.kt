package jp.co.soramitsu.nft.domain.models

import jp.co.soramitsu.core.models.ChainId

sealed interface NFTCollectionResult {

    val chainId: ChainId

    val chainName: String

    class Data(
        override val chainId: ChainId,
        override val chainName: String,
        val collectionName: String,
        val contractAddress: String,
        val description: String,
        val imageUrl: String,
        val type: String,
        val balance: Int,
        val collectionSize: Int
    ) : NFTCollectionResult {

        class WithTokens(
            val data: Data,
            val tokens: List<NFT>
        ) : NFTCollectionResult by data
    }

    class Empty(
        override val chainId: ChainId,
        override val chainName: String
    ) : NFTCollectionResult

    class Error(
        override val chainId: ChainId,
        override val chainName: String,
        val throwable: Throwable
    ) : NFTCollectionResult
}
