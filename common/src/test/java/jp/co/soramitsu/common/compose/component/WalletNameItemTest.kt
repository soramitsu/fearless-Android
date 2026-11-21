package jp.co.soramitsu.common.compose.component

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
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
class WalletNameItemTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val state = WalletNameItemViewState(
        id = 3,
        title = "Signer wallet",
        walletIcon = R.drawable.ic_wallet,
        isSelected = true,
        subtitle = "Backed up from the extension"
    )

    @Before
    fun moveClockPastTheDoubleTapGuard() {
        ShadowSystemClock.advanceBy(Duration.ofSeconds(10))
    }

    @Test
    fun selectedRowShowsTitleSubtitleAndRoutesGestures() {
        val events = mutableListOf<String>()
        compose.setContent {
            FearlessAppTheme {
                WalletNameItem(
                    state = state,
                    onOptionsClick = { events += "options:${it.id}" },
                    onSelected = { events += "selected:${it.id}" },
                    onLongClick = { events += "long:${it.id}" }
                )
            }
        }

        compose.onNodeWithText("Signer wallet", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Backed up from the extension", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Signer wallet").assertIsSelected()

        compose.onNodeWithText("Signer wallet").performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.ux_more)).performClick()
        compose.onNodeWithText("Signer wallet").performTouchInput { longClick() }
        compose.waitForIdle()

        assertEquals(listOf("selected:3", "options:3", "long:3"), events)
    }

    @Test
    fun plainRowHasNoSubtitleOptionsOrSelection() {
        compose.setContent {
            FearlessAppTheme {
                Column {
                    WalletNameItem(state = state.copy(isSelected = false, subtitle = null), onSelected = {})
                }
            }
        }

        compose.onNodeWithText("Signer wallet").assert(isSelected().not())
        compose.onAllNodesWithContentDescription(context.getString(R.string.ux_more)).assertCountEquals(0)
        compose.onNodeWithText("Backed up from the extension", useUnmergedTree = true).assertDoesNotExist()
    }
}
