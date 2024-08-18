package jp.co.soramitsu.liquiditypools.navigation

import java.math.BigDecimal
import jp.co.soramitsu.androidfoundation.format.StringPair

sealed interface LiquidityPoolsNavGraphRoute {

    val routeName: String

    data object Loading : LiquidityPoolsNavGraphRoute {
        override val routeName: String = "Loading"
    }

    class AllPoolsScreen: LiquidityPoolsNavGraphRoute by Companion {
        companion object : LiquidityPoolsNavGraphRoute {
            override val routeName: String = "AllPoolsScreen"
        }
    }

    class ListPoolsScreen(
        val isUserPools: Boolean
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

    class LiquidityAddScreen(
        val ids: StringPair
    ) : LiquidityPoolsNavGraphRoute by Companion {
        companion object : LiquidityPoolsNavGraphRoute {
            override val routeName: String = "LiquidityAddScreens"
        }
    }

    class LiquidityAddConfirmScreen(
        val ids: StringPair,
        val amountBase: BigDecimal,
        val amountTarget: BigDecimal,
        val apy: String
    ) : LiquidityPoolsNavGraphRoute by Companion {
        companion object : LiquidityPoolsNavGraphRoute {
            override val routeName: String = "LiquidityAddConfirmScreen"
        }
    }

    class LiquidityRemoveScreen(
        val ids: StringPair
    ) : LiquidityPoolsNavGraphRoute by Companion {
        companion object : LiquidityPoolsNavGraphRoute {
            override val routeName: String = "LiquidityRemoveScreen"
        }
    }

    class LiquidityRemoveConfirmScreen(
        val ids: StringPair,
        val amountBase: BigDecimal,
        val amountTarget: BigDecimal,
        val firstAmountMin: BigDecimal,
        val secondAmountMin: BigDecimal,
        val desired: BigDecimal,
    ) : LiquidityPoolsNavGraphRoute by Companion {
        companion object : LiquidityPoolsNavGraphRoute {
            override val routeName: String = "LiquidityRemoveConfirmScreen"
        }
    }
}
