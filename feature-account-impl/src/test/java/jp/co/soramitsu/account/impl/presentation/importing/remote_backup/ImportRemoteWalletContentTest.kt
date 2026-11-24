package jp.co.soramitsu.account.impl.presentation.importing.remote_backup

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import jp.co.soramitsu.account.impl.presentation.importing.remote_backup.model.BackupOrigin
import jp.co.soramitsu.account.impl.presentation.importing.remote_backup.model.WrappedBackupAccountMeta
import jp.co.soramitsu.account.impl.presentation.importing.remote_backup.screens.EnterBackupPasswordState
import jp.co.soramitsu.account.impl.presentation.importing.remote_backup.screens.RemoteWalletListError
import jp.co.soramitsu.account.impl.presentation.importing.remote_backup.screens.RemoteWalletListState
import jp.co.soramitsu.account.impl.presentation.importing.remote_backup.screens.WalletImportedState
import jp.co.soramitsu.account.impl.presentation.importing.remote_backup.views.CompactWalletItemViewState
import jp.co.soramitsu.account.impl.testutil.PrivateComposable
import jp.co.soramitsu.account.impl.testutil.iconClickables
import jp.co.soramitsu.account.impl.testutil.string
import jp.co.soramitsu.account.impl.testutil.tap
import jp.co.soramitsu.backup.domain.models.BackupAccountMeta
import jp.co.soramitsu.common.compose.component.TextInputViewState
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.feature_account_impl.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h1400dp")
class ImportRemoteWalletContentTest {

    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()
    private val callback = object : ImportRemoteWalletCallback {
        override fun onCreateNewWallet() { calls += "create" }
        override fun onContinueClick() { calls += "continue" }
        override fun onWalletSelected(backupAccount: WrappedBackupAccountMeta) { calls += "select:${backupAccount.backupMeta.name}" }
        override fun onWalletLongClick(backupAccount: WrappedBackupAccountMeta) { calls += "long:${backupAccount.backupMeta.name}" }
        override fun onBackClick() { calls += "back" }
        override fun loadRemoteWallets() { calls += "load" }
        override fun onErrorActionClick() { calls += "errorAction" }
        override fun onImportAnotherWayClick() { calls += "anotherWay" }
        override fun onUseAnotherGoogleAccountClick() { calls += "anotherAccount" }
        override fun onPasswordChanged(password: String) { calls += "password:$password" }
        override fun onPasswordVisibilityClick() { calls += "eye" }
        override fun onImportMore() { calls += "importMore" }
    }

    private val appWallet = WrappedBackupAccountMeta(BackupAccountMeta(name = "App wallet", address = "5App", fileId = "a1"))
    private val webWallet = WrappedBackupAccountMeta(BackupAccountMeta(name = "Web wallet", address = "", fileId = "w1"), BackupOrigin.WEB)

    private fun render(state: ImportRemoteWalletState) {
        compose.setContent { FearlessAppTheme { ImportRemoteWalletContent(state = state, callback = callback) } }
    }

    @Test
    fun listLoadsBackupsLabelsExtensionOnesAndReportsSelection() {
        render(RemoteWalletListState(wallets = listOf(appWallet, webWallet)))

        compose.onNodeWithText(string(R.string.import_remote_wallet_title_wallets)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.google_backup_origin_extension)).assertIsDisplayed()
        compose.onNodeWithText("App wallet").tap()
        compose.onNodeWithText("Web wallet").performTouchInput { longClick() }
        compose.onNodeWithText(string(R.string.import_remote_wallet_btn_create_wallet)).tap()
        compose.waitForIdle()

        assertEquals(listOf("load", "select:App wallet", "long:Web wallet", "create"), calls)
    }

    @Test
    fun loadingListShowsNeitherWalletsNorEmptyMessage() {
        compose.mainClock.autoAdvance = false
        render(RemoteWalletListState(wallets = null))

        compose.onNodeWithText(string(R.string.google_backup_none_found_title)).assertDoesNotExist()
        compose.onNodeWithText(string(R.string.import_remote_wallet_btn_create_wallet)).assertIsDisplayed()
        assertEquals(listOf("load"), calls)
    }

    @Test
    fun emptyAccountOffersAnotherGoogleAccountOrAnotherWay() {
        render(RemoteWalletListState(wallets = emptyList()))

        compose.onNodeWithText(string(R.string.google_backup_none_found_title)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.google_backup_use_another_account)).tap()
        compose.onNodeWithText(string(R.string.google_backup_import_another_way)).tap()
        compose.onAllNodes(iconClickables())[0].tap()
        compose.waitForIdle()

        assertEquals(listOf("load", "anotherAccount", "anotherWay", "back"), calls)
    }

    @Test
    fun errorWithActionOffersRetryAndAnotherWay() {
        render(RemoteWalletListState(error = RemoteWalletListError("Drive unavailable", "Check the network", "Try again")))

        compose.onNodeWithText("Drive unavailable").assertIsDisplayed()
        compose.onNodeWithText("Check the network").assertIsDisplayed()
        compose.onNodeWithText("Try again").tap()
        compose.onNodeWithText(string(R.string.google_backup_import_another_way)).tap()
        compose.waitForIdle()

        assertEquals(listOf("load", "errorAction", "anotherWay"), calls)
    }

    @Test
    fun errorWithoutActionOnlyOffersAnotherWay() {
        render(RemoteWalletListState(error = RemoteWalletListError("Access denied", "Drive access was refused", null)))

        compose.onNodeWithText("Access denied").assertIsDisplayed()
        compose.onNodeWithText("Try again").assertDoesNotExist()
        compose.onNodeWithText(string(R.string.google_backup_import_another_way)).assertIsDisplayed()
    }

    @Test
    fun passwordScreenNamesTheWalletAndForwardsInput() {
        render(
            EnterBackupPasswordState(
                wallet = webWallet,
                passwordInputViewState = TextInputViewState(text = "", hint = "Password", endIcon = R.drawable.ic_eye_disabled),
                isLoading = false
            )
        )

        compose.onNodeWithText(string(R.string.import_remote_wallet_title_password)).assertIsDisplayed()
        compose.onNodeWithText("Web wallet").assertIsDisplayed()
        compose.onNodeWithText(string(R.string.google_backup_origin_extension)).assertIsDisplayed()
        compose.onNode(hasSetTextAction()).performTextInput("hunter22")
        compose.onNodeWithText(string(R.string.common_continue)).tap()
        val icons = compose.onAllNodes(iconClickables())
        icons[0].tap()
        icons[1].tap()
        compose.waitForIdle()

        assertTrue(calls.contains("password:hunter22"))
        assertEquals(listOf("continue", "back", "eye"), calls.filterNot { it.startsWith("password:") })
    }

    @Test
    fun importedScreenContinuesOrImportsMore() {
        render(WalletImportedState(wallet = appWallet))

        compose.onNodeWithText(string(R.string.import_remote_wallet_success_imported)).assertIsDisplayed()
        compose.onNodeWithText("App wallet").assertIsDisplayed()
        compose.onNodeWithText(string(R.string.common_continue)).tap()
        compose.onNodeWithText(string(R.string.import_remote_wallet_btn_import_more)).tap()
        compose.waitForIdle()

        assertEquals(listOf("continue", "importMore"), calls)
    }

    @Test
    fun compactWalletStatesCarryOnlyTheTitleAndSubtitle() {
        val wallet = CompactWalletItemViewState("Imported")
        assertEquals("Imported", wallet.title)
        assertNull(wallet.balance)
        assertFalse(wallet.isSelected)
    }

    @Test
    fun previewsRenderEachStep() {
        compose.setContent {
            FearlessAppTheme {
                PreviewImportRemoteWalletContent()
                PrivateComposable(
                    "jp.co.soramitsu.account.impl.presentation.importing.remote_backup.screens.WalletImportedScreenKt",
                    "PreviewWalletImportedScreen"
                )
                PrivateComposable(
                    "jp.co.soramitsu.account.impl.presentation.importing.remote_backup.screens.EnterBackupPasswordScreenKt",
                    "PreviewEnterBackupPasswordScreen"
                )
                PrivateComposable(
                    "jp.co.soramitsu.account.impl.presentation.importing.remote_backup.screens.RemoteWalletListScreenKt",
                    "PreviewRemoteWalletListScreen"
                )
            }
        }

        compose.onNodeWithText("Wallet name_1").assertExists()
        compose.onNodeWithText("Name").assertExists()
        compose.onNodeWithText(string(R.string.google_backup_none_found_title)).assertExists()
    }
}
