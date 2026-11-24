package jp.co.soramitsu.account.impl.presentation.account.details

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import jp.co.soramitsu.account.impl.presentation.account.model.ConnectedAccountsInfoItem
import jp.co.soramitsu.account.impl.testutil.PrivateComposable
import jp.co.soramitsu.account.impl.testutil.iconClickables
import jp.co.soramitsu.account.impl.testutil.string
import jp.co.soramitsu.account.impl.testutil.tap
import jp.co.soramitsu.common.compose.component.WalletItemViewState
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.model.WalletEcosystem
import jp.co.soramitsu.feature_account_impl.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h1200dp")
class AccountDetailsContentTest {

    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()
    private val callback = object : AccountDetailsCallback {
        override fun onBackClick() { calls += "back" }
        override fun accountsItemOptionsClicked(type: WalletEcosystem) { calls += "accounts:$type" }
        override fun walletOptionsClicked(item: WalletItemViewState) { calls += "wallet:${item.id}" }
    }

    private val wallet = WalletItemViewState(
        id = 42,
        title = "Savings",
        walletIcon = jp.co.soramitsu.common.R.drawable.ic_wallet,
        isSelected = false
    )

    @Test
    fun listsConnectedAccountsAndReportsTheirOptions() {
        val state = AccountDetailsState(
            walletItem = wallet,
            connectedAccountsInfo = listOf(
                ConnectedAccountsInfoItem(WalletEcosystem.Substrate, "Substrate chain accounts", 12),
                ConnectedAccountsInfoItem(WalletEcosystem.Ethereum, "EVM chain accounts", 0)
            )
        )
        compose.setContent { FearlessAppTheme { AccountDetailsContent(state = state, callback = callback) } }

        compose.onNodeWithText("Savings").assertIsDisplayed()
        compose.onNodeWithText("Connected accounts").assertIsDisplayed()
        compose.onNodeWithText("12").assertIsDisplayed()
        // An ecosystem without accounts shows a warning instead of a count.
        compose.onNodeWithText("0").assertDoesNotExist()

        compose.onNodeWithText("Substrate chain accounts").tap()
        compose.onNodeWithText("EVM chain accounts").tap()
        compose.onNodeWithContentDescription(string(R.string.ux_more)).tap()
        compose.onAllNodes(iconClickables())[0].tap()
        compose.waitForIdle()

        assertEquals(listOf("accounts:Substrate", "accounts:Ethereum", "wallet:42", "back"), calls)
    }

    @Test
    fun emptyStateShowsOnlyTheConnectedAccountsCard() {
        compose.setContent { FearlessAppTheme { AccountDetailsContent(state = AccountDetailsState.Empty, callback = callback) } }

        compose.onNodeWithText("Connected accounts").assertIsDisplayed()
        compose.onNodeWithContentDescription(string(R.string.ux_more)).assertDoesNotExist()
    }

    @Test
    fun previewShowsBothEcosystems() {
        compose.setContent {
            PrivateComposable(
                "jp.co.soramitsu.account.impl.presentation.account.details.AccountDetailsContentKt",
                "PreviewAccountDetailsContent"
            )
        }

        compose.onNodeWithText("Substrate chain accounts").assertIsDisplayed()
        compose.onNodeWithText("33").assertIsDisplayed()
    }
}
