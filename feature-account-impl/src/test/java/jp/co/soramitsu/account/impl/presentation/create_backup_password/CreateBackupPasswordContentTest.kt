package jp.co.soramitsu.account.impl.presentation.create_backup_password

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import jp.co.soramitsu.account.impl.testutil.iconClickables
import jp.co.soramitsu.account.impl.testutil.string
import jp.co.soramitsu.account.impl.testutil.tap
import jp.co.soramitsu.common.compose.component.TextInputViewState
import jp.co.soramitsu.common.compose.component.TextSelectableItemState
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
@Config(qualifiers = "w411dp-h1600dp")
class CreateBackupPasswordContentTest {

    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()
    private val callback = object : CreateBackupPasswordCallback {
        override fun onOriginPasswordChange(password: String) { calls += "origin:$password" }
        override fun onConfirmPasswordChange(password: String) { calls += "confirm:$password" }
        override fun onIsUserAgreedWithStatementsChange(isChecked: Boolean) { calls += "agreed:$isChecked" }
        override fun onBackClick() { calls += "back" }
        override fun onApplyPasswordClick() { calls += "apply" }
        override fun onAgreementsClick() { calls += "agreements" }
        override fun onOriginPasswordVisibilityClick() { calls += "originEye" }
        override fun onConfirmPasswordVisibilityClick() { calls += "confirmEye" }
    }

    private fun state(
        enabled: Boolean,
        matching: Int? = null,
        highlightRule: Boolean = false,
        loading: Boolean = false
    ) = CreateBackupPasswordViewState(
        originPasswordViewState = TextInputViewState(text = "", hint = "Password", endIcon = R.drawable.ic_eye_disabled),
        confirmPasswordViewState = TextInputViewState(text = "", hint = "Confirm", endIcon = R.drawable.ic_eye_disabled),
        isUserAgreedWithStatements = enabled,
        agreementsState = TextSelectableItemState(isSelected = enabled, textResId = R.string.create_backup_password_agreements),
        passwordMatchingTextResource = matching,
        highlightConfirmPassword = matching != null,
        isSetButtonEnabled = enabled,
        isLoading = loading,
        highlightPasswordRule = highlightRule
    )

    private fun render(state: CreateBackupPasswordViewState) {
        compose.setContent { FearlessAppTheme { CreateBackupPasswordContent(state = state, callback = callback) } }
    }

    @Test
    fun typingAgreeingAndSettingThePasswordReachTheCallbacks() {
        render(state(enabled = true))

        compose.onNodeWithText(string(R.string.create_backup_password_title)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.backup_password_rule)).assertIsDisplayed()
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("Secret#1")
        compose.onAllNodes(hasSetTextAction())[1].performTextInput("Secret#1")
        compose.onNodeWithText(string(R.string.create_backup_password_agreements)).tap()
        compose.onNodeWithText(string(R.string.create_backup_password_btn_set)).assertIsEnabled().tap()
        compose.waitForIdle()

        assertTrue(calls.contains("origin:Secret#1"))
        assertTrue(calls.contains("confirm:Secret#1"))
        assertEquals(listOf("agreements", "apply"), calls.filter { it == "agreements" || it == "apply" })
    }

    @Test
    fun backAndVisibilityIconsReachTheirCallbacks() {
        render(state(enabled = false))

        val icons = compose.onAllNodes(iconClickables())
        icons[0].tap()
        icons[1].tap()
        icons[2].tap()
        compose.waitForIdle()

        assertEquals(listOf("back", "originEye", "confirmEye"), calls)
    }

    @Test
    fun mismatchIsShownAndTheSetButtonStaysDisabled() {
        render(state(enabled = false, matching = R.string.create_backup_password_not_matched, highlightRule = true))

        compose.onNodeWithText(string(R.string.create_backup_password_not_matched)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.create_backup_password_btn_set)).assertIsNotEnabled().tap()
        compose.waitForIdle()

        assertTrue(calls.none { it == "apply" })
    }

    @Test
    fun previewShowsTheSampleFields() {
        compose.setContent { PreviewCreateBackupPasswordContent() }

        compose.onNodeWithText(string(R.string.create_backup_password_agreements)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.create_backup_password_btn_set)).assertIsEnabled()
    }
}
