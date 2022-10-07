package jp.co.soramitsu.common.compose.bottomnavbar

import jp.co.soramitsu.common.R

sealed class NavigationItem(val route: String, val icon: Int, val title: String) {
    object Wallet : NavigationItem("wallet", R.drawable.ic_nav_wallet, "Wallet")
    object Crowdloans : NavigationItem("crowdloans", R.drawable.ic_nav_crowdloans, "Crowdloans")
    object Staking : NavigationItem("staking", R.drawable.ic_nav_staking, "Staking")
    object Governance : NavigationItem("governance", R.drawable.ic_nav_governance, "Governance")
    object Settings : NavigationItem("settings", R.drawable.ic_nav_settings, "Settings")
}
