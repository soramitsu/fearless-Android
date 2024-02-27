package jp.co.soramitsu.nft.domain.models

import jp.co.soramitsu.core.models.ChainId

sealed interface NFTCollectionResult {

    val chainId: ChainId

    val chainName: String

    interface Collection : NFTCollectionResult {

        val contractAddress: String

        val collectionName: String

        val description: String

        val imageUrl: String

        val type: String

        val balance: Int

        val collectionSize: Int

        interface WithTokens : Collection {
            val tokens: Sequence<NFT>
        }
    }

    class Empty(
        override val chainId: ChainId,
        override val chainName: String
    ) : NFTCollectionResult {
        override fun toString(): String {
            return "NFTCollectionResult.Empty"
        }
    }

    class Error(
        override val chainId: ChainId,
        override val chainName: String,
        val throwable: Throwable
    ) : NFTCollectionResult {
        override fun toString(): String {
            return "NFTCollectionResult.Error"
        }
    }
}
