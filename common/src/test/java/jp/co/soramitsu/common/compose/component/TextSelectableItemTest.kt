package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
class TextSelectableItemTest {

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun setUp() {
        ShadowSystemClock.advanceBy(Duration.ofSeconds(10))
    }

    @Test
    fun itemShowsItsTextAndReportsSelection() {
        val selected = mutableListOf<String>()
        compose.setContent {
            FearlessAppTheme {
                Column {
                    TextSelectableItem(
                        state = TextSelectableItemState(isSelected = true, textResId = R.string.currencies_stub_text),
                        onSelectedCallback = { selected += "currencies" }
                    )
                    TextSelectableItem(
                        state = TextSelectableItemState(isSelected = false, textResId = R.string.nfts_stub),
                        onSelectedCallback = { selected += "nfts" }
                    )
                }
            }
        }

        compose.onNodeWithText("Currencies").assertIsDisplayed()
        compose.onNodeWithText("NFTs").performClick()
        compose.waitForIdle()

        assertEquals(listOf("nfts"), selected)
    }
}
