package jp.co.soramitsu.common.compose.component

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composer
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import java.time.Duration
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowSystemClock

@RunWith(RobolectricTestRunner::class)
class ConfirmScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun state(isLoading: Boolean, amount: String? = "10 KSM", note: Int? = R.string.pool_staking_unstake_alert) =
        ConfirmScreenViewState(
            toolbarViewState = ToolbarViewState("Confirm redeem", R.drawable.ic_arrow_back_24dp),
            amount = amount,
            tableItems = listOf(
                TitleValueViewState("From", "Main account", "5Grw"),
                TitleValueViewState("Network fee", "0.0051 KSM", "$0.32")
            ),
            assetIcon = GradientIconState.Local(R.drawable.ic_vector),
            titleRes = R.string.staking_redeem,
            additionalMessageRes = note,
            isLoading = isLoading
        )

    @Before
    fun setUp() {
        ShadowSystemClock.advanceBy(Duration.ofSeconds(10))
        Settings.Global.putString(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, "0")
    }

    @Test
    fun confirmationShowsAmountTableAndNoteAndReportsActions() {
        var navigation = 0
        var confirm = 0
        compose.setContent {
            FearlessAppTheme {
                ConfirmScreen(state = state(isLoading = false), onNavigationClick = { navigation++ }, onConfirm = { confirm++ })
            }
        }

        compose.onNodeWithText("Confirm redeem").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.staking_redeem)).assertIsDisplayed()
        compose.onNodeWithText("10 KSM").assertIsDisplayed()
        compose.onNodeWithText("Main account").assertIsDisplayed()
        compose.onNodeWithText(
            context.getString(R.string.common_note) + context.getString(R.string.pool_staking_unstake_alert)
        ).performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.ux_back)).performClick()
        compose.onNodeWithText(context.getString(R.string.common_confirm)).performScrollTo().performClick()
        compose.waitForIdle()

        assertEquals(1, navigation)
        assertEquals(1, confirm)
    }

    @Test
    fun loadingHidesTheConfirmationUntilItEnds() {
        var loading by mutableStateOf(true)
        compose.setContent {
            FearlessAppTheme {
                ConfirmScreen(state = state(isLoading = loading, amount = null, note = null), onNavigationClick = {}, onConfirm = {})
            }
        }

        compose.onAllNodesWithText("Confirm redeem").assertCountEquals(0)
        loading = false
        compose.waitForIdle()

        compose.onNodeWithText("Confirm redeem").assertIsDisplayed()
        compose.onAllNodesWithText("10 KSM").assertCountEquals(0)
        compose.onAllNodesWithText(context.getString(R.string.common_note), substring = true).assertCountEquals(0)
    }

    @Test
    fun previewShowsTheJoinPoolConfirmation() {
        compose.setContent {
            FearlessAppTheme { RenderPreview("jp.co.soramitsu.common.compose.component.ConfirmScreenKt", "ConfirmJoinPoolScreenPreview") }
        }

        compose.onNodeWithText("Account for join").assertExists()
        compose.onAllNodesWithText("10KSM").assertCountEquals(2)
    }

    @Composable
    private fun RenderPreview(fileClass: String, name: String) {
        val method = Class.forName(fileClass).getDeclaredMethod(name, Composer::class.java, Int::class.javaPrimitiveType)
        method.isAccessible = true
        method.invoke(null, currentComposer, 0)
    }
}
