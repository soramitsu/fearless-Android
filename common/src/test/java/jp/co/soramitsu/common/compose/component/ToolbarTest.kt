package jp.co.soramitsu.common.compose.component

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composer
import androidx.compose.runtime.currentComposer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import java.time.Duration
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowSystemClock

@RunWith(RobolectricTestRunner::class)
class ToolbarTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        ShadowSystemClock.advanceBy(Duration.ofSeconds(10))
        // Reduced motion keeps loading skeletons still, so the compose rule can go idle.
        Settings.Global.putString(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, "0")
    }

    @Test
    fun mainToolbarShowsTitleAndChainAndRoutesEveryAction() {
        var navigation = 0
        var chain = 0
        var scan = 0
        var search = 0
        compose.setContent {
            FearlessAppTheme {
                MainToolbar(
                    state = MainToolbarViewState(
                        title = "Main wallet",
                        selectorViewState = ChainSelectorViewState(selectedChainName = "Kusama", selectedChainId = "ksm")
                    ),
                    onChangeChainClick = { chain++ },
                    onNavigationClick = { navigation++ },
                    menuItems = listOf(
                        MenuIconItem(R.drawable.ic_scan, { scan++ }),
                        MenuIconItem(R.drawable.ic_search, { search++ })
                    )
                )
            }
        }

        compose.onNodeWithText("Main wallet").assertIsDisplayed()
        compose.onNodeWithText("Kusama").performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.ux_switch_wallet)).performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.ux_scan)).performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.ux_search)).performClick()
        compose.waitForIdle()

        assertEquals(listOf(1, 1, 1, 1), listOf(chain, navigation, scan, search))
    }

    @Test
    fun mainToolbarWithoutChainCallbackShowsAllNetworksWithoutClickAction() {
        compose.setContent {
            FearlessAppTheme {
                MainToolbar(
                    state = MainToolbarViewState(title = "Watch only", selectorViewState = ChainSelectorViewState()),
                    onChangeChainClick = null
                )
            }
        }

        compose.onNodeWithText("Watch only").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.chain_selection_all_networks)).assertHasNoClickAction()
    }

    @Test
    fun filterToolbarShowsWalletAndFilterAndRoutesClicks() {
        var navigation = 0
        var chain = 0
        var settings = 0
        compose.setContent {
            FearlessAppTheme {
                MainToolbar(
                    state = MainToolbarViewStateWithFilters(
                        title = "Savings",
                        homeIconState = ToolbarHomeIconState.Wallet(ColorDrawable(android.graphics.Color.RED)),
                        selectorViewState = ChainSelectorViewStateWithFilters(
                            filterApplied = ChainSelectorViewStateWithFilters.Filter.Favorite,
                            allowChainSelection = true
                        )
                    ),
                    onChangeChainClick = { chain++ },
                    onNavigationClick = { navigation++ },
                    menuItems = listOf(MenuIconItem(R.drawable.ic_settings, { settings++ }))
                )
            }
        }

        compose.onNodeWithText("Savings").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.network_managment_favourite)).performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.ux_switch_wallet)).performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.profile_settings_title)).performClick()
        compose.waitForIdle()

        assertEquals(listOf(1, 1, 1), listOf(chain, navigation, settings))
    }

    @Test
    fun filterToolbarWhileLoadingShowsSkeletons() {
        val state = MainToolbarViewStateWithFilters.default
        assertNull(state.title)
        assertNull(state.selectorViewState)
        compose.setContent {
            FearlessAppTheme {
                MainToolbar(state = state, onChangeChainClick = {})
            }
        }

        compose.onNodeWithContentDescription(context.getString(R.string.ux_switch_wallet)).assertIsDisplayed()
    }

    @Test
    fun filterToolbarShowsPopularAndAllFilterTitles() {
        compose.setContent {
            FearlessAppTheme {
                Column {
                    MainToolbar(
                        state = MainToolbarViewStateWithFilters(
                            title = "Popular wallet",
                            selectorViewState = ChainSelectorViewStateWithFilters(filterApplied = ChainSelectorViewStateWithFilters.Filter.Popular)
                        ),
                        onChangeChainClick = {}
                    )
                    MainToolbar(
                        state = MainToolbarViewStateWithFilters(
                            title = "All wallet",
                            homeIconState = ToolbarHomeIconState.Wallet(ColorDrawable(android.graphics.Color.BLUE)),
                            selectorViewState = ChainSelectorViewStateWithFilters(filterApplied = ChainSelectorViewStateWithFilters.Filter.All)
                        ),
                        onChangeChainClick = {}
                    )
                }
            }
        }

        compose.onNodeWithText(context.getString(R.string.network_management_popular)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.chain_selection_all_networks)).assertIsDisplayed()
    }

    @Test
    fun shimmerToolbarKeepsNavigationAndMenuUsable() {
        var navigation = 0
        var more = 0
        compose.setContent {
            FearlessAppTheme {
                Column {
                    MainToolbarShimmer(
                        homeIconState = ToolbarHomeIconState.Navigation(R.drawable.ic_arrow_back_24dp),
                        menuItems = listOf(MenuIconItem(R.drawable.ic_dots_horizontal_24, { more++ })),
                        onNavigationClick = { navigation++ }
                    )
                    MainToolbarShimmer()
                }
            }
        }

        compose.onNodeWithContentDescription(context.getString(R.string.ux_back)).performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.ux_more)).performClick()
        compose.waitForIdle()

        assertEquals(1, navigation)
        assertEquals(1, more)
    }

    @Test
    fun simpleToolbarShowsTitleNavigationAndMenu() {
        var navigation = 0
        var close = 0
        val withNavigation = ToolbarViewState("Pool staking", R.drawable.ic_arrow_back_24dp, MenuIconItem(R.drawable.ic_cross_24, { close++ }))
        assertEquals(1, withNavigation.menuItems?.size)
        assertEquals(R.string.ux_close, withNavigation.menuItems?.single()?.contentDescriptionRes)
        compose.setContent {
            FearlessAppTheme {
                Column {
                    Toolbar(state = withNavigation, onNavigationClick = { navigation++ })
                    Toolbar(state = ToolbarViewState(title = "No navigation"))
                }
            }
        }

        compose.onNodeWithText("Pool staking").assertIsDisplayed()
        compose.onNodeWithText("No navigation").assertIsDisplayed()
        compose.onAllNodesWithContentDescription(context.getString(R.string.ux_back)).assertCountEquals(1)
        compose.onNodeWithContentDescription(context.getString(R.string.ux_back)).performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.ux_close)).performClick()
        compose.waitForIdle()

        assertEquals(1, navigation)
        assertEquals(1, close)
    }

    @Test
    fun walletHomeIconRoutesClicks() {
        var home = 0
        var plain = 0
        compose.setContent {
            FearlessAppTheme {
                Column {
                    ToolbarHomeIcon(
                        state = ToolbarHomeIconState.Wallet(ColorDrawable(android.graphics.Color.GREEN)),
                        onClick = { home++ }
                    )
                    IconButton(
                        painter = painterResource(R.drawable.ic_search),
                        contentDescription = "Tinted search",
                        tint = Color.Red,
                        onClick = { plain++ }
                    )
                    IconButton(
                        painter = painterResource(R.drawable.ic_scan),
                        contentDescription = "Own colours",
                        tint = Color.Unspecified,
                        onClick = { plain++ }
                    )
                }
            }
        }

        compose.onNodeWithContentDescription(context.getString(R.string.ux_switch_wallet)).performClick()
        compose.onNodeWithContentDescription("Tinted search").performClick()
        compose.onNodeWithContentDescription("Own colours").performClick()
        compose.waitForIdle()

        assertEquals(1, home)
        assertEquals(2, plain)
    }

    @Test
    fun previewRendersEveryToolbarVariant() {
        compose.setContent { RenderPreview("jp.co.soramitsu.common.compose.component.ToolbarKt", "MainToolbarPreview") }

        compose.onNodeWithText("Fearless wallet very long wallet name").assertIsDisplayed()
        compose.onNodeWithText("Crust shadow parachain").assertIsDisplayed()
        compose.onAllNodesWithText("Pool staking").assertCountEquals(2)
    }

    @Composable
    private fun RenderPreview(fileClass: String, name: String) {
        val method = Class.forName(fileClass).getDeclaredMethod(name, Composer::class.java, Int::class.javaPrimitiveType)
        method.isAccessible = true
        method.invoke(null, currentComposer, 0)
    }
}
