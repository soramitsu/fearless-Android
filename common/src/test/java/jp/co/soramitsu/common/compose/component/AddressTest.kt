package jp.co.soramitsu.common.compose.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composer
import androidx.compose.runtime.currentComposer
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
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
class AddressTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun addressIsShortenedAndCopiedOnClick() {
        var copies = 0
        compose.setContent {
            FearlessAppTheme {
                Address(address = "5GrwvaEF5zXb26Fz9rcQpDWS57CtERHpNehXCPcNoHGKutQY", onClick = { copies++ })
            }
        }

        compose.onNodeWithTag("address", useUnmergedTree = true).assertTextEquals("5GrwvaEF...oHGKutQY")
        compose.onNodeWithTag("address_copy_icon", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("address", useUnmergedTree = true).performClick()
        compose.waitForIdle()

        assertEquals(1, copies)
    }

    @Test
    fun previewShowsAShortenedAddress() {
        compose.setContent { RenderPreview("jp.co.soramitsu.common.compose.component.AddressKt", "AddressPreview") }

        compose.onNodeWithTag("address", useUnmergedTree = true).assertTextEquals("0x321412...vgergsdf")
    }

    @Composable
    private fun RenderPreview(fileClass: String, name: String) {
        val method = Class.forName(fileClass).getDeclaredMethod(name, Composer::class.java, Int::class.javaPrimitiveType)
        method.isAccessible = true
        method.invoke(null, currentComposer, 0)
    }
}
