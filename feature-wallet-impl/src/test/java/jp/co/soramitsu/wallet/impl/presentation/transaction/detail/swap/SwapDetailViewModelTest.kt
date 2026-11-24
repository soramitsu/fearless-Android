package jp.co.soramitsu.wallet.impl.presentation.transaction.detail.swap

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigInteger
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.resources.ClipboardManager
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.polkaswap.api.models.Market
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.impl.WalletTestModels
import jp.co.soramitsu.wallet.impl.domain.model.Operation
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.wallet.impl.presentation.model.OperationParcelizeModel
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
class SwapDetailViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    private val xor = WalletTestModels.asset(id = "xor", chainId = "sora", precision = 18)
    private val valAsset = WalletTestModels.asset(id = "val", chainId = "sora", precision = 18)
    private val router = mockk<WalletRouter>(relaxed = true)
    private val clipboard = mockk<ClipboardManager>(relaxed = true)
    private val resources = mockk<ResourceManager>(relaxed = true) { every { getString(any()) } returns "Copied" }
    private val accounts = mockk<AccountRepository>(relaxed = true) {
        coEvery { getSelectedLightMetaAccount() } returns WalletTestModels.metaAccount(name = "Main")
    }

    @Test
    fun stateDescribesTheSwapAndOffersSubscanActions() {
        val chain = WalletTestModels.chain(id = "sora", assets = listOf(xor, valAsset), addressPrefix = 69,
            explorers = listOf(Chain.Explorer(Chain.Explorer.Type.SUBSCAN, listOf("extrinsic"), "https://sora.subscan.io/{type}/{value}")))
        val viewModel = viewModel(chain, swap(valAsset, "MulticollateralBondingCurvePool"), address = WalletTestModels.metaAccount().address(chain)!!)

        val state = viewModel.state.value
        assertEquals("XOR", state.fromTokenName)
        assertEquals("VAL", state.toTokenName)
        assertEquals(Market.TBC, state.market)
        assertEquals(SwapStatusAppearance.COMPLETED, state.statusAppearance)
        assertTrue(state.isShowSubscanButtons)
        assertEquals("Main", state.addressName)

        viewModel.onSubscanClick()
        viewModel.onShareClick()
        viewModel.onItemClick(SwapDetailViewModel.CODE_HASH_CLICK)
        viewModel.onItemClick(99)
        viewModel.onCloseClick()

        assertEquals("https://sora.subscan.io/extrinsic/0xswap", viewModel.openBrowserEvent.value!!.peekContent())
        assertEquals("https://sora.subscan.io/extrinsic/0xswap", viewModel.shareUrlEvent.value!!.peekContent())
        verify(exactly = 1) { clipboard.addToClipboard("0xswap") }
        verify { router.back() }
    }

    @Test
    fun swapWithoutTargetOrExplorerFallsBackToPlaceholders() {
        val chain = WalletTestModels.chain(id = "sora", assets = listOf(xor))
        val viewModel = viewModel(chain, swap(null, null), address = "someone else")

        val state = viewModel.state.value
        assertEquals("???", state.toTokenName)
        assertEquals("???", state.toTokenAmount)
        assertEquals(Market.SMART, state.market)
        assertEquals(false, state.isShowSubscanButtons)
        assertEquals(null, state.addressName)

        viewModel.onSubscanClick()
        assertEquals(null, viewModel.openBrowserEvent.value)
        assertEquals(SwapStatusAppearance.UNKNOWN, Operation.Status.UNKNOWN.mapToStatusAppearance())
        assertEquals(SwapStatusAppearance.FAILED, Operation.Status.FAILED.mapToStatusAppearance())
        assertEquals(SwapStatusAppearance.PENDING, Operation.Status.PENDING.mapToStatusAppearance())
    }

    private fun swap(target: jp.co.soramitsu.core.models.Asset?, market: String?) = OperationParcelizeModel.Swap(
        id = "s", address = "", hash = "0xswap", time = 1_700_000_000_000, module = "liquidityProxy",
        chainAsset = xor, targetAsset = target, baseAssetAmount = BigInteger.TEN.pow(18), liquidityProviderFee = BigInteger.TEN.pow(15),
        selectedMarket = market, targetAssetAmount = BigInteger.TEN.pow(18).multiply(BigInteger.TWO), networkFee = BigInteger.TEN.pow(16),
        status = if (target == null) Operation.Status.UNKNOWN else Operation.Status.COMPLETED
    )

    private fun viewModel(chain: Chain, swap: OperationParcelizeModel.Swap, address: String): SwapDetailViewModel {
        val withAddress = OperationParcelizeModel.Swap(
            swap.id, address, swap.hash, swap.time, swap.module, swap.chainAsset, swap.targetAsset, swap.baseAssetAmount,
            swap.liquidityProviderFee, swap.selectedMarket, swap.targetAssetAmount, swap.networkFee, swap.status
        )
        val registry = mockk<ChainRegistry> { coEvery { getChain("sora") } returns chain }
        return SwapDetailViewModel(router, registry, resources, clipboard, accounts, SavedStateHandle(mapOf(SwapDetailFragment.KEY_SWAP to withAddress)))
    }
}
