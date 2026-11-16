package jp.co.soramitsu.wallet.impl.presentation.history

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import jp.co.soramitsu.wallet.impl.presentation.createWalletComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import jp.co.soramitsu.common.compose.theme.FearlessAppTheme
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.wallet.impl.presentation.ComposeFixtures
import jp.co.soramitsu.wallet.impl.presentation.ComposeFixtures.string
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w420dp-h1600dp")
class AddressHistoryContentRobolectricTest {

    @Before
    fun prepareUiTest() {
        ComposeFixtures.prepareUiTest()
    }

    @get:Rule
    val compose = createWalletComposeRule()

    private val calls = mutableListOf<String>()
    private val callback = object : AddressHistoryScreenInterface {
        override fun onAddressClick(address: Address) { calls += "address:${address.name}" }
        override fun onNavigationClick() { calls += "back" }
        override fun onCreateContactClick(chainId: String?, address: String?) { calls += "create:$chainId:$address" }
    }

    @Before
    fun setUp() {
        ComposeFixtures.passDoubleTapGuard()
    }

    @Test
    fun recentAndSavedAddressesAreListedByGroup() {
        val recent = Address("", "5GrwvaEF5zXb26Fz9rcQpDWS57CtERHpNehXCPcNoHGKutQY", "", "polkadot", isSavedToContacts = false, score = 80)
        val saved = Address("Alice", "5FHneW46xGXgs5mUiveU4sbTyGBzmstUspZC92UhjJM694ty", "", "polkadot", isSavedToContacts = true, score = null)
        render(LoadingState.Loaded(AddressHistoryViewState(setOf(recent), mapOf("A" to listOf(saved)))))

        compose.onNodeWithText(string(R.string.common_history)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.recent)).assertIsDisplayed()
        compose.onNodeWithText("A").assertIsDisplayed()
        compose.onNodeWithText(string(R.string.common_unknown)).performClick()
        compose.onNodeWithText("Alice").performClick()
        // The only "add contact" chip belongs to the unsaved address.
        val chips = compose.onAllNodes(hasClickAction() and !hasText("Alice") and !hasText(string(R.string.common_unknown)) and
            !hasText(string(R.string.create_contact)))
        chips[chips.fetchSemanticsNodes().size - 1].performClick()
        compose.waitForIdle()

        assertTrue(calls.toString(), calls.containsAll(listOf("address:", "address:Alice", "create:polkadot:${recent.address}")))
    }

    @Test
    fun emptyHistorySaysSoAndStillOffersACreateContact() {
        render(LoadingState.Loaded(AddressHistoryViewState.default))

        compose.onNodeWithText(string(R.string.address_history_empty_message)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.create_contact)).performClick()
        compose.waitForIdle()

        assertEquals(listOf("create:null:null"), calls)
    }

    @Test
    fun loadingShowsNeitherListNorEmptyMessage() {
        render(LoadingState.Loading())

        assertEquals(0, compose.onAllNodes(hasText(string(R.string.address_history_empty_message))).fetchSemanticsNodes().size)
        assertTrue(AddressHistoryViewState.default.isEmpty)
    }

    private fun render(state: LoadingState<AddressHistoryViewState>) {
        compose.setContent {
            FearlessAppTheme {
                AddressHistoryContent(state = state, callback = callback)
            }
        }
        compose.waitForIdle()
    }
}
