package jp.co.soramitsu.account.impl.presentation.account.rename

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import jp.co.soramitsu.account.impl.testutil.PrivateComposable
import jp.co.soramitsu.account.impl.testutil.iconClickables
import jp.co.soramitsu.account.impl.testutil.string
import jp.co.soramitsu.account.impl.testutil.tap
import jp.co.soramitsu.common.compose.component.TextInputViewState
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.feature_account_impl.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h1000dp")
class RenameAccountDialogContentTest {

    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()
    private val callback = object : RenameAccountCallback {
        override fun accountNameChanged(accountName: CharSequence) { calls += "name:$accountName" }
        override fun onSaveClicked() { calls += "save" }
        override fun onBackClick() { calls += "back" }
    }

    @Test
    fun loadedNameCanBeEditedAndSaved() {
        compose.setContent {
            FearlessAppTheme { RenameAccountDialogContent(RenameAccountState(TextInputViewState("Old name", "Wallet name"), true), callback) }
        }

        compose.onNodeWithText(string(R.string.change_wallet_name)).assertIsDisplayed()
        compose.onNodeWithText("Old name").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).performTextInput("!")
        compose.onNodeWithText(string(R.string.common_save)).assertIsEnabled().tap()
        compose.onAllNodes(iconClickables())[0].tap()
        compose.waitForIdle()

        assertTrue(calls.first().startsWith("name:"))
        assertEquals(listOf("save", "back"), calls.drop(1))
    }

    @Test
    fun whileLoadingTheFieldIsEmptyAndSaveIsDisabled() {
        compose.setContent { FearlessAppTheme { RenameAccountDialogContent(RenameAccountState.Empty, callback) } }

        compose.onNodeWithText(string(R.string.rename_account_description)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.common_save)).assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextInput("New")
        compose.waitForIdle()

        assertEquals(listOf("name:New"), calls)
    }

    @Test
    fun previewShowsTheSampleName() {
        compose.setContent {
            PrivateComposable(
                "jp.co.soramitsu.account.impl.presentation.account.rename.RenameAccountDialogContentKt",
                "PreviewRenameAccountDialogContent"
            )
        }

        compose.onNodeWithText("my best wallet").assertIsDisplayed()
    }
}
