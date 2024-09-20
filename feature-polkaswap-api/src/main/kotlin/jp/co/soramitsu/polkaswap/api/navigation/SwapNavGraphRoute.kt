package jp.co.soramitsu.polkaswap.api.navigation

sealed interface SwapNavGraphRoute {

    val routeName: String

    data object Loading : SwapNavGraphRoute {
        override val routeName: String = "Loading"
    }

    class SwapScreen : SwapNavGraphRoute by Companion {
        companion object : SwapNavGraphRoute {
            override val routeName: String = "SwapScreen"
        }
    }
}
