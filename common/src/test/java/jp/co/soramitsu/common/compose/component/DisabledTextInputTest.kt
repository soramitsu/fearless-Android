package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DisabledTextInputTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun valueIsShownAndOnlyAnIconWithCallbackIsClickable() {
        var clicks = 0
        compose.setContent {
            FearlessAppTheme {
                Column {
                    DisabledTextInput(hint = "Hash", value = "0xabc", endIcon = R.drawable.ic_more_vertical, endIconClick = { clicks++ })
                    DisabledTextInput(hint = "Block", value = "123", endIcon = R.drawable.ic_more_vertical)
                    DisabledTextInput(hint = "Fee", value = "0.1 DOT")
                }
            }
        }

        compose.onNodeWithText("Hash").assertIsDisplayed()
        compose.onNodeWithText("0xabc").assertIsDisplayed()
        compose.onNodeWithText("123").assertIsDisplayed()
        compose.onNodeWithText("0.1 DOT").assertIsDisplayed()
        compose.onAllNodes(hasClickAction()).assertCountEquals(1)[0].performClick()
        compose.waitForIdle()

        assertEquals(1, clicks)
    }

    @Test
    fun previewShowsTheHash() {
        compose.setContent { DisabledTextInputPreview() }

        compose.onNodeWithText("Hash").assertIsDisplayed()
        compose.onNodeWithText("0xjewhfbg4yfrheuvbfwyu4rhbv24uyrgh248urhfg").assertIsDisplayed()
    }
}
