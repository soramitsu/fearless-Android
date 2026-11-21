package jp.co.soramitsu.onboarding.impl.welcome.select_import_mode

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.feature_onboarding_impl.R
import jp.co.soramitsu.onboarding.impl.testutil.PrivateComposable
import jp.co.soramitsu.onboarding.impl.testutil.allowNextTap
import jp.co.soramitsu.onboarding.impl.testutil.text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class SelectImportModeContentTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val calls = mutableListOf<String>()
    private val callback = object : SelectImportModeScreenInterface {
        override fun onCancelClick() { calls += "cancel" }
        override fun onGoogleClick() { calls += "google" }
        override fun onMnemonicPhraseClick() { calls += "mnemonic" }
        override fun onRawSeedClick() { calls += "seed" }
        override fun onJsonClick() { calls += "json" }
        override fun onGoogleLoginError(message: String) { calls += "error:$message" }
        override fun onGoogleSignInSuccess() { calls += "signed-in" }
        override fun onPreinstalledImportClick() { calls += "preinstalled" }
    }

    @Before
    fun moveClockPastTheDoubleTapGuard() = allowNextTap()

    private fun show(state: SelectImportModeState, isGoogleAvailable: Boolean) {
        compose.setContent {
            FearlessAppTheme {
                SelectImportModeContent(state = state, isGoogleAvailable = isGoogleAvailable, callback = callback)
            }
        }
        compose.waitForIdle()
    }

    private fun tap(label: String) {
        compose.onNodeWithText(label).performClick()
        compose.waitForIdle()
        allowNextTap()
    }

    @Test
    fun everyImportModeReportsItsChoice() {
        show(SelectImportModeState(preinstalledFeatureEnabled = true), isGoogleAvailable = true)
        compose.onNodeWithText(text(R.string.select_import_mode_title)).assertIsDisplayed()

        tap(text(R.string.select_import_mode_btn_mnemonic))
        tap(text(R.string.select_import_mode_btn_raw_seed))
        tap(text(R.string.select_import_mode_btn_json))
        tap(text(R.string.select_import_mode_btn_google))
        tap(text(R.string.onboarding_preinstalled_wallet_button_text))
        tap(text(R.string.common_cancel))

        assertEquals(listOf("mnemonic", "seed", "json", "google", "preinstalled", "cancel"), calls)
    }

    @Test
    fun googleAndPreinstalledAreOfferedOnlyWhenAvailable() {
        show(SelectImportModeState(preinstalledFeatureEnabled = false), isGoogleAvailable = false)

        compose.onAllNodesWithText(text(R.string.select_import_mode_btn_google)).assertCountEquals(0)
        compose.onAllNodesWithText(text(R.string.onboarding_preinstalled_wallet_button_text)).assertCountEquals(0)
        compose.onNodeWithText(text(R.string.select_import_mode_btn_json)).assertIsDisplayed()
    }

    @Test
    fun previewOffersEveryMode() {
        compose.setContent {
            PrivateComposable(
                "jp.co.soramitsu.onboarding.impl.welcome.select_import_mode.SelectImportModeContentKt",
                "PreviewSelectImportModeContent"
            )
        }
        compose.waitForIdle()

        compose.onNodeWithText(text(R.string.select_import_mode_btn_google)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.onboarding_preinstalled_wallet_button_text)).assertIsDisplayed()
        tap(text(R.string.common_cancel))
        assertTrue(calls.isEmpty())
    }

    @Test
    fun dialogBundleSaysWhetherAnAccountIsBeingAdded() {
        assertFalse(SelectImportModeDialog.getBundle().getBoolean(SelectImportModeDialog.IS_ADDING_ACCOUNT_KEY))
        assertTrue(SelectImportModeDialog.getBundle(isAddingAccount = true).getBoolean(SelectImportModeDialog.IS_ADDING_ACCOUNT_KEY))
        assertEquals("RESULT_IMPORT_MODE", SelectImportModeDialog.RESULT_IMPORT_MODE)
    }
}
