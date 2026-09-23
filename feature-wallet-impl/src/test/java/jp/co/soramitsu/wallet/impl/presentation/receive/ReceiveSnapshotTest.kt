package jp.co.soramitsu.wallet.impl.presentation.receive

import android.graphics.Bitmap
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.resources.ClipboardManager
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.QrCodeGenerator
import jp.co.soramitsu.common.utils.write
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.bokoloCashTokenId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraMainChainId
import jp.co.soramitsu.wallet.impl.domain.CurrentAccountAddressUseCase
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.WalletAccount
import jp.co.soramitsu.wallet.impl.presentation.AssetPayload
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.wallet.impl.presentation.cross_chain.setup.ChainAssetsManager
import jp.co.soramitsu.wallet.impl.presentation.receive.model.ReceiveToggleType
import java.io.File
import java.math.BigDecimal
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import jp.co.soramitsu.core.models.Asset as CoreAsset

@OptIn(ExperimentalCoroutinesApi::class)
class ReceiveSnapshotTest {
    @get:Rule val instantTaskRule = InstantTaskExecutorRule()
    private val dispatcher = StandardTestDispatcher()
    private val interactor = mockk<WalletInteractor>(relaxed = true)
    private val generator = mockk<QrCodeGenerator>()
    private val resources = mockk<ResourceManager>(relaxed = true)
    private val clipboard = mockk<ClipboardManager>(relaxed = true)
    private val router = mockk<WalletRouter>(relaxed = true)
    private val registry = mockk<ChainRegistry>()
    private val currentAddress = mockk<CurrentAccountAddressUseCase>()
    private val manager = mockk<ChainAssetsManager>(relaxed = true)
    private val accounts = MutableStateFlow(WalletAccount("qa-address-A", "QA A"))
    private val selectedAsset = MutableStateFlow<Asset?>(null)
    private val bitmaps = mutableMapOf<String, Bitmap>()
    private val writtenBitmaps = mutableListOf<Bitmap>()
    private val viewModels = mutableListOf<ReceiveViewModel>()
    private val file = File("synthetic-receive-image")

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        mockkStatic("jp.co.soramitsu.common.utils.WriteKt")
        coEvery { any<File>().write(any(), any()) } coAnswers { writtenBitmaps += secondArg<Bitmap>() }
        every { interactor.selectedAccountFlow(any()) } returns accounts
        every { manager.assetFlow } returns selectedAsset
        coEvery { currentAddress(any()) } coAnswers { accounts.value.address }
        coEvery { registry.getChain(any()) } returns mockk { every { name } returns "SORA" }
        every { resources.getString(R.string.wallet_receive_share_message) } returns "My %s address to receive %s:"
        coEvery { interactor.createFileInTempStorageAndRetrieveAsset(any()) } returns Result.success(file)
        coEvery { interactor.getQrCodeSharingSoraString(any(), any(), any()) } coAnswers {
            val amount = thirdArg<BigDecimal?>()?.takeIf { it > BigDecimal.ZERO }?.let { ":$it" }.orEmpty()
            "substrate:${accounts.value.address}:qa-public:${accounts.value.name}:${secondArg<String>()}$amount"
        }
        coEvery { generator.generateQrBitmap(any()) } coAnswers {
            bitmaps.getOrPut(firstArg()) { mockk<Bitmap>(name = firstArg()) }
        }
    }

    @After
    fun teardown() {
        viewModels.forEach { it.coroutineContext.cancel() }
        unmockkStatic("jp.co.soramitsu.common.utils.WriteKt")
        Dispatchers.resetMain()
    }

    private fun asset(id: String, chainId: String = soraMainChainId, currencyId: String = id): Asset {
        val core = mockk<CoreAsset>(relaxed = true) {
            every { this@mockk.id } returns id
            every { this@mockk.chainId } returns chainId
            every { symbol } returns id.uppercase()
            every { precision } returns 18
            every { this@mockk.currencyId } returns currencyId
            every { iconUrl } returns ""
        }
        return Asset.createEmpty(core, 1L, byteArrayOf(1), minSupportedVersion = null)
    }

    private fun viewModel(opening: Asset = asset("xor")): ReceiveViewModel {
        selectedAsset.value = opening
        coEvery { interactor.getCurrentAsset(opening.chainId, opening.id) } returns opening
        return ReceiveViewModel(
            interactor, generator, resources, clipboard, router, registry, currentAddress,
            SavedStateHandle(mapOf(ReceiveFragment.KEY_ASSET_PAYLOAD to AssetPayload(opening.chainId, opening.id))), manager
        ).also { viewModels += it }
    }

    private fun snapshot(vm: ReceiveViewModel) = (vm.state.value as LoadingState.Loaded).data

    @Test
    fun selectingValAfterOpeningXorSharesTheDisplayedAssetAddressAndQr() = runTest(dispatcher) {
        val vm = viewModel()
        runCurrent()
        vm.receiveChanged(ReceiveToggleType.Request)
        vm.onAmountInput(BigDecimal("2.5"))
        selectedAsset.value = asset("val")
        runCurrent()
        val current = snapshot(vm)
        assertEquals("VAL", current.assetSymbol)
        assertEquals("SORA", current.networkName)
        assertEquals(BigDecimal("2.5"), current.amountInputViewState.tokenAmount)
        assertSame(bitmaps["substrate:qa-address-A:qa-public:QA A:val:2.5"], current.qrCode)
        vm.tokenClicked()
        runCurrent()
        verify { router.openSelectAsset(chainId = soraMainChainId, selectedAssetId = "val", excludeAssetId = null) }

        vm.copyClicked()
        vm.shareClicked()
        runCurrent()
        verify(exactly = 1) { clipboard.addToClipboard("qa-address-A") }
        assertSame(current.qrCode, writtenBitmaps.single())
        val payload = vm.shareEvent.value!!.peekContent()
        assertEquals("My SORA address to receive VAL: qa-address-A", payload.shareMessage)
        assertEquals(file, payload.qrFile)
    }

    @Test
    fun pendingXorQrIsCancelledAndNeverPublishedBesideValText() = runTest(dispatcher) {
        val oldQr = CompletableDeferred<Bitmap>()
        var cancelled = false
        coEvery { generator.generateQrBitmap(match { it.endsWith(":xor") }) } coAnswers {
            try { oldQr.await() } finally { cancelled = true }
        }
        val vm = viewModel()
        runCurrent()
        assertNull(snapshot(vm).qrCode)
        vm.copyClicked()
        vm.shareClicked()
        verify(exactly = 0) { clipboard.addToClipboard(any()) }
        coVerify(exactly = 0) { interactor.createFileInTempStorageAndRetrieveAsset(any()) }

        selectedAsset.value = asset("val")
        runCurrent()
        assertTrue(cancelled)
        val current = snapshot(vm)
        assertEquals("VAL", current.assetSymbol)
        assertSame(bitmaps["substrate:qa-address-A:qa-public:QA A:val"], current.qrCode)
        oldQr.complete(mockk())
        runCurrent()
        assertSame(current, snapshot(vm))
    }

    @Test
    fun changingAssetDuringSharePreparationDiscardsTheObsoleteShare() = runTest(dispatcher) {
        val fileResult = CompletableDeferred<Result<File>>()
        coEvery { interactor.createFileInTempStorageAndRetrieveAsset(any()) } coAnswers { fileResult.await() }
        val vm = viewModel()
        runCurrent()
        vm.shareClicked()
        vm.shareClicked()
        runCurrent()
        coVerify(exactly = 1) { interactor.createFileInTempStorageAndRetrieveAsset(any()) }
        selectedAsset.value = asset("val")
        runCurrent()
        fileResult.complete(Result.success(file))
        runCurrent()
        assertNull(vm.shareEvent.value)
        vm.shareClicked()
        runCurrent()
        assertEquals("My SORA address to receive VAL: qa-address-A", vm.shareEvent.value!!.peekContent().shareMessage)
        assertSame(snapshot(vm).qrCode, writtenBitmaps.last())
    }

    @Test
    fun walletChangeReplacesPlainAddressQrAndBlocksCopyWhilePending() = runTest(dispatcher) {
        val newQr = CompletableDeferred<Bitmap>()
        coEvery { generator.generateQrBitmap("qa-address-B") } coAnswers { newQr.await() }
        val vm = viewModel(asset("eth", "ethereum"))
        runCurrent()
        assertSame(bitmaps["qa-address-A"], snapshot(vm).qrCode)
        accounts.value = WalletAccount("qa-address-B", "QA B")
        runCurrent()
        assertEquals("qa-address-B", snapshot(vm).account.address)
        assertNull(snapshot(vm).qrCode)
        vm.copyClicked()
        verify(exactly = 0) { clipboard.addToClipboard(any()) }
        val bitmap = mockk<Bitmap>()
        newQr.complete(bitmap)
        runCurrent()
        assertSame(bitmap, snapshot(vm).qrCode)
        vm.copyClicked()
        verify(exactly = 1) { clipboard.addToClipboard("qa-address-B") }
        coVerify(exactly = 0) { interactor.getQrCodeSharingSoraString(any(), any(), any()) }
    }

    @Test
    fun bokoloRequestRetainsExistingTwoDecimalQrAmount() = runTest(dispatcher) {
        val vm = viewModel(asset("bokolo", currencyId = bokoloCashTokenId))
        runCurrent()
        vm.receiveChanged(ReceiveToggleType.Request)
        vm.onAmountInput(BigDecimal("1.239"))
        runCurrent()
        assertEquals(BigDecimal("1.23"), snapshot(vm).amountInputViewState.tokenAmount)
        coVerify { interactor.getQrCodeSharingSoraString(soraMainChainId, "bokolo", BigDecimal("1.23")) }
    }

    @Test
    fun failedSharePreparationCanBeRetriedWithTheSameSnapshot() = runTest(dispatcher) {
        coEvery { interactor.createFileInTempStorageAndRetrieveAsset(any()) } returnsMany listOf(
            Result.failure(IllegalStateException("Share preparation unavailable")), Result.success(file)
        )
        val vm = viewModel()
        runCurrent()
        vm.shareClicked()
        runCurrent()
        assertEquals("Share preparation unavailable", vm.errorLiveData.value?.getContentIfNotHandled())
        assertNull(vm.shareEvent.value)
        vm.shareClicked()
        runCurrent()
        assertEquals("My SORA address to receive XOR: qa-address-A", vm.shareEvent.value!!.peekContent().shareMessage)
        assertSame(snapshot(vm).qrCode, writtenBitmaps.single())
    }
}
