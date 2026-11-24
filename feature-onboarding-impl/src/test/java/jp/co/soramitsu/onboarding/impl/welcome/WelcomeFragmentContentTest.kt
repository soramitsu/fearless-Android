package jp.co.soramitsu.onboarding.impl.welcome

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.account.api.domain.model.AccountType
import jp.co.soramitsu.common.launch.LaunchLanding
import jp.co.soramitsu.feature_onboarding_impl.R
import jp.co.soramitsu.onboarding.impl.testutil.text
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * WelcomeFragment is a Hilt entry point and cannot be attached without a Hilt application, so its screen content is
 * rendered directly with a mocked view model installed in place of the `by viewModels()` delegate.
 */
@OptIn(ExperimentalMaterialApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class WelcomeFragmentContentTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val screenEvents = MutableSharedFlow<WelcomeEvent>(extraBufferCapacity = 16)
    private val viewModel = mockk<WelcomeViewModel>(relaxed = true)

    private fun showFragment(startDestination: String = WelcomeEvent.Onboarding.SplashScreen.route): WelcomeFragment {
        every { viewModel.events } returns screenEvents
        every { viewModel.startDestination } returns startDestination
        every { viewModel.state } returns MutableStateFlow(WelcomeState())
        every { viewModel.onboardingBackground } returns MutableStateFlow(null)
        every { viewModel.onboardingFlowState } returns MutableStateFlow(null)
        val fragment = WelcomeFragment()
        WelcomeFragment::class.java.getDeclaredField("viewModel\$delegate").apply { isAccessible = true }
            .set(fragment, lazyOf(viewModel))
        compose.setContent {
            val host = remember { fragment }
            Box {
                host.Background()
                host.Content(
                    PaddingValues(0.dp),
                    rememberScrollState(),
                    rememberModalBottomSheetState(ModalBottomSheetValue.Hidden)
                )
            }
        }
        compose.waitForIdle()
        return fragment
    }

    private fun send(event: WelcomeEvent) {
        compose.runOnIdle { assertTrue(screenEvents.tryEmit(event)) }
        compose.waitForIdle()
    }

    @Test
    fun screenEventsNavigateBetweenTheOnboardingPages() {
        val fragment = showFragment()
        assertSame(viewModel, fragment.viewModel)
        compose.onAllNodesWithText(text(R.string.username_setup_title_2_0)).assertCountEquals(0)

        send(WelcomeEvent.Onboarding.WelcomeScreen(AccountType.Ton))
        compose.onNodeWithText(text(R.string.username_setup_title_2_0)).assertExists()
        assertTrue(LaunchLanding.ready.value)
        // Ton was carried in the route, so the Google entry stays hidden for it.
        compose.onAllNodesWithText(text(R.string.onboarding_connect_with_google)).assertCountEquals(0)

        send(WelcomeEvent.Onboarding.SelectEcosystemScreen)
        compose.onNodeWithText(text(R.string.ux_network_selection_title)).assertIsDisplayed()

        send(WelcomeEvent.Back)
        compose.onNodeWithText(text(R.string.username_setup_title_2_0)).assertExists()

        compose.mainClock.autoAdvance = false
        send(WelcomeEvent.Onboarding.PagerScreen)
        compose.mainClock.advanceTimeBy(1_000)
        compose.onAllNodesWithText(text(R.string.ux_network_selection_title)).assertCountEquals(0)
        verify(exactly = 0) { viewModel.exitOnboarding() }
    }

    @Test
    fun backOnTheFirstPageLeavesOnboarding() {
        showFragment(startDestination = "WelcomeScreen")

        send(WelcomeEvent.Back)

        verify(exactly = 1) { viewModel.exitOnboarding() }
    }

    @Test
    fun googleEventStartsTheSignInWithTheFragmentsLauncher() {
        showFragment()

        send(WelcomeEvent.AuthorizeGoogle)

        verify(exactly = 1) { viewModel.authorizeGoogle(any()) }
        verify(exactly = 0) { viewModel.exitOnboarding() }
    }

    @Test
    fun anUnattachedFragmentOffersNoGoogleSignIn() {
        val fragment = showFragment(startDestination = "WelcomeScreen?accountType=SubstrateOrEvm")

        assertNull(fragment.context)
        compose.onAllNodesWithText(text(R.string.onboarding_connect_with_google)).assertCountEquals(0)
        assertEquals("key_payload", WelcomeFragment.KEY_PAYLOAD)
        val bundle = WelcomeFragment.getBundle(displayBack = true, route = "WelcomeScreen")
        @Suppress("DEPRECATION")
        val payload = bundle.getParcelable<WelcomeFragmentPayload>(WelcomeFragment.KEY_PAYLOAD)!!
        assertTrue(payload.displayBack)
        assertNull(payload.createChainAccount)
        assertEquals("WelcomeScreen", payload.route)
    }
}
