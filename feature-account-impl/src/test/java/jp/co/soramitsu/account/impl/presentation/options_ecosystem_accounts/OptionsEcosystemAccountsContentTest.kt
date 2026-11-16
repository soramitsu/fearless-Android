package jp.co.soramitsu.account.impl.presentation.options_ecosystem_accounts

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
import jp.co.soramitsu.common.model.WalletEcosystem

@RunWith(RobolectricTestRunner::class)
class OptionsEcosystemAccountsContentTest {

    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()

    private fun render(hasReplacedAccounts: Boolean) {
        compose.setContent {
            FearlessAppTheme {
                OptionsEcosystemAccountsContent(
                    state = OptionsEcosystemAccountsScreenViewState(1, WalletEcosystem.Substrate, hasReplacedAccounts),
                    onBackupEcosystemAccountsClicked = { calls += "backup" },
                    onEcosystemAccountsClicked = { calls += "details" },
                    onBackClicked = { calls += "close" }
                )
            }
        }
    }

    @Test
    fun ecosystemWithOwnKeysOffersBackupDetailsAndClose() {
        render(hasReplacedAccounts = false)

        compose.onNodeWithText(string(R.string.ecosystem_options_title)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.ecosystem_options_backup_title)).tap()
        compose.onNodeWithText(string(R.string.ecosystem_options_details_title)).tap()
        compose.onAllNodes(iconClickables())[0].tap()
        compose.waitForIdle()

        assertEquals(listOf("backup", "details", "close"), calls)
    }

    @Test
    fun ecosystemWithReplacedAccountsHidesTheBackup() {
        render(hasReplacedAccounts = true)

        compose.onNodeWithText(string(R.string.ecosystem_options_backup_title)).assertDoesNotExist()
        compose.onNodeWithText(string(R.string.ecosystem_options_details_title)).assertIsDisplayed()
    }

    @Test
    fun previewOffersTheBackup() {
        compose.setContent {
            PrivateComposable(
                "jp.co.soramitsu.account.impl.presentation.options_ecosystem_accounts.OptionsEcosystemAccountsContentKt",
                "OptionsEcosystemAccountsScreenPreview"
            )
        }

        compose.onNodeWithText(string(R.string.ecosystem_options_backup_title)).assertIsDisplayed()
    }
}
