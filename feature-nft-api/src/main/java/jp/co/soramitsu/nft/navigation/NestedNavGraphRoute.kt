package jp.co.soramitsu.nft.navigation

import jp.co.soramitsu.nft.domain.models.NFT
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId

sealed interface NestedNavGraphRoute {

    val routeName: String

    object Loading : NestedNavGraphRoute {
        override val routeName: String = "Loading"
    }

    class CollectionNFTsScreen(
        val chainId: ChainId,
        val contractAddress: String
    ) : NestedNavGraphRoute by Companion {
        companion object : NestedNavGraphRoute {
            override val routeName: String = "CollectionNFTsScreen"
        }
    }

    class DetailsNFTScreen(
        val token: NFT.Full
    ) : NestedNavGraphRoute by Companion {
        companion object : NestedNavGraphRoute {
            override val routeName: String = "DetailsNFTScreen"
        }
    }

    class ChooseNFTRecipientScreen(
        val token: NFT.Full
    ) : NestedNavGraphRoute by Companion {
        companion object : NestedNavGraphRoute {
            override val routeName: String = "ChooseNFTRecipientScreen"
        }
    }

    class ConfirmNFTSendScreen(
        val token: NFT.Full,
        val receiver: String,
        val isReceiverKnown: Boolean
    ) : NestedNavGraphRoute by Companion {
        companion object : NestedNavGraphRoute {
            override val routeName: String = "ConfirmNFTSendScreen"
        }
    }
}
