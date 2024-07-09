package jp.co.soramitsu.liquiditypools.navigation

import jp.co.soramitsu.androidfoundation.format.StringPair
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId

sealed interface LiquidityPoolsNavGraphRoute {

    val routeName: String

    object Loading : LiquidityPoolsNavGraphRoute {
        override val routeName: String = "Loading"
    }

    class AllPoolsScreen(
        val chainId: ChainId,
        val contractAddress: String
    ) : LiquidityPoolsNavGraphRoute by Companion {
        companion object : LiquidityPoolsNavGraphRoute {
            override val routeName: String = "AllPoolsScreen"
        }
    }

    class ListPoolsScreen(
//        val token: NFT
    ) : LiquidityPoolsNavGraphRoute by Companion {
        companion object : LiquidityPoolsNavGraphRoute {
            override val routeName: String = "ListPoolsScreen"
        }
    }

    class PoolDetailsScreen(
        val ids: StringPair
    ) : LiquidityPoolsNavGraphRoute by Companion {
        companion object : LiquidityPoolsNavGraphRoute {
            override val routeName: String = "LiquidityPoolDetailsScreen"
        }
    }

    class LiquidityAddScreens(
//        val token: NFT,
        val receiver: String,
        val isReceiverKnown: Boolean
    ) : LiquidityPoolsNavGraphRoute by Companion {
        companion object : LiquidityPoolsNavGraphRoute {
            override val routeName: String = "LiquidityAddScreens"
        }
    }

    class LiquidityRemoveScreens(
//        val token: NFT,
        val receiver: String,
        val isReceiverKnown: Boolean
    ) : LiquidityPoolsNavGraphRoute by Companion {
        companion object : LiquidityPoolsNavGraphRoute {
            override val routeName: String = "LiquidityAddScreens"
        }
    }
}
