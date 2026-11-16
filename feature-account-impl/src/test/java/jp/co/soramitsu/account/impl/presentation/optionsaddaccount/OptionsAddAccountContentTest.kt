package jp.co.soramitsu.account.impl.presentation.optionsaddaccount

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import jp.co.soramitsu.account.impl.testutil.PrivateComposable
import jp.co.soramitsu.account.impl.testutil.iconClickables
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
class OptionsAddAccountContentTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun offersCreatingOrImportingAWallet() {
        val calls = mutableListOf<String>()
        compose.setContent {
            FearlessAppTheme {
                OptionsAddAccountContent(onCreate = { calls += "create" }, onImport = { calls += "import" }, onBackClicked = { calls += "close" })
            }
        }

        compose.onNodeWithText(string(R.string.recovery_source_type)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.create_new_account)).tap()
        compose.onNodeWithText(string(R.string.already_have_account)).tap()
        compose.onAllNodes(iconClickables())[0].tap()
        compose.waitForIdle()

        assertEquals(listOf("create", "import", "close"), calls)
    }

    @Test
    fun previewShowsBothChoices() {
        compose.setContent {
            PrivateComposable(
                "jp.co.soramitsu.account.impl.presentation.optionsaddaccount.OptionsAddAccountContentKt",
                "OptionsAddAccountScreenPreview"
            )
        }

        compose.onNodeWithText(string(R.string.already_have_account)).assertIsDisplayed()
    }
}
