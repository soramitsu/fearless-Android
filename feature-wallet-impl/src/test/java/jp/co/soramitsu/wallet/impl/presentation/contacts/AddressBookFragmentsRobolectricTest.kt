package jp.co.soramitsu.wallet.impl.presentation.contacts

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.android.material.bottomsheet.BottomSheetDialog
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.common.compose.component.SelectorState
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.wallet.impl.presentation.ComposeFixtures
import jp.co.soramitsu.wallet.impl.presentation.ComposeFixtures.string
import jp.co.soramitsu.wallet.impl.presentation.HiltFragmentHost
import jp.co.soramitsu.wallet.impl.presentation.StubHiltActivity
import jp.co.soramitsu.wallet.impl.presentation.StubHiltApplication
import jp.co.soramitsu.wallet.impl.presentation.addressbook.CreateContactFragment
import jp.co.soramitsu.wallet.impl.presentation.addressbook.CreateContactViewModel
import jp.co.soramitsu.wallet.impl.presentation.addressbook.CreateContactViewState
import jp.co.soramitsu.wallet.impl.presentation.history.AddressHistoryFragment
import jp.co.soramitsu.wallet.impl.presentation.history.AddressHistoryViewModel
import jp.co.soramitsu.wallet.impl.presentation.history.AddressHistoryViewState
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/** The address history, contacts and create-contact sheets render their book and forward the user's choices. */
@RunWith(RobolectricTestRunner::class)
@Config(application = StubHiltApplication::class, qualifiers = "w420dp-h1600dp")
class AddressBookFragmentsRobolectricTest {

    @get:Rule
    val compose = createEmptyComposeRule().apply { mainClock.autoAdvance = false }

    private var host: ActivityController<StubHiltActivity>? = null

    @Before
    fun prepareUiTest() {
        ComposeFixtures.prepareUiTest()
    }

    @After
    fun closeHost() {
        HiltFragmentHost.close(host)
    }

    @Test
    fun emptyHistoryOffersToCreateAContactAndCancellingReportsTheClose() {
        val viewModel = mockk<AddressHistoryViewModel>(relaxed = true) {
            every { state } returns MutableStateFlow<LoadingState<AddressHistoryViewState>>(LoadingState.Loaded(AddressHistoryViewState.default))
            every { isSoftKeyboardOpenFlow } returns MutableStateFlow(false)
        }
        val fragment = AddressHistoryFragment()
        host = HiltFragmentHost.host(fragment, mapOf(AddressHistoryViewModel::class.java to viewModel))
        settle()

        compose.onNodeWithText(string(R.string.address_history_empty_message)).assertExists()
        ComposeFixtures.passDoubleTapGuard()
        compose.onNodeWithText(string(R.string.create_contact)).performClick()
        compose.waitForIdle()
        verify { viewModel.onCreateContactClick(null, null) }
        val dialog = fragment.requireDialog() as BottomSheetDialog
        assertTrue(dialog.behavior.skipCollapsed)

        dialog.cancel()
        ShadowLooper.idleMainLooper()

        verify { viewModel.onScreenClosed() }
        assertEquals("polkadot", AddressHistoryFragment.getBundle("polkadot").getString(AddressHistoryFragment.KEY_PAYLOAD))
    }

    @Test
    fun contactsAreListedAndAPickedContactReachesTheViewModel() {
        val bob = Contact("Bob", "5FHneW46xGXgs5mUiveU4sbTyGBzmstUspZC92UhjJM694ty", "", "kusama", isSavedToContacts = true)
        val viewModel = mockk<ContactsViewModel>(relaxed = true) {
            every { state } returns MutableStateFlow<LoadingState<ContactsViewState>>(
                LoadingState.Loaded(ContactsViewState(mapOf("B" to listOf(bob))))
            )
            every { isSoftKeyboardOpenFlow } returns MutableStateFlow(false)
        }
        val fragment = ContactsFragment()
        host = HiltFragmentHost.host(fragment, mapOf(ContactsViewModel::class.java to viewModel))
        settle()

        compose.onNodeWithText(string(R.string.common_contacts)).assertExists()
        ComposeFixtures.passDoubleTapGuard()
        compose.onNodeWithText("Bob").performClick()
        compose.waitForIdle()

        verify { viewModel.onContactClick(bob) }
        assertTrue((fragment.requireDialog() as BottomSheetDialog).behavior.skipCollapsed)
        assertEquals("kusama", ContactsFragment.getBundle("kusama").getString(ContactsFragment.KEY_PAYLOAD))
    }

    @Test
    fun createContactFormForwardsTheNetworkChoiceAndTheCreation() {
        val viewModel = mockk<CreateContactViewModel>(relaxed = true) {
            every { state } returns MutableStateFlow(
                CreateContactViewState(SelectorState("Network", "Kusama", null), "Bob", "5FHneW46xGXgs5mUiveU4sbTyGBzmstUspZC92UhjJM694ty", true)
            )
            every { isSoftKeyboardOpenFlow } returns MutableStateFlow(false)
        }
        val fragment = CreateContactFragment()
        host = HiltFragmentHost.host(fragment, mapOf(CreateContactViewModel::class.java to viewModel))
        settle()

        ComposeFixtures.passDoubleTapGuard()
        compose.onNodeWithText("Kusama").performClick()
        ComposeFixtures.passDoubleTapGuard()
        compose.onNode(hasText(string(R.string.create_contact)) and hasClickAction()).performClick()
        compose.waitForIdle()

        verify { viewModel.onChainClick() }
        verify { viewModel.onCreateContactClick() }
        assertTrue((fragment.requireDialog() as BottomSheetDialog).behavior.skipCollapsed)
    }

    @Test
    fun createContactBundleCarriesTheNetworkAndAddress() {
        val filled = CreateContactFragment.getBundle("kusama", "5FHneW")
        val empty = CreateContactFragment.getBundle(null, null)

        assertEquals("kusama", filled.getString(CreateContactFragment.KEY_CHAIN_ID))
        assertEquals("5FHneW", filled.getString(CreateContactFragment.KEY_PAYLOAD))
        assertTrue(empty.containsKey(CreateContactFragment.KEY_CHAIN_ID))
        assertNull(empty.getString(CreateContactFragment.KEY_CHAIN_ID))
        assertNull(empty.getString(CreateContactFragment.KEY_PAYLOAD))
    }

    private fun settle() {
        ShadowLooper.idleMainLooper()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }
}
