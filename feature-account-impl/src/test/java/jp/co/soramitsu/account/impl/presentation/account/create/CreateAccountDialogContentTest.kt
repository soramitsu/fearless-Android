package jp.co.soramitsu.account.impl.presentation.account.create

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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h1000dp")
class CreateAccountDialogContentTest {

    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()
    private val callback = object : CreateAccountCallback {
        override fun accountNameChanged(accountName: CharSequence) { calls += "name:$accountName" }
        override fun nextClicked() { calls += "next" }
        override fun onBackClick() { calls += "back" }
    }

    @Test
    fun namingTheWalletEnablesContinue() {
        compose.setContent {
            FearlessAppTheme { CreateAccountDialogContent(CreateAccountState(TextInputViewState("", "Wallet name"), true), callback) }
        }

        compose.onNodeWithText(string(R.string.create_account_title)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.create_account_edit_text_visibility)).assertIsDisplayed()
        compose.onNode(hasSetTextAction()).performTextInput("Travel")
        compose.onNodeWithText(string(R.string.common_continue)).assertIsEnabled().tap()
        compose.onAllNodes(iconClickables())[0].tap()
        compose.waitForIdle()

        assertEquals(listOf("name:Travel", "next", "back"), calls)
    }

    @Test
    fun emptyNameKeepsContinueDisabled() {
        compose.setContent { FearlessAppTheme { CreateAccountDialogContent(CreateAccountState.Empty, callback) } }

        compose.onNodeWithText(string(R.string.common_continue)).assertIsNotEnabled().tap()
        compose.waitForIdle()

        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun previewShowsTheSampleName() {
        compose.setContent {
            PrivateComposable(
                "jp.co.soramitsu.account.impl.presentation.account.create.CreateAccountDialogContentKt",
                "PreviewCreateAccountDialogContent"
            )
        }

        compose.onNodeWithText("my best wallet").assertIsDisplayed()
    }
}
