package jp.co.soramitsu.onboarding.impl.welcome

import android.media.AudioManager
import androidx.activity.ComponentActivity
import androidx.compose.material.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import jp.co.soramitsu.account.api.domain.model.AccountType
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.compose.theme.fw.FwEffects
import jp.co.soramitsu.common.compose.theme.fw.FwTier
import jp.co.soramitsu.common.compose.theme.fw.LocalFwEffects
import jp.co.soramitsu.feature_onboarding_impl.R
import jp.co.soramitsu.onboarding.impl.testutil.PrivateComposable
import jp.co.soramitsu.onboarding.impl.testutil.allowNextTap
import jp.co.soramitsu.onboarding.impl.testutil.drawOnce
import jp.co.soramitsu.onboarding.impl.testutil.text
import jp.co.soramitsu.onboarding.impl.testutil.viewsOfType
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
class WelcomeScreenTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val calls = mutableListOf<String>()
    private lateinit var navController: NavHostController

    private val callbacks = object : WelcomeScreenInterface {
        override fun backClicked() { calls += "back" }
        override fun importAccountClicked(accountType: AccountType?) { calls += "import:$accountType" }
        override fun createAccountClicked(accountType: AccountType?) { calls += "create:$accountType" }
        override fun savedEndpointsClicked() { calls += "endpoints" }
        override fun googleSigninClicked() { calls += "google" }
        override fun getPreInstalledWalletClicked() { calls += "preinstalled" }
        override fun privacyClicked() { calls += "privacy" }
        override fun termsClicked() { calls += "terms" }
    }

    @Before
    fun moveClockPastTheDoubleTapGuard() = allowNextTap()

    private fun show(
        state: WelcomeState,
        isGoogleAvailable: Boolean = true,
        effects: FwEffects = FwEffects(FwTier.Full, highContrast = false, reduceMotion = false)
    ) {
        val flow = MutableStateFlow(state)
        compose.setContent {
            FearlessAppTheme {
                CompositionLocalProvider(LocalFwEffects provides effects) {
                    navController = rememberNavController()
                    NavHost(navController, startDestination = WelcomeEvent.Onboarding.WelcomeScreen.route) {
                        WelcomeScreen(flow, isGoogleAvailable, callbacks)
                        composable(ELSEWHERE) { Text(ELSEWHERE) }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun open(accountType: AccountType) {
        compose.runOnIdle { navController.navigate("WelcomeScreen?accountType=${accountType.name}") }
        compose.waitForIdle()
    }

    private fun tap(label: String) {
        compose.onNodeWithText(label).performScrollTo().performClick()
        compose.waitForIdle()
        allowNextTap()
    }

    private fun absent(label: String) = compose.onAllNodesWithText(label).assertCountEquals(0)

    @Test
    fun withoutAnAccountTypeEveryEntryReportsItsOwnAction() {
        show(WelcomeState())

        tap(text(R.string.username_setup_title_2_0))
        tap(text(R.string.onboarding_restore_wallet))
        tap(text(R.string.saved_endpoints_title))
        tap(text(R.string.onboarding_terms_and_conditions_2))
        tap(text(R.string.onboarding_privacy_policy))

        assertEquals(listOf("create:null", "import:null", "endpoints", "terms", "privacy"), calls)
        absent(text(R.string.onboarding_connect_with_google))
        absent(text(R.string.onboarding_preinstalled_wallet_button_text))
        compose.onAllNodesWithContentDescription(text(R.string.ux_back)).assertCountEquals(0)
        compose.activity.window.decorView.drawOnce()
    }

    @Test
    fun substrateEntryOffersGoogleThePreinstalledWalletAndBack() {
        show(WelcomeState(isBackVisible = true, preinstalledFeatureEnabled = true))
        open(AccountType.SubstrateOrEvm)

        tap(text(R.string.username_setup_title_2_0))
        tap(text(R.string.onboarding_restore_wallet))
        tap(text(R.string.onboarding_connect_with_google))
        tap(text(R.string.onboarding_preinstalled_wallet_button_text))
        compose.onNodeWithContentDescription(text(R.string.ux_back)).performScrollTo().performClick()
        compose.waitForIdle()

        assertEquals(
            listOf("create:SubstrateOrEvm", "import:SubstrateOrEvm", "google", "preinstalled", "back"),
            calls
        )
    }

    @Test
    fun tonEntryNeverOffersGoogleOrThePreinstalledWallet() {
        show(WelcomeState(preinstalledFeatureEnabled = true))
        open(AccountType.Ton)

        tap(text(R.string.username_setup_title_2_0))

        assertEquals(listOf("create:Ton"), calls)
        absent(text(R.string.onboarding_connect_with_google))
        absent(text(R.string.onboarding_preinstalled_wallet_button_text))
    }

    @Test
    fun googleIsHiddenWhenTheDeviceCannotSignIn() {
        show(WelcomeState(), isGoogleAvailable = false)
        open(AccountType.SubstrateOrEvm)

        absent(text(R.string.onboarding_connect_with_google))
        compose.onNodeWithText(text(R.string.username_setup_title_2_0)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun loadingHidesTheActionsFromInput() {
        compose.mainClock.autoAdvance = false
        show(WelcomeState(isLoading = true))

        absent(text(R.string.username_setup_title_2_0))
        absent(text(R.string.onboarding_restore_wallet))
    }

    @Test
    fun animatedCrowIsAButtonThatDoesNotNavigateAndIsReleasedWithThePage() {
        show(WelcomeState())
        val root = compose.activity.window.decorView
        assertEquals(1, root.viewsOfType<CrowVideoView>().size)

        compose.onNodeWithTag("welcome_crow").assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.fw_welcome_crow_call)).performClick()
        compose.waitForIdle()
        assertTrue(calls.isEmpty())

        compose.runOnIdle { navController.navigate(ELSEWHERE) }
        compose.waitForIdle()
        compose.onNodeWithText(ELSEWHERE).assertIsDisplayed()
        assertTrue(root.viewsOfType<CrowVideoView>().isEmpty())
    }

    @Test
    fun reducedMotionShowsOnlyThePosterAndASilentPhoneStaysSilent() {
        val audio = compose.activity.applicationContext.getSystemService(AudioManager::class.java)
        audio.ringerMode = AudioManager.RINGER_MODE_SILENT
        show(WelcomeState(), effects = FwEffects(FwTier.Reduced, highContrast = false, reduceMotion = true))

        assertTrue(compose.activity.window.decorView.viewsOfType<CrowVideoView>().isEmpty())
        compose.onNodeWithContentDescription(text(R.string.fw_welcome_crow_call)).performClick()
        compose.waitForIdle()
        assertTrue(calls.isEmpty())
    }

    @Test
    fun previewRendersTheWelcomeActions() {
        compose.setContent {
            PrivateComposable("jp.co.soramitsu.onboarding.impl.welcome.WelcomeScreenKt", "WelcomeScreenPreview")
        }
        compose.waitForIdle()

        // Loading is handled by the navigation wrapper; the preview renders the content directly.
        compose.onNodeWithText(text(R.string.username_setup_title_2_0)).assertExists()
        compose.onNodeWithText(text(R.string.saved_endpoints_title)).assertExists()
    }

    private companion object {
        const val ELSEWHERE = "elsewhere"
    }
}
