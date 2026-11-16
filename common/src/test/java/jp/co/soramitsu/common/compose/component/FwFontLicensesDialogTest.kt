package jp.co.soramitsu.common.compose.component

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.compose.theme.fw.FwFontLicense
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FwFontLicensesDialogTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun dialogListsEveryLicenceByParagraphAndClosesFromItsButton() {
        var closes = 0
        compose.setContent {
            FearlessAppTheme {
                FwFontLicensesDialog(
                    licenses = listOf(
                        FwFontLicense("Sora", "Copyright Sora.\n\nPermission is granted."),
                        FwFontLicense("Inter", "Copyright Inter.")
                    ),
                    onClose = { closes++ }
                )
            }
        }

        compose.onNode(isDialog()).assertExists()
        compose.onNodeWithTag("fw-font-licenses-title").assertTextEquals(context.getString(R.string.fw_font_licenses))
        compose.onNodeWithTag("fw-font-license-Sora").assertTextEquals("Sora")
        compose.onNodeWithText("Copyright Sora.").assertIsDisplayed()
        compose.onNodeWithText("Permission is granted.").assertIsDisplayed()
        compose.onNodeWithTag("fw-font-license-Inter").assertTextEquals("Inter")
        compose.onNodeWithText("Copyright Inter.").assertIsDisplayed()
        compose.onNodeWithTag("fw-font-licenses-end").assertExists()

        compose.onNodeWithTag("fw-font-licenses-close").assertIsDisplayed().performClick()
        compose.waitForIdle()

        assertEquals(1, closes)
    }
}
