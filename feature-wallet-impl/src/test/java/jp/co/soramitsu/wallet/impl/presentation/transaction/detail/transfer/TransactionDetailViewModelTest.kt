package jp.co.soramitsu.wallet.impl.presentation.transaction.detail.transfer

import android.graphics.Picture
import android.graphics.drawable.PictureDrawable
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.account.api.presentation.account.AddressDisplayUseCase
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.resources.ClipboardManager
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.impl.WalletTestModels
import jp.co.soramitsu.wallet.impl.awaitValue
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.presentation.AssetPayload
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.wallet.impl.presentation.model.OperationParcelizeModel
import jp.co.soramitsu.wallet.impl.presentation.model.OperationStatusAppearance
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TransactionDetailViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    private val router = mockk<WalletRouter>(relaxed = true)
    private val clipboard = mockk<ClipboardManager>(relaxed = true)
    private val resources = mockk<ResourceManager>(relaxed = true) { every { getString(any()) } returns "Copied" }
    private val icons = mockk<AddressIconGenerator> {
        coEvery { createAddressIcon(any(), any(), any()) } returns PictureDrawable(Picture())
    }
    private val names = mockk<AddressDisplayUseCase> { coEvery { this@mockk.invoke(any()) } returns "Contact" }
    private val explorers = listOf(
        Chain.Explorer(Chain.Explorer.Type.SUBSCAN, listOf("extrinsic", "account", "transfer"), "https://subscan/{type}/{value}"),
        Chain.Explorer(Chain.Explorer.Type.ETHERSCAN, listOf("tx", "address"), "https://etherscan/{type}/{value}")
    )
    private val registry = mockk<ChainRegistry> { coEvery { getChain("polkadot") } returns WalletTestModels.chain(explorers = explorers) }
    private val payload = AssetPayload("polkadot", "dot")

    @Test
    fun incomingTransferRepeatsToTheSenderAndOffersExplorers() {
        val viewModel = viewModel(isIncome = true)

        assertEquals(WalletTestModels.BOB, viewModel.retryAddressModelLiveData.awaitValue()!!.address)
        assertEquals(WalletTestModels.ALICE, viewModel.recipientAddressModelLiveData.awaitValue()!!.address)
        assertTrue(viewModel.getSupportedExplorers(Chain.Explorer.Type.SUBSCAN, "0xhash").containsKey(Chain.Explorer.Type.SUBSCAN))
        assertTrue(viewModel.getSupportedExplorers(Chain.Explorer.Type.ETHERSCAN, "0xhash").containsKey(Chain.Explorer.Type.ETHERSCAN))
        assertTrue(viewModel.getSupportedExplorers(Chain.Explorer.Type.REEF, "0xhash").containsKey(Chain.Explorer.Type.SUBSCAN))
        assertEquals(2, viewModel.getSupportedAddressExplorers(WalletTestModels.ALICE).size)

        viewModel.repeatTransaction()
        viewModel.copyStringClicked("0xhash")
        viewModel.showExternalActionsClicked(ExternalActionsSource.TO_ADDRESS)
        viewModel.openUrl("https://subscan")
        viewModel.backClicked()

        verify { router.openSend(payload, WalletTestModels.BOB, any(), any()) }
        verify { clipboard.addToClipboard("0xhash") }
        assertEquals(ExternalActionsSource.TO_ADDRESS, viewModel.showExternalTransactionActionsEvent.value!!.peekContent())
        assertEquals("https://subscan", viewModel.openBrowserEvent.value!!.peekContent())
        verify { router.back() }
        assertEquals(Chain.Explorer.Type.SUBSCAN, viewModel.explorerType)
    }

    @Test
    fun outgoingTransferRepeatsToTheRecipient() {
        val viewModel = viewModel(isIncome = false)

        assertEquals(WalletTestModels.ALICE, viewModel.retryAddressModelLiveData.awaitValue()!!.address)
        viewModel.repeatTransaction()

        verify { router.openSend(payload, WalletTestModels.ALICE, any(), any()) }
    }

    private fun viewModel(isIncome: Boolean): TransactionDetailViewModel {
        val transfer = OperationParcelizeModel.Transfer(
            time = 1L, address = WalletTestModels.ALICE, hash = "0xhash", isIncome = isIncome, amount = "1 DOT",
            receiver = WalletTestModels.ALICE, sender = WalletTestModels.BOB, fee = "0.01 DOT", statusAppearance = OperationStatusAppearance.COMPLETED
        )
        return TransactionDetailViewModel(
            mockk<WalletInteractor>(relaxed = true), router, resources, icons, clipboard, names, registry,
            SavedStateHandle(mapOf(KEY_TRANSACTION to transfer, KEY_ASSET_PAYLOAD to payload, KEY_EXPLORER_TYPE to Chain.Explorer.Type.SUBSCAN))
        )
    }
}
