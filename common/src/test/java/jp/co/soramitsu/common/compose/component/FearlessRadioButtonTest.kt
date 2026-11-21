package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composer
import androidx.compose.runtime.currentComposer
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FearlessRadioButtonTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun radioButtonsReportSelectionAndClicks() {
        var clicks = 0
        compose.setContent {
            FearlessAppTheme {
                Column {
                    FearlessRadioButton(selected = true, onClick = { clicks++ }, modifier = Modifier.testTag("selected"))
                    FearlessRadioButton(selected = false, onClick = { clicks++ }, modifier = Modifier.testTag("unselected"))
                    FearlessRadioButton(selected = false, onClick = { clicks++ }, enabled = false, modifier = Modifier.testTag("disabled"))
                    FearlessRadioButton(selected = true, onClick = null, modifier = Modifier.testTag("display"))
                }
            }
        }

        compose.onNodeWithTag("selected").assertIsSelected().performClick()
        compose.onNodeWithTag("unselected").assertIsNotSelected().performClick()
        compose.onNodeWithTag("disabled").assertIsNotEnabled().performClick()
        compose.onNodeWithTag("display").assertHasNoClickAction()
        compose.waitForIdle()

        assertEquals(2, clicks)
    }

    @Test
    fun previewTogglesItsFirstButton() {
        compose.setContent {
            FearlessAppTheme { RenderPreview("jp.co.soramitsu.common.compose.component.FearlessRadioButtonKt", "Preview") }
        }

        val buttons = compose.onAllNodes(hasClickAction()).assertCountEquals(2)
        buttons[0].assertIsSelected().performClick()
        compose.waitForIdle()
        compose.onAllNodes(hasClickAction())[0].assertIsNotSelected()
    }

    @Composable
    private fun RenderPreview(fileClass: String, name: String) {
        val method = Class.forName(fileClass).getDeclaredMethod(name, Composer::class.java, Int::class.javaPrimitiveType)
        method.isAccessible = true
        method.invoke(null, currentComposer, 0)
    }
}
