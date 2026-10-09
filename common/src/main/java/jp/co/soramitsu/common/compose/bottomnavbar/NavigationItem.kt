package jp.co.soramitsu.common.compose.bottomnavbar

import jp.co.soramitsu.common.R

sealed class NavigationItem(val route: String, val icon: Int, val title: String) {
    object Portfolio : NavigationItem("portfolio", R.drawable.ic_nav_wallet, "Wallet")
    object DeFi : NavigationItem("defi", R.drawable.ic_nav_staking, "Earn")
    object Polkaswap : NavigationItem("polkaswap", R.drawable.ic_polkaswap_fab, "Polkaswap")
    object CrossChain : NavigationItem("cross-chain", R.drawable.ic_common_cross_chain, "Transfer")
    object Settings : NavigationItem("settings", R.drawable.ic_nav_settings, "Settings")
}
