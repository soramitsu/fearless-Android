package jp.co.soramitsu.common.compose.component

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AccountInfoTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun accountInfoShowsCaptionAndName() {
        val state = AccountInfoViewState(address = "5Grw", accountName = "Main account", image = R.drawable.ic_wallet, caption = "Join pool from")
        compose.setContent { FearlessAppTheme { AccountInfo(state) } }

        compose.onNodeWithText("Join pool from").assertIsDisplayed()
        compose.onNodeWithText("Main account").assertIsDisplayed()
        assertEquals("5Grw", state.address)
    }
}
