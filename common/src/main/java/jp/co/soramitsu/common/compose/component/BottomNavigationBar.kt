package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.BottomNavigation
import androidx.compose.material.BottomNavigationItem
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.bottomnavbar.NavigationItem
import jp.co.soramitsu.common.compose.theme.FearlessTheme

@Composable
fun BottomNavigationBar(navController: NavController?) {
    val items = listOf(
        NavigationItem.Wallet,
        NavigationItem.Crowdloans,
        NavigationItem.Staking,
        NavigationItem.Governance,
        NavigationItem.Settings
    )
    Column {
        Divider(
            color = Color.White.copy(0.08f),
            modifier = Modifier.fillMaxWidth()
        )
        BottomNavigation(
            backgroundColor = colorResource(id = R.color.black) // .copy(alpha = 0.5f),
        ) {
            val navBackStackEntry = navController?.currentBackStackEntryAsState()
//            val navBackStackEntry by navController?.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.value?.destination?.route
            items.forEachIndexed { index, item ->
                BottomNavigationItem(
                    icon = { Icon(painterResource(id = item.icon), contentDescription = item.title) },
//                label = { Text(text = item.title) },
                    selectedContentColor = Color.White,
                    unselectedContentColor = Color.White.copy(0.5f),
                    alwaysShowLabel = false,
                    selected = currentRoute == item.route,
                    onClick = {
                        navController?.navigate(item.route) {
                            // Pop up to the start destination of the graph to
                            // avoid building up a large stack of destinations
                            // on the back stack as users select items
                            navController.graph.startDestinationRoute?.let { route ->
                                popUpTo(route) {
                                    saveState = true
                                }
                            }
                            // Avoid multiple copies of the same destination when
                            // reselecting the same item
                            launchSingleTop = true
                            // Restore state when reselecting a previously selected item
                            restoreState = true
                        }
                    }
                )
            }
        }
    }
}

@Preview
@Composable
private fun BottomNavigationBarPreview() {
    FearlessTheme {
        BottomNavigationBar(null)
    }
}
