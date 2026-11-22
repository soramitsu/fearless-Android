package jp.co.soramitsu.wallet.impl.presentation.history

import android.graphics.drawable.Drawable
import android.graphics.drawable.PictureDrawable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.coredb.model.AddressBookContact
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.impl.awaitFirst
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.presentation.send.SendSharedState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * The address-history sheet lists the network's contacts (grouped by initial) and its recent
 * recipients. Rows get their address icons asynchronously.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AddressHistoryViewModelCoverageTest {

    @get:Rule
    val mainRule = MainCoroutineRule(UnconfinedTestDispatcher())

    private val placeholder = mockk<Drawable>()
    private val icon = mockk<PictureDrawable>()
    private val addressBook = MutableStateFlow<List<AddressBookContact>>(emptyList())
    private val iconGenerator = mockk<AddressIconGenerator> {
        coEvery { createAddressIcon(any(), any(), any()) } returns icon
        coEvery { createEthereumAddressIcon(any(), any(), any()) } returns icon
    }
    private val resourceManager = mockk<ResourceManager> {
        every { getDrawable(any()) } returns placeholder
    }
    private val viewModels = mutableListOf<AddressHistoryViewModel>()

    @After
    fun tearDown() {
        viewModels.forEach { it.viewModelScope.cancel() }
    }

    @Test
    fun contactsAreGroupedByInitialAndRecentRowsKnowWhichAreContacts() {
        addressBook.value = listOf(
            AddressBookContact(ALICE, "alice", CHAIN_ID),
            AddressBookContact(CHARLIE, null, CHAIN_ID)
        )
        val viewModel = viewModel(ethereum = false, recentAddresses = setOf(ALICE, BOB))

        val data = loaded(viewModel) { it.recentAddresses.size == 2 && it.allRows().all { row -> row.image === icon } }

        assertEquals(setOf("A", null), data.addressBookAddresses.keys)
        assertEquals(listOf(Address("alice", ALICE, icon, CHAIN_ID, true)), data.addressBookAddresses["A"])
        assertEquals(listOf(Address("", CHARLIE, icon, CHAIN_ID, true)), data.addressBookAddresses[null])
        assertEquals(
            setOf(
                Address("alice", ALICE, icon, CHAIN_ID, true),
                Address("", BOB, icon, CHAIN_ID, false)
            ),
            data.recentAddresses
        )
    }

    @Test
    fun ethereumRowsAreListedWithTheirIconsAndContactState() {
        addressBook.value = listOf(AddressBookContact(ETH_A, "alice", CHAIN_ID))
        val viewModel = viewModel(ethereum = true, recentAddresses = setOf(ETH_A, ETH_B))

        val data = loaded(viewModel, ::iconsSettled)

        assertEquals(Address("alice", ETH_A, icon, CHAIN_ID, true), data.addressBookAddresses.getValue("A").single())
        assertEquals(
            mapOf(ETH_A to true, ETH_B to false),
            data.recentAddresses.associate { it.address to it.isSavedToContacts }
        )
    }

    @Test
    fun anUpdatedAddressBookRenamesContactsAndRecentRows() {
        addressBook.value = listOf(AddressBookContact(ETH_A, "alice", CHAIN_ID))
        val viewModel = viewModel(ethereum = true, recentAddresses = setOf(ETH_A, ETH_B))
        loaded(viewModel, ::iconsSettled)

        addressBook.value = listOf(
            AddressBookContact(ETH_A, "Alicia", CHAIN_ID),
            AddressBookContact(ETH_B, "bob", CHAIN_ID)
        )

        val data = loaded(viewModel) {
            it.addressBookAddresses.keys == setOf("A", "B") && it.recentAddresses.all { row -> row.isSavedToContacts }
        }
        assertEquals(Address("Alicia", ETH_A, icon, CHAIN_ID, true), data.addressBookAddresses.getValue("A").single())
        assertEquals(Address("bob", ETH_B, icon, CHAIN_ID, true), data.addressBookAddresses.getValue("B").single())
        assertEquals(
            mapOf(ETH_A to "Alicia", ETH_B to "bob"),
            data.recentAddresses.associate { it.address to it.name }
        )
    }

    private fun iconsSettled(data: AddressHistoryViewState): Boolean =
        data.recentAddresses.size == 2 && data.allRows().all { it.image === icon }

    private fun AddressHistoryViewState.allRows(): List<Address> =
        addressBookAddresses.values.flatten() + recentAddresses

    private fun loaded(
        viewModel: AddressHistoryViewModel,
        predicate: (AddressHistoryViewState) -> Boolean
    ): AddressHistoryViewState {
        val state = viewModel.state.awaitFirst { it is LoadingState.Loaded && predicate(it.data) }
        return (state as LoadingState.Loaded).data
    }

    private fun viewModel(ethereum: Boolean, recentAddresses: Set<String>): AddressHistoryViewModel {
        val chain = mockk<Chain>(relaxed = true) {
            every { id } returns CHAIN_ID
            every { isEthereumChain } returns ethereum
            every { isEthereumBased } returns ethereum
        }
        val walletInteractor = mockk<WalletInteractor> {
            every { observeAddressBook(CHAIN_ID) } returns addressBook
            coEvery { getOperationAddressWithChainId(CHAIN_ID, any()) } returns recentAddresses
        }
        val chainsRepository = mockk<ChainsRepository> {
            coEvery { getChain(CHAIN_ID) } returns chain
        }
        return AddressHistoryViewModel(
            SavedStateHandle(mapOf(AddressHistoryFragment.KEY_PAYLOAD to CHAIN_ID)),
            SendSharedState(),
            walletInteractor,
            chainsRepository,
            resourceManager,
            iconGenerator,
            mockk(relaxed = true)
        ).also(viewModels::add)
    }

    private companion object {
        const val CHAIN_ID = "history-chain"
        const val ALICE = "5GrwvaEF5zXb26Fz9rcQpDWS57CtERHpNehXCPcNoHGKutQY"
        const val BOB = "5FHneW46xGXgs5mUiveU4sbTyGBzmstUspZC92UhjJM694ty"
        const val CHARLIE = "5FLSigC9HGRKVhB9FiEo4Y3koPsNmBmLJbpXg2mp1hXcS59Y"
        const val ETH_A = "0x1111111111111111111111111111111111111111"
        const val ETH_B = "0x2222222222222222222222222222222222222222"
    }
}
