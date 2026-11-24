package jp.co.soramitsu.wallet.impl.presentation.transaction.detail.extrinsic

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
import jp.co.soramitsu.common.data.network.BlockExplorerUrlBuilder
import jp.co.soramitsu.common.resources.ClipboardManager
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.impl.WalletTestModels
import jp.co.soramitsu.wallet.impl.awaitValue
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.wallet.impl.presentation.model.OperationParcelizeModel
import jp.co.soramitsu.wallet.impl.presentation.model.OperationStatusAppearance
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ExtrinsicDetailViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    @Test
    fun detailShowsTheOriginAndReportsActions() {
        val router = mockk<WalletRouter>(relaxed = true)
        val clipboard = mockk<ClipboardManager>(relaxed = true)
        val icons = mockk<AddressIconGenerator> { coEvery { createAddressIcon(any(), any(), any()) } returns PictureDrawable(Picture()) }
        val names = mockk<AddressDisplayUseCase> { coEvery { this@mockk.invoke(any()) } returns "Me" }
        val explorers = listOf(Chain.Explorer(Chain.Explorer.Type.SUBSCAN, listOf("extrinsic", "account"), "https://subscan/{type}/{value}"))
        val registry = mockk<ChainRegistry> { coEvery { getChain("polkadot") } returns WalletTestModels.chain(explorers = explorers) }
        val operation = OperationParcelizeModel.Extrinsic(1L, WalletTestModels.ALICE, "0xext", "staking", "bond", "0.01", OperationStatusAppearance.FAILED)
        val viewModel = ExtrinsicDetailViewModel(
            clipboard, mockk<ResourceManager>(relaxed = true) { every { getString(any()) } returns "Copied" }, names, icons, router, registry,
            SavedStateHandle(mapOf(ExtrinsicDetailFragment.PAYLOAD_KEY to ExtrinsicDetailsPayload(operation, "polkadot")))
        )

        assertEquals("Me", viewModel.fromAddressModelLiveData.awaitValue()!!.name)
        assertEquals("https://subscan/extrinsic/0xext", viewModel.getSupportedExplorers(BlockExplorerUrlBuilder.Type.EXTRINSIC, "0xext")[Chain.Explorer.Type.SUBSCAN])
        assertEquals(1, viewModel.getSupportedAddressExplorers(WalletTestModels.ALICE).size)

        viewModel.copyStringClicked("0xext")
        viewModel.showExternalActionsClicked(ExternalActionsSource.FROM_ADDRESS)
        viewModel.openUrl("https://subscan")
        viewModel.backClicked()

        verify { clipboard.addToClipboard("0xext") }
        assertEquals(ExternalActionsSource.FROM_ADDRESS, viewModel.showExternalExtrinsicActionsEvent.value!!.peekContent())
        assertEquals("https://subscan", viewModel.openBrowserEvent.value!!.peekContent())
        verify { router.back() }
        assertEquals("polkadot", viewModel.payload.chainId)
    }
}
