package jp.co.soramitsu.common.compose.component

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NftStubTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun stubExplainsThatNftsAreNotAvailable() {
        compose.setContent {
            FearlessAppTheme { NftStub() }
        }

        compose.onNodeWithTag("nft_stub_icon").assertExists()
        compose.onNodeWithText(context.getString(R.string.nft_stub_title)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.nft_stub_text)).assertIsDisplayed()
    }
}
