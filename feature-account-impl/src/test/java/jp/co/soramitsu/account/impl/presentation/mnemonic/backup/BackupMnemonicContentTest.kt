package jp.co.soramitsu.account.impl.presentation.mnemonic.backup

import android.app.Activity
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import jp.co.soramitsu.account.impl.testutil.PrivateComposable
import jp.co.soramitsu.account.impl.testutil.string
import jp.co.soramitsu.account.impl.testutil.tap
import jp.co.soramitsu.backup.GoogleSignInResult
import jp.co.soramitsu.common.compose.component.MnemonicWordModel
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.model.WalletEcosystem
import jp.co.soramitsu.feature_account_impl.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h2000dp")
class BackupMnemonicContentTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val calls = mutableListOf<String>()
    private var launcher: ActivityResultLauncher<Intent>? = null
    private var failure: GoogleSignInResult? = null

    private val callback = object : BackupMnemonicCallback {
        override fun onNextClick(launcher: ActivityResultLauncher<Intent>) {
            calls += "next"
            this@BackupMnemonicContentTest.launcher = launcher
        }
        override fun onBackClick() { calls += "back" }
        override fun onGoogleSignInFailed(result: GoogleSignInResult) {
            calls += "googleFailed"
            failure = result
        }
        override fun onGoogleSignInSuccess() { calls += "googleSuccess" }
        override fun onSubstrateDerivationPathChange(path: String) { calls += "substrate:$path" }
        override fun onEthereumDerivationPathChange(path: String) { calls += "ethereum:$path" }
        override fun chooseEncryptionClicked() { calls += "encryption" }
        override fun onBackupWithGoogleClick(launcher: ActivityResultLauncher<Intent>) {
            calls += "google"
            this@BackupMnemonicContentTest.launcher = launcher
        }
    }

    private fun state(fromGoogle: Boolean) = BackupMnemonicState(
        mnemonicWords = listOf(MnemonicWordModel("1", "alpha"), MnemonicWordModel("2", "bravo")),
        selectedEncryptionType = "sr25519",
        accountTypes = listOf(WalletEcosystem.Substrate),
        substrateDerivationPath = "",
        ethereumDerivationPath = "",
        isFromGoogleBackup = fromGoogle
    )

    private fun render(fromGoogle: Boolean, googleAvailable: Boolean) {
        compose.setContent {
            FearlessAppTheme { BackupMnemonicContent(state = state(fromGoogle), isGoogleAvailable = googleAvailable, callback = callback) }
        }
    }

    @Test
    fun newWalletShowsWordsAdvancedOptionsAndGoogleBackup() {
        render(fromGoogle = false, googleAvailable = true)

        compose.onNodeWithText(string(R.string.backup_mnemonic_title)).assertIsDisplayed()
        compose.onNodeWithText("alpha").assertIsDisplayed()
        compose.onNodeWithText("bravo").assertIsDisplayed()
        compose.onNodeWithText(string(R.string.btn_backup_with_google)).assertIsDisplayed()

        compose.onNodeWithText(string(R.string.account_confirmation_title)).tap()
        compose.onNodeWithText(string(R.string.btn_backup_with_google)).tap()
        compose.waitForIdle()

        assertEquals(listOf("next", "google"), calls)
        assertNotNull(launcher)
    }

    @Test
    fun advancedSectionEditsDerivationPathsAndOpensTheEncryptionChooser() {
        render(fromGoogle = false, googleAvailable = false)

        compose.onNodeWithText(string(R.string.btn_backup_with_google)).assertDoesNotExist()
        compose.onNodeWithText(string(R.string.common_advanced)).tap()
        compose.onNodeWithText("sr25519").tap()
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("//polkadot")
        compose.onAllNodes(hasSetTextAction())[1].performTextInput("/44'/60'/0'/0/0")
        compose.waitForIdle()

        assertEquals("encryption", calls.first())
        assertEquals(true, calls.any { it.startsWith("substrate:") && it.endsWith("//polkadot") })
        assertEquals(true, calls.any { it.startsWith("ethereum:") && it.endsWith("/44'/60'/0'/0/0") })
    }

    @Test
    fun googleBackupFlowContinuesWithoutAdvancedOptions() {
        render(fromGoogle = true, googleAvailable = true)

        compose.onNodeWithText(string(R.string.common_advanced)).assertDoesNotExist()
        compose.onNodeWithText(string(R.string.btn_backup_with_google)).assertDoesNotExist()
        compose.onNodeWithText(string(R.string.common_continue)).tap()
        compose.onAllNodesWithText(string(R.string.backup_mnemonic_title))[0].assertIsDisplayed()

        assertEquals(listOf("next"), calls)
    }

    @Test
    fun signInResultReachesTheSuccessOrFailureCallback() {
        render(fromGoogle = false, googleAvailable = true)
        compose.onNodeWithText(string(R.string.btn_backup_with_google)).tap()
        compose.waitForIdle()

        val activity = shadowOf(compose.activity)
        compose.runOnUiThread { launcher!!.launch(Intent("sign-in")) }
        val first = activity.nextStartedActivityForResult
        compose.runOnUiThread { activity.receiveResult(first.intent, Activity.RESULT_OK, null) }
        compose.runOnUiThread { launcher!!.launch(Intent("sign-in")) }
        val second = activity.nextStartedActivityForResult
        compose.runOnUiThread {
            activity.receiveResult(
                second.intent,
                Activity.RESULT_CANCELED,
                GoogleSignInResult.failureData(GoogleSignInResult.Failure.NETWORK)
            )
        }
        compose.waitForIdle()

        assertEquals(listOf("google", "googleSuccess", "googleFailed"), calls)
        assertEquals(GoogleSignInResult.Network, failure)
    }

    @Test
    fun previewShowsSampleWords() {
        compose.setContent {
            PrivateComposable(
                "jp.co.soramitsu.account.impl.presentation.mnemonic.backup.BackupMnemonicContentKt",
                "PreviewBackupMnemonicContent"
            )
        }

        compose.onNodeWithText("three").assertIsDisplayed()
    }
}
