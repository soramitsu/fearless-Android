package jp.co.soramitsu.nft.impl.navigation

import jp.co.soramitsu.nft.domain.models.NFT
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId

sealed interface Destination {

    sealed interface NestedNavGraphRoute: Destination {

        val routeName: String

        object Loading: NestedNavGraphRoute {
            override val routeName: String = "Loading"
        }

        class CollectionNFTsScreen(
            val chainId: ChainId,
            val contractAddress: String
        ): NestedNavGraphRoute by Companion {
            companion object: NestedNavGraphRoute {
                override val routeName: String = "CollectionNFTsScreen"
            }
        }

        class ChooseNFTRecipientScreen(
            val token: NFT.Full
        ): NestedNavGraphRoute by Companion {
            companion object: NestedNavGraphRoute {
                override val routeName: String = "ChooseNFTRecipientScreen"
            }
        }

        class ConfirmNFTSendScreen(
            val token: NFT.Full,
            val receiver: String,
            val isReceiverKnown: Boolean
        ): NestedNavGraphRoute by Companion {
            companion object: NestedNavGraphRoute {
                override val routeName: String = "ConfirmNFTSendScreen"
            }
        }

    }

    sealed interface Action: Destination {

        object BackPressed: Action

        object QRCodeScanner: Action

        @JvmInline
        value class ShowError(
            val errorText: String
        ): Action

    }

}