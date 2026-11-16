package jp.co.soramitsu.account.impl.presentation.recovery

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import jp.co.soramitsu.account.api.domain.model.ImportMode
import jp.co.soramitsu.account.impl.domain.recovery.WalletRecoveryInventory
import jp.co.soramitsu.account.impl.testutil.string
import jp.co.soramitsu.account.impl.testutil.tap
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.data.storage.encrypt.WalletRecoveryKind
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretScope
import jp.co.soramitsu.feature_account_impl.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h4000dp")
class RecoveryCenterScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()

    private val chainAccount = WalletSecretScope.ChainAccount("ab".repeat(20))

    private fun render(state: WalletRecoveryInventory.State, pinUnreadable: Boolean = false) {
        compose.setContent {
            FearlessAppTheme {
                RecoveryCenterScreen(
                    state = state,
                    back = { calls += "back" },
                    restore = { target, mode -> calls += "restore:${target.scopeId}:$mode" },
                    importCorrected = { calls += "corrected:${it.scopeId}" },
                    exportStored = { calls += "export" },
                    backupWallet = { id, chain -> calls += "backup:$id:$chain" },
                    archivedData = { calls += "archive" },
                    pinUnreadable = pinUnreadable,
                    resetPin = { calls += "resetPin" },
                    previousSecureStorage = { calls += "previousStorage" },
                    savedEndpoints = { calls += "endpoints" }
                )
            }
        }
    }

    @Test
    fun walletIssuesOfferTheMatchingRecoveryActions() {
        val wallet = WalletRecoveryInventory.Wallet(
            id = 7,
            name = "Family wallet",
            items = listOf(
                WalletRecoveryInventory.Item(RecoveryTarget.from(7, WalletSecretScope.Ethereum), WalletRecoveryKind.IDENTITY_MISMATCH),
                WalletRecoveryInventory.Item(RecoveryTarget.from(7, WalletSecretScope.Ton), WalletRecoveryKind.MISSING_KEY),
                WalletRecoveryInventory.Item(RecoveryTarget.from(7, chainAccount), WalletRecoveryKind.QUARANTINED)
            ),
            advisories = listOf(
                WalletRecoveryInventory.Advisory(WalletSecretScope.Substrate, asTyped = true),
                WalletRecoveryInventory.Advisory(WalletSecretScope.Ethereum, asTyped = false, chainId = "chain-1")
            ),
            readFailed = true
        )
        render(WalletRecoveryInventory.State(listOf(wallet), archivedCount = 0))

        compose.onNodeWithText("Family wallet").assertIsDisplayed()
        compose.onNodeWithText(string(R.string.wallet_recovery_inventory_failed)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.wallet_recovery_identity_issue)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.wallet_recovery_missing_key)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.wallet_recovery_unusable_key)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.wallet_recovery_as_typed)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.wallet_recovery_backup_advisory)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.wallet_emergency_kind_network) + "\n0x" + "ab".repeat(20)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.wallet_recovery_no_attention)).assertDoesNotExist()

        compose.onNodeWithText(string(R.string.wallet_recovery_import_corrected)).tap()
        // The TON key is restored from a phrase only; the network key also offers seed and JSON.
        val phrase = compose.onAllNodesWithText(string(R.string.wallet_recovery_use_phrase))
        phrase[0].tap()
        phrase[1].tap()
        compose.onNodeWithText(string(R.string.wallet_recovery_use_seed)).tap()
        compose.onNodeWithText(string(R.string.wallet_recovery_use_json)).tap()
        val backups = compose.onAllNodesWithText(string(R.string.wallet_recovery_backup_key))
        backups[0].tap()
        backups[1].tap()
        compose.waitForIdle()

        val chain = "chain:" + "ab".repeat(20)
        assertEquals(
            listOf(
                "corrected:ethereum",
                "restore:ton:${ImportMode.MnemonicPhrase}",
                "restore:$chain:${ImportMode.MnemonicPhrase}",
                "restore:$chain:${ImportMode.RawSeed}",
                "restore:$chain:${ImportMode.Json}",
                "backup:7:null",
                "backup:7:chain-1"
            ),
            calls
        )
    }

    @Test
    fun nothingToFixSaysSoAndKeepsTheRetainedDataActions() {
        render(
            WalletRecoveryInventory.State(
                listOf(WalletRecoveryInventory.Wallet(1, "Quiet wallet", emptyList(), emptyList(), readFailed = false)),
                archivedCount = 0
            )
        )

        compose.onNodeWithText(string(R.string.wallet_recovery_no_attention)).assertIsDisplayed()
        compose.onNodeWithText("Quiet wallet").assertDoesNotExist()

        compose.onNodeWithText(string(R.string.wallet_keyloss_retry)).tap()
        compose.onNodeWithText(string(R.string.saved_endpoints_title)).tap()
        compose.onNodeWithText(string(R.string.wallet_archive_open)).tap()
        compose.onNodeWithText(string(R.string.wallet_emergency_open)).tap()
        compose.onNodeWithText(string(R.string.wallet_recovery_keep_view_only)).tap()
        compose.onNodeWithText(string(R.string.wallet_recovery_back)).tap()
        compose.waitForIdle()

        assertEquals(listOf("previousStorage", "endpoints", "archive", "export", "back", "back"), calls)
    }

    @Test
    fun unreadablePinOffersAResetAndAFailedReadIsReported() {
        render(WalletRecoveryInventory.State(emptyList(), archivedCount = null, readFailed = true), pinUnreadable = true)

        compose.onNodeWithText(string(R.string.pin_recovery_view_only_intro)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.wallet_recovery_inventory_failed)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.wallet_recovery_no_attention)).assertDoesNotExist()
        compose.onNodeWithText(string(R.string.pin_recovery_reset)).tap()
        compose.waitForIdle()

        assertEquals(listOf("resetPin"), calls)
    }

    @Test
    fun loadingInventoryDoesNotClaimThereIsNothingToFix() {
        render(WalletRecoveryInventory.State(emptyList(), archivedCount = null, loading = true))

        compose.onNodeWithText(string(R.string.wallet_recovery_center)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.wallet_recovery_no_attention)).assertDoesNotExist()
    }
}
