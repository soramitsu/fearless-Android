package jp.co.soramitsu.wallet.impl.presentation.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.wallet.impl.presentation.send.SendSharedState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test

/**
 * The history sheet picks the send screen's recipient through [SendSharedState]. One sheet delivers
 * at most one outcome: a tap that reaches it after a pick or a close must not replace the recipient.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AddressHistoryViewModelTest {

    @get:Rule
    val mainRule = MainCoroutineRule(UnconfinedTestDispatcher())

    private val sharedState = SendSharedState()
    private val router = mockk<WalletRouter>(relaxed = true)
    private val chain = mockk<Chain>(relaxed = true) {
        every { id } returns CHAIN_ID
        every { isEthereumChain } returns false
        every { isEthereumBased } returns false
    }
    private val walletInteractor = mockk<WalletInteractor> {
        every { observeAddressBook(CHAIN_ID) } returns flowOf(emptyList())
        coEvery { getOperationAddressWithChainId(CHAIN_ID, any()) } returns emptySet()
    }
    private val chainsRepository = mockk<ChainsRepository> {
        coEvery { getChain(CHAIN_ID) } returns chain
    }
    private val resourceManager = mockk<ResourceManager> {
        every { getDrawable(any()) } returns mockk()
    }
    private val viewModels = mutableListOf<AddressHistoryViewModel>()

    @After
    fun tearDown() {
        viewModels.forEach { it.viewModelScope.cancel() }
    }

    @Test
    fun shouldKeepTheFirstPick_whenASecondRowIsTappedBeforeTheSheetCloses() {
        val viewModel = viewModel()

        viewModel.onAddressClick(row(ALICE))
        viewModel.onAddressClick(row(BOB))

        assertEquals(ALICE, recipient())
        verify(exactly = 1) { router.backWithResult(AddressHistoryFragment.RESULT_ADDRESS to ALICE) }
        verify(exactly = 0) { router.backWithResult(AddressHistoryFragment.RESULT_ADDRESS to BOB) }
    }

    @Test
    fun shouldKeepTheCurrentRecipient_whenATapArrivesAfterTheSheetWasClosed() {
        sharedState.updateAddress(BOB)
        val viewModel = viewModel()

        viewModel.onNavigationClick()
        viewModel.onAddressClick(row(ALICE))
        viewModel.onNavigationClick()

        assertEquals(BOB, recipient())
        verify(exactly = 1) { router.back() }
        verify(exactly = 0) { router.backWithResult(*anyVararg<Pair<String, Any?>>()) }
    }

    @Test
    fun shouldKeepTheCurrentRecipient_whenATapArrivesAfterTheSheetWasDismissed() {
        sharedState.updateAddress(BOB)
        val viewModel = viewModel()

        viewModel.onScreenClosed()
        viewModel.onAddressClick(row(ALICE))
        viewModel.onCreateContactClick(null, ALICE)

        assertEquals(BOB, recipient())
        verify(exactly = 0) { router.backWithResult(*anyVararg<Pair<String, Any?>>()) }
        verify(exactly = 0) { router.openCreateContact(any(), any()) }
    }

    @Test
    fun shouldKeepTheCurrentRecipient_whenATapReachesAClearedSheet() {
        sharedState.updateAddress(BOB)
        val viewModel = viewModel()

        viewModel.viewModelScope.cancel() // what ViewModel.onCleared leaves behind
        viewModel.onAddressClick(row(ALICE))

        assertEquals(BOB, recipient())
        verify(exactly = 0) { router.backWithResult(*anyVararg<Pair<String, Any?>>()) }
    }

    @Test
    fun shouldNotOpenCreateContact_afterTheRecipientWasPicked() {
        val viewModel = viewModel()

        viewModel.onAddressClick(row(ALICE))
        viewModel.onCreateContactClick(null, BOB)

        verify(exactly = 0) { router.openCreateContact(any(), any()) }
    }

    @Test
    fun shouldStillPickARecipient_afterCreateContactWasOpened() {
        val viewModel = viewModel()

        viewModel.onCreateContactClick(null, ALICE)
        viewModel.onAddressClick(row(ALICE))

        assertEquals(ALICE, recipient())
        verify(exactly = 1) { router.openCreateContact(CHAIN_ID, ALICE) }
        verify(exactly = 1) { router.backWithResult(AddressHistoryFragment.RESULT_ADDRESS to ALICE) }
    }

    @Test
    fun shouldAllowAnotherPick_whenLeavingTheSheetFailed() {
        val viewModel = viewModel()
        every { router.backWithResult(*anyVararg<Pair<String, Any?>>()) } throws IllegalStateException("no back stack")

        assertThrows(IllegalStateException::class.java) { viewModel.onAddressClick(row(ALICE)) }

        every { router.backWithResult(*anyVararg<Pair<String, Any?>>()) } returns Unit
        viewModel.onAddressClick(row(BOB))

        assertEquals(BOB, recipient())
        verify(exactly = 1) { router.backWithResult(AddressHistoryFragment.RESULT_ADDRESS to BOB) }
    }

    private fun viewModel() = AddressHistoryViewModel(
        SavedStateHandle(mapOf(AddressHistoryFragment.KEY_PAYLOAD to CHAIN_ID)),
        sharedState,
        walletInteractor,
        chainsRepository,
        mockk(relaxed = true),
        resourceManager,
        mockk(relaxed = true),
        router
    ).also(viewModels::add)

    private fun row(address: String) = Address("Contact", address, Any(), CHAIN_ID, true, null)

    private fun recipient() = runBlocking { sharedState.addressFlow.first() }

    private companion object {
        const val CHAIN_ID = "polkadot"
        const val ALICE = "5GrwvaEF5zXb26Fz9rcQpDWS57CtERHpNehXCPcNoHGKutQY"
        const val BOB = "5FHneW46xGXgs5mUiveU4sbTyGBzmstUspZC92UhjJM694ty"
    }
}
