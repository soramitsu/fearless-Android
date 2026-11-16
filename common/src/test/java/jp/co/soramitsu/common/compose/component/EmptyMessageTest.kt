package jp.co.soramitsu.common.compose.component

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import java.time.Duration
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.compose.theme.fw.FwEffects
import jp.co.soramitsu.common.compose.theme.fw.FwTier
import jp.co.soramitsu.common.compose.theme.fw.LocalFwEffects
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowSystemClock

@RunWith(RobolectricTestRunner::class)
class EmptyMessageTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun moveClockPastTheDoubleTapGuard() {
        ShadowSystemClock.advanceBy(Duration.ofSeconds(10))
    }

    @Test
    fun emptyMessageShowsTheDefaultTitleWithItsMessage() {
        compose.show { EmptyMessage(message = R.string.choose_amount_error_balance) }

        compose.onNodeWithText(context.getString(R.string.common_search_assets_alert_title)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.choose_amount_error_balance)).assertIsDisplayed()
    }

    @Test
    fun emptyMessageAcceptsItsOwnIconAndTitle() {
        compose.show {
            EmptyMessage(icon = R.drawable.ic_alert_16, title = R.string.common_warning, message = R.string.network_issue_stub)
        }

        compose.onNodeWithText(context.getString(R.string.common_warning)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.network_issue_stub)).assertIsDisplayed()
    }

    @Test
    fun previewShowsTheBalanceError() {
        compose.show { EmptyMessagePreview() }

        compose.onNodeWithText(context.getString(R.string.choose_amount_error_balance)).assertIsDisplayed()
    }

    @Test
    fun listDialogShowsItsTitleAndReportsTheChosenItem() {
        val chosen = mutableListOf<Choice>()
        compose.show {
            Column {
                ListDialog(
                    state = ListDialogState(titleRes = R.string.common_warning, items = listOf(Choice(R.string.common_close), Choice(R.string.top_up))),
                    onSelected = { chosen += it }
                )
            }
        }

        compose.onNodeWithText(context.getString(R.string.common_warning)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.top_up)).tap()
        compose.onNodeWithText(context.getString(R.string.common_close)).tap()

        assertEquals(listOf(Choice(R.string.top_up), Choice(R.string.common_close)), chosen)
    }

    private data class Choice(override val titleRes: Int) : ListDialogState.Item

    private fun SemanticsNodeInteraction.tap() {
        performClick()
        compose.waitForIdle()
        ShadowSystemClock.advanceBy(Duration.ofSeconds(10))
    }

    private fun ComposeContentTestRule.show(content: @Composable () -> Unit) = setContent {
        FearlessAppTheme {
            CompositionLocalProvider(
                LocalFwEffects provides FwEffects(FwTier.Full, highContrast = false, reduceMotion = true),
                content = content
            )
        }
    }
}
