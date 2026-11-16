package jp.co.soramitsu.wallet.impl.presentation.contacts

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
class ContactsContentRobolectricTest {

    @Before
    fun prepareUiTest() {
        ComposeFixtures.prepareUiTest()
    }

    @get:Rule
    val compose = createWalletComposeRule()

    private val calls = mutableListOf<String>()
    private val callback = object : ContactsScreenInterface {
        override fun onContactClick(contact: Contact) { calls += "contact:${contact.name}" }
        override fun onNavigationClick() { calls += "back" }
        override fun onCreateContactClick(chainId: String?, address: String?) { calls += "create:$chainId:$address" }
    }

    @Before
    fun setUp() {
        ComposeFixtures.passDoubleTapGuard()
    }

    @Test
    fun contactsAreGroupedAndUnsavedOnesOfferSaving() {
        val bob = Contact("Bob", "5FHneW46xGXgs5mUiveU4sbTyGBzmstUspZC92UhjJM694ty", "", "kusama", isSavedToContacts = true)
        val unnamed = Contact("", "5GrwvaEF5zXb26Fz9rcQpDWS57CtERHpNehXCPcNoHGKutQY", "", "kusama", isSavedToContacts = false)
        render(LoadingState.Loaded(ContactsViewState(mapOf("B" to listOf(bob), null to listOf(unnamed)))))

        compose.onNodeWithText(string(R.string.common_contacts)).assertIsDisplayed()
        compose.onNodeWithText("B").assertIsDisplayed()
        compose.onNodeWithText("Bob").performClick()
        val chips = compose.onAllNodes(hasClickAction() and !hasText("Bob") and !hasText(string(R.string.common_unknown)) and
            !hasText(string(R.string.create_contact)) and !hasText("", substring = true))
        chips[chips.fetchSemanticsNodes().size - 1].performClick()
        compose.waitForIdle()

        assertTrue(calls.toString(), calls.containsAll(listOf("contact:Bob", "create:kusama:${unnamed.address}")))
    }

    @Test
    fun emptyBookOffersToCreateAContact() {
        render(LoadingState.Loaded(ContactsViewState.default))

        compose.onNodeWithText(string(R.string.contacts_empty_message)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.create_contact)).performClick()
        compose.waitForIdle()

        assertEquals(listOf("create:null:null"), calls)
    }

    @Test
    fun loadingShowsNoContacts() {
        render(LoadingState.Loading())

        assertEquals(0, compose.onAllNodes(hasText(string(R.string.contacts_empty_message))).fetchSemanticsNodes().size)
    }

    private fun render(state: LoadingState<ContactsViewState>) {
        compose.setContent {
            FearlessAppTheme {
                ContactsContent(state = state, callback = callback)
            }
        }
        compose.waitForIdle()
    }
}
