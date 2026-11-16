package jp.co.soramitsu.wallet.impl.presentation.addressbook

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.wallet.impl.WalletTestModels
import jp.co.soramitsu.wallet.impl.awaitFirst
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CreateContactViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    private val chainSelection = MutableStateFlow<String?>(null)
    private val router = mockk<WalletRouter>(relaxed = true) { every { chainSelectorPayloadFlow } returns chainSelection }
    private val interactor = mockk<WalletInteractor>(relaxed = true) {
        coEvery { getChain("polkadot") } returns WalletTestModels.chain()
        coEvery { getChain("kusama") } returns WalletTestModels.chain(id = "kusama")
        coEvery { validateSendAddress("polkadot", "5Good") } returns true
        coEvery { validateSendAddress("kusama", "bad") } returns false
    }
    private val resources = mockk<ResourceManager>(relaxed = true) { every { getString(any()) } returns "Network" }

    @Test
    fun validContactIsSavedForTheChosenNetwork() {
        val viewModel = viewModel(address = " 5Good ", chainId = "polkadot")

        assertEquals("Polkadot", viewModel.state.awaitFirst { it.chainSelectorState.subTitle != null }.chainSelectorState.subTitle)
        assertFalse(viewModel.state.value.isCreateEnabled)
        viewModel.onNameInput("Alice")
        assertTrue(viewModel.state.awaitFirst { it.isCreateEnabled }.isCreateEnabled)

        viewModel.onChainClick()
        viewModel.onCreateContactClick()

        verify { router.openSelectChain(selectedChainId = "polkadot", showAllChains = false) }
        coVerify { interactor.saveAddress("Alice", "5Good", "polkadot") }
        verify { router.back() }
    }

    @Test
    fun invalidAddressShowsAnErrorAndAChainChoiceFromTheSelector() {
        val viewModel = viewModel(address = null, chainId = null)
        viewModel.onCreateContactClick()

        chainSelection.value = "kusama"
        assertEquals("Kusama", viewModel.state.awaitFirst { it.chainSelectorState.subTitle != null }.chainSelectorState.subTitle)
        viewModel.onNameInput("Bob")
        viewModel.onAddressInput("bad")
        viewModel.state.awaitFirst { it.isCreateEnabled }
        viewModel.onCreateContactClick()
        viewModel.onNavigationClick()

        coVerify(exactly = 0) { interactor.saveAddress(any(), any(), any()) }
        coVerify { interactor.validateSendAddress("kusama", "bad") }
        verify(exactly = 1) { router.back() }
    }

    private fun viewModel(address: String?, chainId: String?) = CreateContactViewModel(
        SavedStateHandle(mapOf(CreateContactFragment.KEY_PAYLOAD to address, CreateContactFragment.KEY_CHAIN_ID to chainId)),
        interactor, resources, router
    )
}
