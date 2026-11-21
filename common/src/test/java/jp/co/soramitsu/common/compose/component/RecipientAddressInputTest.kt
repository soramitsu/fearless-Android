package jp.co.soramitsu.common.compose.component

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composer
import androidx.compose.runtime.currentComposer
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
class RecipientAddressInputTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        ShadowSystemClock.advanceBy(Duration.ofSeconds(10))
    }

    @Test
    fun filledAddressShowsAddressAndClears() {
        var cleared = 0
        var pasted = 0
        compose.setContent {
            FearlessAppTheme {
                RecipientAddressInput(
                    state = RecipientAddressInput.Filled(title = "Send to", address = "0x23d2ef23", image = R.drawable.ic_wallet),
                    onPaste = { pasted++ },
                    onClear = { cleared++ }
                )
            }
        }

        compose.onNodeWithText("Send to").assertIsDisplayed()
        compose.onNodeWithText("0x23d2ef23").assertIsDisplayed()
        compose.onAllNodes(hasClickAction()).assertCountEquals(1)[0].performClick()
        compose.waitForIdle()

        assertEquals(1, cleared)
        assertEquals(0, pasted)
        compose.onAllNodesWithText(context.getString(R.string.chip_paste)).assertCountEquals(0)
    }

    @Test
    fun emptyAddressShowsHintAndOffersPaste() {
        var pasted = 0
        compose.setContent {
            FearlessAppTheme {
                Column {
                    RecipientAddressInput(
                        state = RecipientAddressInput.Empty(title = "Recipient", hint = "Public address"),
                        onPaste = { pasted++ },
                        onClear = {}
                    )
                    RecipientAddressInput(
                        state = RecipientAddressInput.Filled(title = "Filled", address = "5Grw", image = R.drawable.ic_wallet),
                        onPaste = {},
                        onClear = {}
                    )
                }
            }
        }

        compose.onNodeWithText("Public address").assertIsDisplayed()
        compose.onNodeWithText("Filled").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.chip_paste)).performClick()
        compose.waitForIdle()

        assertEquals(1, pasted)
    }

    @Test
    fun previewShowsFilledAndEmptyStates() {
        compose.setContent {
            FearlessAppTheme { RenderPreview("jp.co.soramitsu.common.compose.component.RecipientAddressInputKt", "AccountInputPreview") }
        }

        compose.onAllNodes(hasText("Send to")).assertCountEquals(2)
        compose.onNodeWithText("BlueBird").assertIsDisplayed()
        compose.onNodeWithText("Public address").assertIsDisplayed()
    }

    @Composable
    private fun RenderPreview(fileClass: String, name: String) {
        val method = Class.forName(fileClass).getDeclaredMethod(name, Composer::class.java, Int::class.javaPrimitiveType)
        method.isAccessible = true
        method.invoke(null, currentComposer, 0)
    }
}
