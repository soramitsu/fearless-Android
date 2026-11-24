package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composer
import androidx.compose.runtime.currentComposer
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.isNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import java.time.Duration
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowSystemClock

@RunWith(RobolectricTestRunner::class)
class TextInputTest {

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun setUp() {
        ShadowSystemClock.advanceBy(Duration.ofSeconds(10))
    }

    @Test
    fun activeInputReportsTextFocusAndEndIconClicks() {
        val inputs = mutableListOf<String>()
        val focus = mutableListOf<FocusState>()
        var endIconClicks = 0
        compose.setContent {
            FearlessAppTheme {
                TextInput(
                    state = TextInputViewState(text = "My pool", hint = "Pool name", endIcon = R.drawable.ic_close_16_circle),
                    onInput = { inputs += it },
                    onFocusChanged = { focus += it },
                    onEndIconClick = { endIconClicks++ }
                )
            }
        }

        compose.onNodeWithText("Pool name").assertIsDisplayed()
        val field = compose.onNode(hasSetTextAction())
        field.assertIsEnabled().assert(hasText("My pool"))
        field.performClick()
        field.performTextReplacement("Best pool")
        compose.onAllNodes(hasClickAction() and !hasSetTextAction()).assertCountEquals(1)[0].performClick()
        compose.waitForIdle()

        assertEquals(listOf("Best pool"), inputs)
        assertTrue(focus.any { it.isFocused })
        assertEquals(1, endIconClicks)
    }

    @Test
    fun emptyInputShowsPlaceholderAndInactivePasswordIsDisabled() {
        compose.setContent {
            FearlessAppTheme {
                Column {
                    TextInput(
                        state = TextInputViewState(text = "", hint = "Wallet name", placeholder = "Enter a name"),
                        onInput = {}
                    )
                    TextInput(
                        state = TextInputViewState(
                            text = "secret",
                            hint = "Password",
                            isActive = false,
                            mode = TextInputViewState.Mode.Password
                        ),
                        onInput = {}
                    )
                }
            }
        }

        compose.onNodeWithText("Wallet name").assertIsDisplayed()
        compose.onNodeWithText("Enter a name", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Password").assertIsDisplayed()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(1)[0].assertIsEnabled()
        compose.onNode(isNotEnabled()).assertIsNotEnabled()
        // Password text is masked on screen.
        compose.onAllNodes(hasText("secret")).assertCountEquals(0)
    }

    @Test
    fun previewShowsBothInputs() {
        compose.setContent {
            FearlessAppTheme { RenderPreview("jp.co.soramitsu.common.compose.component.TextInputKt", "TextInputPreview") }
        }

        compose.onAllNodes(hasText("Pool name")).assertCountEquals(2)
    }

    @Composable
    private fun RenderPreview(fileClass: String, name: String) {
        val method = Class.forName(fileClass).getDeclaredMethod(name, Composer::class.java, Int::class.javaPrimitiveType)
        method.isAccessible = true
        method.invoke(null, currentComposer, 0)
    }
}
