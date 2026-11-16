package jp.co.soramitsu.account.impl.presentation.backup_wallet

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import jp.co.soramitsu.account.impl.testutil.PrivateComposable
import jp.co.soramitsu.account.impl.testutil.iconClickables
import jp.co.soramitsu.account.impl.testutil.string
import jp.co.soramitsu.account.impl.testutil.tap
import jp.co.soramitsu.backup.GoogleSignInResult
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.feature_account_impl.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h1400dp")
class BackupWalletContentTest {

    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()
    private val callback = object : BackupWalletCallback {
        override fun onBackClick() { calls += "back" }
        override fun onShowMnemonicPhraseClick() { calls += "mnemonic" }
        override fun onShowRawSeedClick() { calls += "seed" }
        override fun onExportJsonClick() { calls += "json" }
        override fun onDeleteGoogleBackupClick() { calls += "deleteGoogle" }
        override fun onGoogleBackupClick() { calls += "google" }
        override fun onGoogleSignInFailed(result: GoogleSignInResult) { calls += "googleFailed" }
        override fun onGoogleSignInSuccess() { calls += "googleSuccess" }
    }

    private fun render(state: BackupWalletState, googleAvailable: Boolean) {
        compose.setContent { FearlessAppTheme { BackupWalletContent(state = state, isGoogleAvailable = googleAvailable, callback = callback) } }
    }

    @Test
    fun everySupportedExportIsOfferedAndReachesItsCallback() {
        render(
            BackupWalletState.Empty.copy(
                walletItem = BackupWalletState.Empty.walletItem.copy(title = "Main"),
                isSeedBackupSupported = true,
                isGoogleBackupAllowed = true
            ),
            googleAvailable = true
        )

        compose.onNodeWithText(string(R.string.export_wallet)).assertIsDisplayed()
        compose.onNodeWithText("Main").assertIsDisplayed()
        compose.onNodeWithText(string(R.string.backup_wallet_warning_about_lose_phrase)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.backup_wallet_delete_google_backup)).assertDoesNotExist()

        compose.onNodeWithText(string(R.string.backup_wallet_show_mnemonic_phrase)).tap()
        compose.onNodeWithText(string(R.string.backup_wallet_show_raw_seed)).tap()
        compose.onNodeWithText(string(R.string.backup_wallet_export_json)).tap()
        compose.onNodeWithText(string(R.string.backup_wallet_backup_to_google)).tap()
        compose.onAllNodes(iconClickables())[0].tap()
        compose.waitForIdle()

        assertEquals(listOf("mnemonic", "seed", "json", "google", "back"), calls)
    }

    @Test
    fun walletAlreadyOnGoogleDriveOffersDeletingTheBackup() {
        render(
            BackupWalletState.Empty.copy(isGoogleBackupAllowed = true, isAuthedToGoogle = true, isWalletSavedInGoogle = true),
            googleAvailable = true
        )

        compose.onNodeWithText(string(R.string.backup_wallet_backup_to_google)).assertDoesNotExist()
        compose.onNodeWithText(string(R.string.backup_wallet_delete_google_backup)).tap()
        compose.waitForIdle()

        assertEquals(listOf("deleteGoogle"), calls)
    }

    @Test
    fun unsupportedExportsAndUnavailableGoogleAreHidden() {
        render(
            BackupWalletState.Empty.copy(
                isMnemonicBackupSupported = false,
                isJsonBackupSupported = false,
                isGoogleBackupAllowed = true
            ),
            googleAvailable = false
        )

        compose.onNodeWithText(string(R.string.backup_wallet_show_mnemonic_phrase)).assertDoesNotExist()
        compose.onNodeWithText(string(R.string.backup_wallet_show_raw_seed)).assertDoesNotExist()
        compose.onNodeWithText(string(R.string.backup_wallet_export_json)).assertDoesNotExist()
        compose.onNodeWithText(string(R.string.backup_wallet_backup_to_google)).assertDoesNotExist()
    }

    @Test
    fun previewOffersPhraseAndJsonWithoutGoogle() {
        compose.setContent {
            PrivateComposable("jp.co.soramitsu.account.impl.presentation.backup_wallet.BackupWalletContentKt", "PreviewBackupWalletContent")
        }

        compose.onNodeWithText(string(R.string.backup_wallet_show_mnemonic_phrase)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.backup_wallet_backup_to_google)).assertDoesNotExist()
    }
}
