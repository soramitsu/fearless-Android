package jp.co.soramitsu.account.impl.presentation.exporting

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import jp.co.soramitsu.account.impl.testutil.PrivateComposable
import jp.co.soramitsu.account.impl.testutil.string
import jp.co.soramitsu.account.impl.testutil.tap
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.feature_account_impl.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SecurityWarningDialogTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun warningProceedsOrCancels() {
        val calls = mutableListOf<String>()
        compose.setContent { SecurityWarningDialog(onConfirm = { calls += "proceed" }, onDismiss = { calls += "cancel" }) }

        compose.onNodeWithText(string(R.string.account_export_warning_title)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.account_export_warning_message)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.common_proceed)).tap()
        compose.onNodeWithText(string(R.string.common_cancel)).tap()
        compose.waitForIdle()

        assertEquals(listOf("proceed", "cancel"), calls)
    }

    @Test
    fun previewShowsTheWarning() {
        compose.setContent {
            FearlessAppTheme {
                PrivateComposable("jp.co.soramitsu.account.impl.presentation.exporting.SecurityWarningDialogKt", "SecurityWarningDialogPreview")
            }
        }

        compose.onNodeWithText(string(R.string.account_export_warning_title)).assertIsDisplayed()
    }
}
