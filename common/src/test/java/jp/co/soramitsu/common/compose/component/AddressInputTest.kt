package jp.co.soramitsu.common.compose.component

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composer
import androidx.compose.runtime.currentComposer
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
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
class AddressInputTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        ShadowSystemClock.advanceBy(Duration.ofSeconds(10))
    }

    @Test
    fun filledInputReportsTypingAndClearsWithTheCloseIcon() {
        val inputs = mutableListOf<String>()
        var cleared = 0
        compose.setContent {
            FearlessAppTheme {
                AddressInput(
                    state = AddressInputState(title = "Send to", input = "5GrwvaEF", image = R.drawable.ic_address_placeholder),
                    onInput = { inputs += it },
                    onInputClear = { cleared++ },
                    onPaste = { error("paste is only offered for an empty input") }
                )
            }
        }

        compose.onNodeWithText("Send to").assertIsDisplayed()
        val field = compose.onNode(hasSetTextAction())
        field.performClick()
        field.performTextReplacement("5FHneW46")
        compose.onAllNodes(hasClickAction() and !hasSetTextAction()).assertCountEquals(1)[0].performClick()
        compose.waitForIdle()

        assertEquals(listOf("5FHneW46", ""), inputs)
        assertEquals(1, cleared)
        compose.onAllNodesWithText(context.getString(R.string.chip_paste)).assertCountEquals(0)
    }

    @Test
    fun emptyInputShowsHintAndOffersPaste() {
        var pasted = 0
        compose.setContent {
            FearlessAppTheme {
                AddressInput(
                    state = AddressInputState(title = "Recipient", input = "", image = R.drawable.ic_address_placeholder),
                    onPaste = { pasted++ }
                )
            }
        }

        compose.onNodeWithText("Public address", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.chip_paste)).performClick()
        compose.waitForIdle()

        assertEquals(1, pasted)
    }

    @Test
    fun readOnlyInputShowsTheShortenedAddressWithoutClear() {
        val address = "0x1234567890abcdef1234567890abcdef"
        compose.setContent {
            FearlessAppTheme {
                Column {
                    AddressInput(
                        state = AddressInputState(
                            title = "From",
                            input = address,
                            image = R.drawable.ic_address_placeholder,
                            editable = false,
                            showClear = false
                        )
                    )
                }
            }
        }

        val field = compose.onNodeWithText("0x123456...90abcdef")
        field.assertIsNotEnabled()
        field.assert(hasText("0x123456...90abcdef"))
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
    }

    @Test
    fun previewShowsFilledAndEmptyInputs() {
        compose.setContent {
            FearlessAppTheme { RenderPreview("jp.co.soramitsu.common.compose.component.AddressInputKt", "AccountInputPreview") }
        }

        compose.onAllNodesWithText("Send to").assertCountEquals(2)
        compose.onAllNodesWithText(context.getString(R.string.chip_paste)).assertCountEquals(2)
    }

    @Composable
    private fun RenderPreview(fileClass: String, name: String) {
        val method = Class.forName(fileClass).getDeclaredMethod(name, Composer::class.java, Int::class.javaPrimitiveType)
        method.isAccessible = true
        method.invoke(null, currentComposer, 0)
    }
}
