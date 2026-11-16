package jp.co.soramitsu.onboarding.impl.welcome

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.feature_onboarding_impl.R
import jp.co.soramitsu.onboarding.impl.testutil.allowNextTap
import jp.co.soramitsu.onboarding.impl.testutil.drawOnce
import jp.co.soramitsu.onboarding.impl.testutil.text
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class SelectEcosystemScreenTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val viewModel = mockk<WelcomeViewModel>(relaxed = true)

    @Before
    fun moveClockPastTheDoubleTapGuard() = allowNextTap()

    private fun show(state: WelcomeState) {
        every { viewModel.state } returns MutableStateFlow(state)
        compose.setContent {
            FearlessAppTheme {
                NavHost(rememberNavController(), startDestination = WelcomeEvent.Onboarding.SelectEcosystemScreen.route) {
                    SelectEcosystemScreen(listener = viewModel)
                }
            }
        }
        compose.waitForIdle()
    }

    private fun tap(label: String) {
        compose.onNodeWithText(label).performScrollTo().performClick()
        compose.waitForIdle()
        allowNextTap()
    }

    @Test
    fun eachNetworkCardAndLinkReachesTheViewModel() {
        show(WelcomeState())
        compose.onNodeWithText(text(R.string.ux_network_selection_title)).assertIsDisplayed()
        compose.activity.window.decorView.drawOnce()

        tap(text(R.string.ux_networks_ethereum_polkadot))
        tap(text(R.string.ux_network_ton))
        tap(text(R.string.onboarding_terms_and_conditions_2))
        tap(text(R.string.onboarding_privacy_policy))
        compose.onNodeWithContentDescription(text(R.string.ux_back)).performScrollTo().performClick()
        compose.waitForIdle()

        verifyOrder {
            viewModel.substrateEvmClick()
            viewModel.tonClick()
            viewModel.termsClicked()
            viewModel.privacyClicked()
            viewModel.backClicked()
        }
    }

    @Test
    fun whileLoadingNoNetworkCanBeChosen() {
        compose.mainClock.autoAdvance = false
        show(WelcomeState(isLoading = true))

        compose.onAllNodesWithText(text(R.string.ux_networks_ethereum_polkadot)).assertCountEquals(0)
        compose.onAllNodesWithText(text(R.string.ux_network_ton)).assertCountEquals(0)
        verify(exactly = 0) { viewModel.substrateEvmClick() }
    }

    @Test
    fun previewListsBothNetworks() {
        compose.setContent { SelectEcosystemScreenPreview() }
        compose.waitForIdle()

        compose.onNodeWithText(text(R.string.ux_networks_ethereum_polkadot)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.ux_asset_ton)).performScrollTo().assertIsDisplayed()
        tap(text(R.string.ux_network_ton))
        tap(text(R.string.onboarding_privacy_policy))
    }
}
