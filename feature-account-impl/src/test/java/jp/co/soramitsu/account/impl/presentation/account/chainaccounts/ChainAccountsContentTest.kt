package jp.co.soramitsu.account.impl.presentation.account.chainaccounts

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import jp.co.soramitsu.account.impl.testutil.PrivateComposable
import jp.co.soramitsu.account.impl.testutil.iconClickables
import jp.co.soramitsu.account.impl.testutil.string
import jp.co.soramitsu.account.impl.testutil.tap
import jp.co.soramitsu.common.compose.component.WalletItemViewState
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.list.headers.TextHeader
import jp.co.soramitsu.feature_account_impl.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h1400dp")
class ChainAccountsContentTest {

    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()
    private val callback = object : ChainAccountsCallback {
        override fun onBackClick() { calls += "back" }
        override fun chainAccountOptionsClicked(item: AccountInChainUi) { calls += "options:${item.chainId}" }
        override fun onSearchInput(input: String) { calls += "search:$input" }
    }

    private fun account(chainId: String, name: String, address: String, hasAccount: Boolean) = AccountInChainUi(
        chainId = chainId,
        chainName = name,
        chainIcon = "",
        address = address,
        accountIcon = null,
        accountName = null,
        accountFrom = null,
        hasAccount = hasAccount,
        markedAsNotNeed = false
    )

    @Test
    fun groupsAccountsUnderHeadersAndOpensTheirOptions() {
        val state = ChainAccountsState(
            walletItem = WalletItemViewState(id = 3, title = "Wallet three", walletIcon = jp.co.soramitsu.common.R.drawable.ic_wallet, isSelected = false),
            chainProjections = listOf(
                TextHeader("Shared secret"),
                account("polkadot", "Polkadot", "15oF4uVJwmo4TdGW7VfQxNLavjCXviqxT9S1MgbjMNHr6Sp5", hasAccount = true),
                TextHeader("Missing accounts"),
                account("kusama", "Kusama", "", hasAccount = false)
            )
        )
        compose.setContent { FearlessAppTheme { ChainAccountsContent(state = state, callback = callback) } }

        compose.onNodeWithText("Wallet three").assertIsDisplayed()
        compose.onNodeWithText("Shared secret", ignoreCase = true).assertIsDisplayed()
        compose.onNodeWithText("Missing accounts", ignoreCase = true).assertIsDisplayed()
        compose.onNodeWithText("Polkadot").tap()
        compose.onNodeWithText("Kusama").tap()
        compose.onNode(hasSetTextAction()).performTextInput("pol")
        compose.onAllNodes(iconClickables())[0].tap()
        compose.waitForIdle()

        assertEquals(listOf("options:polkadot", "options:kusama"), calls.filter { it.startsWith("options:") })
        assertTrue(calls.any { it.startsWith("search:") && it.endsWith("pol") })
        assertEquals("back", calls.last())
    }

    @Test
    fun searchWithoutMatchesSaysNoAccountsWereFound() {
        compose.setContent {
            FearlessAppTheme { ChainAccountsContent(state = ChainAccountsState(null, emptyList(), searchQuery = "xyz"), callback = callback) }
        }

        compose.onNodeWithText(string(R.string.accounts_not_found)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.common_search_assets_alert_title)).assertIsDisplayed()
    }

    @Test
    fun emptyListWithoutSearchShowsNoNotFoundMessage() {
        compose.setContent { FearlessAppTheme { ChainAccountsContent(state = ChainAccountsState.Empty, callback = callback) } }

        compose.onNodeWithText(string(R.string.accounts_not_found)).assertDoesNotExist()
    }

    @Test
    fun previewShowsTheSampleChain() {
        compose.setContent {
            PrivateComposable(
                "jp.co.soramitsu.account.impl.presentation.account.chainaccounts.ChainAccountsContentKt",
                "PreviewChainAccountsContent"
            )
        }

        compose.onNodeWithText("Chain name").assertIsDisplayed()
    }
}
