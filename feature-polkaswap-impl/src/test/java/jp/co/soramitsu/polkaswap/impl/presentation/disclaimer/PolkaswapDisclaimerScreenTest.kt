package jp.co.soramitsu.polkaswap.impl.presentation.disclaimer

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.click
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.compose.theme.colorAccent
import jp.co.soramitsu.feature_polkaswap_impl.R
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
class PolkaswapDisclaimerScreenTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val calls = mutableListOf<String>()
    private val callbacks = object : DisclaimerScreenInterface {
        override fun onLinkClick(url: String) { calls += "link:$url" }
        override fun onContinueClick() { calls += "continue" }
        override fun onBackClick() { calls += "back" }
        override fun onHasReadChecked() { calls += "toggle" }
    }

    private fun highlights(text: String, vararg links: String) = TextWithHighlights(
        text, "%%", links.map { TextWithHighlights.HighlightedTextParameters(colorAccent, it) }
    )

    private val unread = PolkaswapDisclaimerViewState(
        polkaswapMaintained = highlights("%%Polkaswap FAQ%%", "https://faq.test"),
        userResponsibilityTitle = "You are responsible for",
        userResponsibilities = listOf("Keeping your keys safe", "Checking every route"),
        disclaimerReminder = highlights("Read the %%terms%% and %%privacy%% notes", "https://terms.test", "https://privacy.test"),
        hasReadChecked = false,
        switchEnabled = true
    )

    @Before fun advanceClock() { ShadowSystemClock.advanceBy(Duration.ofSeconds(10)) }

    private fun show(state: PolkaswapDisclaimerViewState) {
        compose.setContent { FearlessAppTheme { PolkaswapDisclaimerScreen(state, callbacks) } }
    }

    @Test fun termsAreListedWithNumberedResponsibilities() {
        show(unread)

        compose.onNodeWithText(context.getString(R.string.common_disclaimer)).assertIsDisplayed()
        compose.onNodeWithText("You are responsible for").assertIsDisplayed()
        compose.onNodeWithText("1. ").assertIsDisplayed()
        compose.onNodeWithText("Keeping your keys safe").assertIsDisplayed()
        compose.onNodeWithText("2. ").assertIsDisplayed()
        compose.onNodeWithText("Checking every route").assertIsDisplayed()
        compose.onNodeWithText("Read the terms and privacy notes").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.common_important).uppercase() + ": ", substring = true)
            .performScrollTo().assertIsDisplayed()
    }

    @Test fun highlightedTextOpensItsLinkAndPlainTextDoesNot() {
        show(unread)

        // Tap the first character: the link covers it in the first text, plain words start the second.
        compose.onNodeWithText("Polkaswap FAQ").performTouchInput { click(Offset(1f, height / 2f)) }
        compose.onNodeWithText("Read the terms and privacy notes").performScrollTo().performTouchInput { click(Offset(1f, height / 2f)) }

        assertEquals(listOf("link:https://faq.test"), calls)
    }

    @Test fun unreadTermsKeepContinueDisabledUntilTheSwitchIsOn() {
        show(unread)

        compose.onNode(isToggleable()).performScrollTo().assertIsOff().assertIsEnabled().performClick()
        compose.onNodeWithText(context.getString(R.string.common_continue)).performScrollTo().assertIsNotEnabled().performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.ux_back)).performClick()

        assertEquals(listOf("toggle", "back"), calls)
    }

    @Test fun checkedTermsAllowContinuing() {
        show(unread.copy(hasReadChecked = true))

        compose.onNode(isToggleable()).performScrollTo().assertIsOn().assertIsEnabled()
        compose.onNodeWithText(context.getString(R.string.common_continue)).performScrollTo().assertIsEnabled().performClick()

        assertEquals(listOf("continue"), calls)
    }

    @Test fun previouslyAcceptedTermsLockTheSwitchOn() {
        show(unread.copy(hasReadChecked = true, switchEnabled = false))

        compose.onNode(isToggleable()).performScrollTo().assertIsOn().assertIsNotEnabled().performClick()
        compose.onNodeWithText(context.getString(R.string.common_continue)).performScrollTo().assertIsEnabled()

        assertEquals(emptyList<String>(), calls)
    }

    @Test fun fragmentBundleNamesTheScreenWaitingForTheAnswer() {
        val bundle = PolkaswapDisclaimerFragment.getBundle(42)

        assertEquals(42, bundle.getInt(PolkaswapDisclaimerFragment.KEY_RESULT_DESTINATION))
    }
}
