package jp.co.soramitsu.nft.domain.models

import jp.co.soramitsu.core.models.ChainId

sealed interface NFTCollection {

    interface Reloading : NFTCollection {
        companion object : Reloading
    }

    interface Loaded : NFTCollection {

        val chainId: ChainId

        val chainName: String

        interface Result : Loaded {

            interface Collection : Result {

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
            ) : Result
        }

        class WithFailure(
            override val chainId: ChainId,
            override val chainName: String,
            val throwable: Throwable
        ) : Loaded
    }
}
