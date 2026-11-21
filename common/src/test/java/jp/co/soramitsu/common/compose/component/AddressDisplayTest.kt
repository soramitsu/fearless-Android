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
class AddressDisplayTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun addressIsShownInFullAndOnlyAnIconWithCallbackIsClickable() {
        var clicks = 0
        compose.setContent {
            FearlessAppTheme {
                Column {
                    AddressDisplay(
                        state = AddressDisplayState("From", "0xfrom", image = R.drawable.ic_wallet, endIcon = R.drawable.ic_more_vertical),
                        endIconClick = { clicks++ }
                    )
                    AddressDisplay(state = AddressDisplayState("To", "0xto", image = R.drawable.ic_wallet, endIcon = R.drawable.ic_more_vertical))
                    AddressDisplay(state = AddressDisplayState("Via", "0xvia", image = R.drawable.ic_wallet))
                }
            }
        }

        listOf("From", "0xfrom", "To", "0xto", "Via", "0xvia").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        compose.onAllNodes(hasClickAction()).assertCountEquals(1)[0].performClick()
        compose.waitForIdle()

        assertEquals(1, clicks)
    }

    @Test
    fun previewShowsTheSender() {
        compose.setContent { AddressDisplayPreview() }

        compose.onNodeWithText("From").assertIsDisplayed()
        compose.onNodeWithText("0xksjdnbfuwyt4g5fuy24guyf247ryfg2374yrgf73yrgf").assertIsDisplayed()
    }
}
