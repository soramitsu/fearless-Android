package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import jp.co.soramitsu.common.compose.bottomnavbar.NavigationItem
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import jp.co.soramitsu.common.R
import org.junit.Before
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BottomNavigationBarTest {

    @get:Rule
    val compose = createComposeRule()

    private val items = listOf(
        NavigationItem.Portfolio,
        NavigationItem.DeFi,
        NavigationItem.Polkaswap,
        NavigationItem.CrossChain,
        NavigationItem.Settings
    )

    @Before
    fun useVectorIconFixtures() {
        items.forEach { item ->
            mockkObject(item)
            every { item.icon } returns R.drawable.ic_vector
        }
    }

    @After
    fun restoreIcons() {
        items.forEach { unmockkObject(it) }
    }

    @Test
    fun withoutANavControllerEveryTabIsListedAndNoneSelected() {
        compose.setContent {
            FearlessAppTheme { BottomNavigationBar(navController = null) }
        }

        items.forEach { compose.onNodeWithText(it.title).assertIsDisplayed().assertIsNotSelected() }
        // A tap without a controller is ignored.
        compose.onNodeWithText(NavigationItem.Settings.title).performClick()
        compose.onNodeWithText(NavigationItem.Settings.title).assertIsNotSelected()
    }

    @Test
    fun tappingATabNavigatesToItsRouteAndSelectsIt() {
        lateinit var controller: NavHostController
        compose.setContent {
            FearlessAppTheme {
                controller = rememberNavController()
                Column {
                    Box(Modifier.weight(1f)) {
                        NavHost(navController = controller, startDestination = NavigationItem.Portfolio.route) {
                            items.forEach { item -> composable(item.route) { Text("${item.title} screen") } }
                        }
                    }
                    BottomNavigationBar(navController = controller)
                }
            }
        }

        compose.onNodeWithText("Wallet screen").assertIsDisplayed()
        compose.onNodeWithText(NavigationItem.Portfolio.title).assertIsSelected()

        compose.onNodeWithText(NavigationItem.DeFi.title).performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Earn screen").assertIsDisplayed()
        compose.onNodeWithText(NavigationItem.DeFi.title).assertIsSelected()
        compose.onNodeWithText(NavigationItem.Portfolio.title).assertIsNotSelected()
        assertEquals(NavigationItem.DeFi.route, controller.currentDestination?.route)

        compose.onNodeWithText(NavigationItem.Settings.title).performClick()
        compose.waitForIdle()
        assertEquals(NavigationItem.Settings.route, controller.currentDestination?.route)
    }
}
