package jp.co.soramitsu.liquiditypools.navigation

import java.math.BigDecimal
import jp.co.soramitsu.androidfoundation.format.StringPair
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId

sealed interface LiquidityPoolsNavGraphRoute {

    val routeName: String

    object Loading : LiquidityPoolsNavGraphRoute {
        override val routeName: String = "Loading"
    }

    class AllPoolsScreen(
        val chainId: ChainId
    ): LiquidityPoolsNavGraphRoute by Companion {
        companion object : LiquidityPoolsNavGraphRoute {
            override val routeName: String = "AllPoolsScreen"
        }
    }

    class ListPoolsScreen(
        val chainId: ChainId,
        val isUserPools: Boolean
    ) : LiquidityPoolsNavGraphRoute by Companion {
        companion object : LiquidityPoolsNavGraphRoute {
            override val routeName: String = "ListPoolsScreen"
        }
    }

    class PoolDetailsScreen(
        val chainId: ChainId,
        val ids: StringPair
    ) : LiquidityPoolsNavGraphRoute by Companion {
        companion object : LiquidityPoolsNavGraphRoute {
            override val routeName: String = "LiquidityPoolDetailsScreen"
        }
    }

    class LiquidityAddScreen(
        val chainId: ChainId,
        val ids: StringPair
    ) : LiquidityPoolsNavGraphRoute by Companion {
        companion object : LiquidityPoolsNavGraphRoute {
            override val routeName: String = "LiquidityAddScreens"
        }
    }

    class LiquidityAddConfirmScreen(
        val chainId: ChainId,
        val ids: StringPair,
        val amountFrom: BigDecimal,
        val amountTo: BigDecimal,
        val apy: String
    ) : LiquidityPoolsNavGraphRoute by Companion {
        companion object : LiquidityPoolsNavGraphRoute {
            override val routeName: String = "LiquidityAddConfirmScreen"
        }
    }

    class LiquidityRemoveScreen(
        val chainId: ChainId,
        val ids: StringPair
    ) : LiquidityPoolsNavGraphRoute by Companion {
        companion object : LiquidityPoolsNavGraphRoute {
            override val routeName: String = "LiquidityRemoveScreen"
        }
    }

    class LiquidityRemoveConfirmScreen(
        val chainId: ChainId,
        val ids: StringPair,
        val amountFrom: BigDecimal,
        val amountTo: BigDecimal
    ) : LiquidityPoolsNavGraphRoute by Companion {
        companion object : LiquidityPoolsNavGraphRoute {
            override val routeName: String = "LiquidityRemoveConfirmScreen"
        }
    }
}
