package jp.co.soramitsu.account.impl.presentation.recovery

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import jp.co.soramitsu.account.impl.domain.recovery.ArchivedWalletRecovery
import jp.co.soramitsu.account.impl.testutil.string
import jp.co.soramitsu.account.impl.testutil.tap
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferenceSnapshot
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretScope
import jp.co.soramitsu.feature_account_impl.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h3000dp")
class ArchivedWalletRecoveryScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()

    private val readable = ArchivedWalletRecovery.Record(
        "wallet_secret_archive:v1:7:ETHEREUM_SECRETS",
        listOf(
            ArchivedWalletRecovery.Address(WalletSecretScope.Ethereum, "0xpublic"),
            ArchivedWalletRecovery.Address(WalletSecretScope.ChainAccount("cd".repeat(32)), "5Network")
        ),
        unprovedParts = 2,
        readable = true,
        snapshot = EncryptedPreferenceSnapshot.unencrypted("public fixture data")
    )
    private val unreadable = ArchivedWalletRecovery.Record(
        "wallet_secret_archive:v1:8:TON_SECRETS",
        listOf(
            ArchivedWalletRecovery.Address(WalletSecretScope.Ton, "UQton"),
            ArchivedWalletRecovery.Address(WalletSecretScope.Substrate, "5Substrate")
        ),
        unprovedParts = 0,
        readable = false,
        snapshot = null
    )

    private fun render(state: ArchivedWalletRecoveryViewModel.State) {
        compose.setContent {
            FearlessAppTheme {
                ArchivedWalletRecoveryScreen(
                    state = state,
                    back = { calls += "back" },
                    retry = { calls += "retry" },
                    unlock = { calls += "unlock" },
                    restore = { record, scope -> calls += "restore:${record.key}:$scope" },
                    purge = { calls += "purge:${it.key}" },
                    confirm = { calls += "confirm" },
                    cancel = { calls += "cancel" },
                    export = { calls += "export:${it.key}" },
                    closeExport = { calls += "closeExport" },
                    copySecret = { calls += "copy:$it" }
                )
            }
        }
    }

    @Test
    fun recordsShowTheirAddressesAndOfferRestoreExportAndDelete() {
        render(ArchivedWalletRecoveryViewModel.State(records = listOf(readable, unreadable), busy = false))

        compose.onNodeWithText(readable.key).assertIsDisplayed()
        compose.onNodeWithText("0xpublic").assertIsDisplayed()
        compose.onNodeWithText("5Network").assertIsDisplayed()
        compose.onNodeWithText("UQton").assertIsDisplayed()
        compose.onNodeWithText(string(R.string.wallet_emergency_kind_network)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.wallet_emergency_kind_ton)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.wallet_emergency_kind_substrate)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.wallet_archive_unproved)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.wallet_archive_unreadable)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.wallet_archive_empty)).assertDoesNotExist()

        compose.onAllNodesWithText(string(R.string.wallet_recovery_restore))[0].tap()
        compose.onNodeWithText(string(R.string.wallet_archive_exact_data)).tap()
        compose.onNodeWithText(string(R.string.wallet_archive_delete)).tap()
        compose.onNodeWithText(string(R.string.ux_back)).tap()
        compose.waitForIdle()

        assertEquals(
            listOf("restore:${readable.key}:${WalletSecretScope.Ethereum}", "export:${readable.key}", "purge:${readable.key}", "back"),
            calls
        )
    }

    @Test
    fun lockedArchiveAsksToUnlock() {
        render(ArchivedWalletRecoveryViewModel.State(busy = false, locked = true))

        compose.onNodeWithText(string(R.string.wallet_archive_unlock)).tap()
        compose.waitForIdle()

        compose.onNodeWithText(string(R.string.wallet_archive_empty)).assertDoesNotExist()
        assertEquals(listOf("unlock"), calls)
    }

    @Test
    fun busyArchiveShowsNeitherRecordsNorEmptyText() {
        render(ArchivedWalletRecoveryViewModel.State(records = listOf(readable), busy = true))

        compose.onNodeWithText(readable.key).assertDoesNotExist()
        compose.onNodeWithText(string(R.string.wallet_archive_empty)).assertDoesNotExist()
    }

    @Test
    fun failedReadOffersRetryAndEmptyArchiveSaysSo() {
        render(ArchivedWalletRecoveryViewModel.State(busy = false, failed = true))
        compose.onNodeWithText(string(R.string.wallet_recovery_restore_failed)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.wallet_archive_empty)).assertDoesNotExist()
        compose.onNodeWithText(string(R.string.common_try_again)).tap()
        compose.waitForIdle()
        assertEquals(listOf("retry"), calls)
    }

    @Test
    fun emptyArchiveSaysThereIsNothingRetained() {
        render(ArchivedWalletRecoveryViewModel.State(busy = false))

        compose.onNodeWithText(string(R.string.wallet_archive_empty)).assertIsDisplayed()
    }

    @Test
    fun restoreConfirmationListsOnlyTheChosenAddress() {
        render(
            ArchivedWalletRecoveryViewModel.State(
                records = listOf(readable),
                busy = false,
                confirmation = ArchivedWalletRecoveryViewModel.Confirmation(readable, WalletSecretScope.Ethereum)
            )
        )

        compose.onNodeWithText(string(R.string.wallet_archive_restore_confirm)).assertIsDisplayed()
        compose.onAllNodesWithText("0xpublic").assertCountEquals(2)
        compose.onAllNodesWithText("5Network").assertCountEquals(1)
        compose.onAllNodesWithText(string(R.string.wallet_recovery_restore)).let { it[it.fetchSemanticsNodes().size - 1].tap() }
        compose.onNodeWithText(string(R.string.common_cancel)).tap()
        compose.waitForIdle()

        assertEquals(listOf("confirm", "cancel"), calls)
    }

    @Test
    fun deleteConfirmationTakesTwoSteps() {
        render(
            ArchivedWalletRecoveryViewModel.State(
                records = listOf(readable),
                busy = false,
                confirmation = ArchivedWalletRecoveryViewModel.Confirmation(readable, purgeStep = 1)
            )
        )
        compose.onNodeWithText(string(R.string.wallet_archive_delete_first)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.wallet_archive_continue_delete)).tap()
        compose.waitForIdle()
        assertEquals(listOf("confirm"), calls)
    }

    @Test
    fun finalDeleteStepWarnsBeforeDeleting() {
        render(
            ArchivedWalletRecoveryViewModel.State(
                records = listOf(readable),
                busy = false,
                confirmation = ArchivedWalletRecoveryViewModel.Confirmation(readable, purgeStep = 2)
            )
        )
        compose.onNodeWithText(string(R.string.wallet_archive_delete_final)).assertIsDisplayed()
        compose.onAllNodesWithText(string(R.string.wallet_archive_delete)).assertCountEquals(3)
    }

    @Test
    fun exactDataDialogCopiesOrCloses() {
        render(ArchivedWalletRecoveryViewModel.State(busy = false, export = "exact archived bytes"))

        compose.onNodeWithText(string(R.string.wallet_archive_export_warning)).assertIsDisplayed()
        compose.onNodeWithText("exact archived bytes").assertIsDisplayed()
        compose.onNodeWithText(string(R.string.common_copy)).tap()
        compose.onNodeWithText(string(R.string.common_close)).tap()
        compose.waitForIdle()

        assertEquals(listOf("copy:exact archived bytes", "closeExport"), calls)
    }
}
