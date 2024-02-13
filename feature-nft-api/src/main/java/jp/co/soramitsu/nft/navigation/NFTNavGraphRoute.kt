package jp.co.soramitsu.nft.navigation

import jp.co.soramitsu.nft.domain.models.NFT
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId

sealed interface NFTNavGraphRoute {

    val routeName: String

    object Loading : NFTNavGraphRoute {
        override val routeName: String = "Loading"
    }

    class CollectionNFTsScreen(
        val chainId: ChainId,
        val contractAddress: String
    ) : NFTNavGraphRoute by Companion {
        companion object : NFTNavGraphRoute {
            override val routeName: String = "CollectionNFTsScreen"
        }
    }

    class DetailsNFTScreen(
        val token: NFT
    ) : NFTNavGraphRoute by Companion {
        companion object : NFTNavGraphRoute {
            override val routeName: String = "DetailsNFTScreen"
        }
    }

    class ChooseNFTRecipientScreen(
        val token: NFT
    ) : NFTNavGraphRoute by Companion {
        companion object : NFTNavGraphRoute {
            override val routeName: String = "ChooseNFTRecipientScreen"
        }
    }

    class ConfirmNFTSendScreen(
        val token: NFT,
        val receiver: String,
        val isReceiverKnown: Boolean
    ) : NFTNavGraphRoute by Companion {
        companion object : NFTNavGraphRoute {
            override val routeName: String = "ConfirmNFTSendScreen"
        }
    }
}
