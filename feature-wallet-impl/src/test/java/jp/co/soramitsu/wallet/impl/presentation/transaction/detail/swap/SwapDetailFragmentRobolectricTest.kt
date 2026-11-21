package jp.co.soramitsu.wallet.impl.presentation.transaction.detail.swap

import android.content.Intent
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.core.content.IntentCompat
import androidx.lifecycle.SavedStateHandle
import com.google.android.material.bottomsheet.BottomSheetDialog
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigInteger
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.resources.ClipboardManager
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.impl.WalletTestModels
import jp.co.soramitsu.wallet.impl.domain.model.Operation
import jp.co.soramitsu.wallet.impl.presentation.ComposeFixtures
import jp.co.soramitsu.wallet.impl.presentation.HiltFragmentHost
import jp.co.soramitsu.wallet.impl.presentation.StubHiltActivity
import jp.co.soramitsu.wallet.impl.presentation.StubHiltApplication
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.wallet.impl.presentation.model.OperationParcelizeModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowToast

/** The swap detail sheet shows the swap, shares its Subscan link, opens Subscan and reports a copied hash. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = StubHiltApplication::class, qualifiers = "w420dp-h2000dp")
class SwapDetailFragmentRobolectricTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    @get:Rule
    val compose = createEmptyComposeRule().apply { mainClock.autoAdvance = false }

    // Icons without a URL keep the sheet's token images off the network.
    private val xor = WalletTestModels.asset(id = "xor", chainId = "sora", precision = 18).copy(iconUrl = "", color = "E6007A")
    private val valAsset = WalletTestModels.asset(id = "val", chainId = "sora", precision = 18).copy(iconUrl = "", color = "E6007A")
    private val router = mockk<WalletRouter>(relaxed = true)
    private val clipboard = mockk<ClipboardManager>(relaxed = true)
    private val resources = mockk<ResourceManager>(relaxed = true) { every { getString(any()) } returns "Copied" }
    private val accounts = mockk<AccountRepository>(relaxed = true) {
        coEvery { getSelectedLightMetaAccount() } returns WalletTestModels.metaAccount(name = "Main")
    }
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
    fun sheetShowsTheSwapAndSharesItsSubscanLink() {
        val viewModel = viewModel()
        val fragment = hostSwapDetail(viewModel)

        compose.onNodeWithText("0xswap").assertExists()
        assertTrue((fragment.requireDialog() as BottomSheetDialog).behavior.skipCollapsed)

        viewModel.onShareClick()
        ShadowLooper.idleMainLooper()

        val chooser = shadowOf(host!!.get()).nextStartedActivity
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        assertEquals(fragment.getString(jp.co.soramitsu.common.R.string.common_share), chooser.getCharSequenceExtra(Intent.EXTRA_TITLE).toString())
        val shared = IntentCompat.getParcelableExtra(chooser, Intent.EXTRA_INTENT, Intent::class.java)!!
        assertEquals(Intent.ACTION_SEND, shared.action)
        assertEquals("text/plain", shared.type)
        assertEquals(SUBSCAN_LINK, shared.getStringExtra(Intent.EXTRA_TEXT))
    }

    @Test
    fun subscanClickOpensTheExplorerInTheBrowser() {
        val viewModel = viewModel()
        hostSwapDetail(viewModel)

        viewModel.onSubscanClick()
        ShadowLooper.idleMainLooper()

        val opened = shadowOf(host!!.get()).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, opened.action)
        assertEquals(SUBSCAN_LINK, opened.dataString)
    }

    @Test
    fun copiedHashIsReportedWithAToast() {
        val viewModel = viewModel()
        hostSwapDetail(viewModel)

        viewModel.onItemClick(SwapDetailViewModel.CODE_HASH_CLICK)
        ShadowLooper.idleMainLooper()

        verify { clipboard.addToClipboard("0xswap") }
        assertEquals("Copied", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun bundleCarriesTheSwap() {
        val swap = swap()

        @Suppress("DEPRECATION")
        val stored = SwapDetailFragment.getBundle(swap).get(SwapDetailFragment.KEY_SWAP)

        assertSame(swap, stored)
    }

    private fun hostSwapDetail(viewModel: SwapDetailViewModel): SwapDetailFragment {
        val fragment = SwapDetailFragment()
        host = HiltFragmentHost.host(fragment, mapOf(SwapDetailViewModel::class.java to viewModel))
        ShadowLooper.idleMainLooper()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        return fragment
    }

    private fun viewModel(): SwapDetailViewModel {
        val chain = WalletTestModels.chain(
            id = "sora", assets = listOf(xor, valAsset), addressPrefix = 69,
            explorers = listOf(Chain.Explorer(Chain.Explorer.Type.SUBSCAN, listOf("extrinsic"), "https://sora.subscan.io/{type}/{value}"))
        )
        val registry = mockk<ChainRegistry> { coEvery { getChain("sora") } returns chain }
        return SwapDetailViewModel(router, registry, resources, clipboard, accounts, SavedStateHandle(mapOf(SwapDetailFragment.KEY_SWAP to swap())))
    }

    private fun swap() = OperationParcelizeModel.Swap(
        id = "s", address = "someone else", hash = "0xswap", time = 1_700_000_000_000, module = "liquidityProxy",
        chainAsset = xor, targetAsset = valAsset, baseAssetAmount = BigInteger.TEN.pow(18), liquidityProviderFee = BigInteger.TEN.pow(15),
        selectedMarket = "MulticollateralBondingCurvePool", targetAssetAmount = BigInteger.TEN.pow(18).multiply(BigInteger.TWO),
        networkFee = BigInteger.TEN.pow(16), status = Operation.Status.COMPLETED
    )

    private companion object {
        const val SUBSCAN_LINK = "https://sora.subscan.io/extrinsic/0xswap"
    }
}
