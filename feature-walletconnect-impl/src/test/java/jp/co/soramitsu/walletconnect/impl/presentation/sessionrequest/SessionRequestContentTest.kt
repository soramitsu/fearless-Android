package jp.co.soramitsu.walletconnect.impl.presentation.sessionrequest

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import jp.co.soramitsu.common.compose.component.InfoItemViewState
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w480dp-h1000dp")
class SessionRequestContentTest {
    @get:Rule val compose = createComposeRule()
    private val callback = mock<SessionRequestScreenInterface>()

    @Before
    fun advanceClock() {
        ShadowSystemClock.advanceBy(Duration.ofSeconds(10))
    }

    @Test
    fun requestShowsWalletMessageOriginAndDispatchesPreview() {
        val state = SessionRequestViewState.default.copy(
            connectionUrl = "https://verified.example",
            wallet = SessionRequestViewState.default.wallet.copy(title = "Selected wallet"),
            message = InfoItemViewState("Message", "Login challenge", singleLine = false),
            originWarning = "Origin verification failed"
        )
        compose.setContent { FearlessAppTheme { SessionRequestContent(state, callback) } }
        listOf("https://verified.example", "Selected wallet", "Login challenge", "Origin verification failed").forEach {
            compose.onNodeWithText(it).performScrollTo().assertIsDisplayed()
        }
        ShadowSystemClock.advanceBy(Duration.ofSeconds(2))
        compose.onNodeWithText("Preview").assertIsDisplayed().performClick()
        verify(callback).onPreviewClick()
    }

    @Test
    fun generalWarningIsReplacedByOriginWarning() {
        val state = mutableStateOf(SessionRequestViewState.default)
        val warning = RuntimeEnvironment.getApplication().getString(jp.co.soramitsu.common.R.string.connection_sign_message_warning)
        compose.setContent { FearlessAppTheme { SessionRequestContent(state.value, callback) } }
        compose.onNodeWithText(warning).performScrollTo().assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(originWarning = "Different origin") }
        compose.onNodeWithText(warning).assertDoesNotExist()
        compose.onNodeWithText("Different origin").performScrollTo().assertIsDisplayed()
    }
}
