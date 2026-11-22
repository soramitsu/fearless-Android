package jp.co.soramitsu.wallet.impl.presentation.balance.list

import android.graphics.Picture
import android.graphics.drawable.PictureDrawable
import android.net.Uri
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.SwipeableState
import androidx.lifecycle.viewModelScope
import co.jp.soramitsu.walletconnect.domain.WalletConnectInteractor
import co.jp.soramitsu.walletconnect.model.WalletConnectPairingExpiredException
import co.jp.soramitsu.walletconnect.model.WalletConnectUriException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.account.api.domain.UniversalWalletPasswordNotice
import jp.co.soramitsu.account.api.domain.UniversalWalletSlot
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.interfaces.TotalBalanceUseCase
import jp.co.soramitsu.androidfoundation.coroutine.CoroutineManager
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.BuildConfig
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.compose.component.ActionItemType
import jp.co.soramitsu.common.compose.component.ChainSelectorViewStateWithFilters
import jp.co.soramitsu.common.compose.component.SwipeState
import jp.co.soramitsu.common.compose.viewstate.AssetListItemViewState
import jp.co.soramitsu.common.data.network.coingecko.FiatCurrency
import jp.co.soramitsu.common.data.network.config.ProductFeatureToggleStore
import jp.co.soramitsu.common.data.storage.UpdateSecurityNoticeTracker
import jp.co.soramitsu.common.domain.GetAvailableFiatCurrencies
import jp.co.soramitsu.common.domain.SelectedFiat
import jp.co.soramitsu.common.resources.ClipboardManager
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.nft.domain.NFTInteractor
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraMainChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraTestChainId
import jp.co.soramitsu.runtime.multiNetwork.tokengroups.ResolvedTokenGroups
import jp.co.soramitsu.tonconnect.api.domain.TonConnectInteractor
import jp.co.soramitsu.tonconnect.api.model.AppEntity
import jp.co.soramitsu.wallet.impl.WalletTestModels
import jp.co.soramitsu.wallet.impl.data.network.blockchain.balance.InMemoryPreferences
import jp.co.soramitsu.wallet.impl.data.network.blockchain.balance.NetworkScanStateStore
import jp.co.soramitsu.wallet.impl.domain.ChainInteractor
import jp.co.soramitsu.wallet.impl.domain.CurrentAccountAddressUseCase
import jp.co.soramitsu.wallet.impl.domain.SendPausePolicy
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.domain.model.ControllerDeprecationWarning
import jp.co.soramitsu.wallet.impl.domain.model.QrContentSora
import jp.co.soramitsu.wallet.impl.presentation.AssetPayload
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.wallet.impl.presentation.balance.list.model.AssetType
import jp.co.soramitsu.wallet.impl.presentation.bundledPortfolioVisibility
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.job
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class BalanceListInteractionTest {
    @get:Rule val instant = InstantTaskExecutorRule()
    @get:Rule val main = MainCoroutineRule()
    private val meta = WalletTestModels.metaAccount()
    private val config = WalletTestModels.asset()
    private val chain = WalletTestModels.chain(assets = listOf(config))
    private val router = mockk<WalletRouter>(relaxed = true) {
        every { chainSelectorPayloadFlow } returns emptyFlow()
    }
    private val wallet = mockk<WalletInteractor>(relaxed = true) {
        every { assetsFlowAndAccount() } returns flowOf(1L to emptyList())
        every { selectedMetaAccountFlow() } returns flowOf(meta)
        every { selectedLightMetaAccountFlow() } returns flowOf(meta)
        every { observeSelectedAccountChainSelectFilter() } returns flowOf(ChainSelectorViewStateWithFilters.Filter.All)
        every { getAssetManagementIntroPassed() } returns true
        every { networkIssuesFlow() } returns flowOf(emptyMap())
        coEvery { getSelectedMetaAccount() } returns meta
        coEvery { getSavedChainId(any()) } returns null
        coEvery { getChain(any()) } returns chain
        every { getChains() } returns flowOf(listOf(chain))
        coEvery { syncAssetsRates() } returns Result.success(Unit)
        coEvery { tryReadSoraFormat(any()) } returns null
        coEvery { extractTonAddress(any()) } returns null
        coEvery { extractEthAddress(any()) } returns null
        coEvery { checkControllerDeprecations() } returns emptyList()
    }
    private val accounts = mockk<AccountInteractor>(relaxed = true) {
        every { selectedLightMetaAccountFlow() } returns flowOf(meta)
        every { walletRecoveryRequiredFlow(any()) } returns flowOf(false)
        coEvery { selectedLightMetaAccount() } returns meta
        every { lightMetaAccountsFlow() } returns flowOf(listOf(meta))
    }
    private val resources = mockk<ResourceManager>(relaxed = true) {
        every { getString(any()) } answers { "res:" + firstArg<Int>() }
        every { getString(any(), *anyVararg()) } answers { "res:" + firstArg<Int>() }
    }
    private val clipboard = mockk<ClipboardManager>(relaxed = true)
    private val fiat = mockk<SelectedFiat>(relaxed = true) {
        every { flow() } returns flowOf("usd")
        every { get() } returns "usd"
    }
    private val usd = FiatCurrency("usd", "$", "Dollar", "usd.svg")
    private val eur = FiatCurrency("eur", "€", "Euro", "eur.svg")
    private val currencies = mockk<GetAvailableFiatCurrencies>(relaxed = true).also {
        every { it.flow() } returns flowOf(emptyList())
        coEvery { it.invoke() } returns listOf(usd, eur)
    }
    private val walletConnect = mockk<WalletConnectInteractor>(relaxed = true)
    private val tonConnect = mockk<TonConnectInteractor>(relaxed = true)
    private val passwordNotice = mockk<UniversalWalletPasswordNotice>(relaxed = true) {
        every { isDismissed(any()) } returns true
    }
    private val models = mutableListOf<BalanceListViewModel>()

    @After fun dispose() {
        val jobs = models.map { it.viewModelScope.coroutineContext.job }
        models.forEach { it.viewModelScope.cancel() }
        await { jobs.all { it.isCompleted } }
    }

    @Test fun toolbarAndManagementActionsOpenTheirDestinations() {
        val vm = model()
        vm.openWalletSelector(); vm.openSearchAssets(); vm.onManageAssetClick()
        vm.onHiddenFundsReviewClick(); vm.onServiceButtonClick(); vm.onWalletRecoveryClick()
        main.dispatcher.scheduler.runCurrent()
        verify {
            router.openSelectWallet(); router.openSearchAssets(); router.openManageAssets("All")
            router.openManageAssets("Hidden"); router.openServiceScreen(); router.openRecoveryCenter()
        }
    }

    @Test fun fiatChooserPreservesSelectionAndUpdatesTheChosenCurrency() {
        val vm = model()
        vm.onBalanceClicked()
        await { vm.showFiatChooser.value != null }
        val chooser = vm.showFiatChooser.value!!.peekContent()
        assertEquals(listOf(usd, eur), chooser.data)
        assertEquals(usd, chooser.selected)
        vm.onFiatSelected(eur)
        main.dispatcher.scheduler.runCurrent()
        verify { fiat.set("eur") }
    }

    @Test fun emptyCurrencyListDoesNotOpenAnEmptyChooser() {
        coEvery { currencies.invoke() } returns emptyList()
        val vm = model()
        vm.onBalanceClicked()
        main.dispatcher.scheduler.runCurrent()
        assertNull(vm.showFiatChooser.value)
    }

    @Test fun backupDismissalRequiresTheDialogConfirmation() {
        val vm = model()
        vm.onBackupClicked()
        main.dispatcher.scheduler.runCurrent()
        verify { router.openBackupWalletScreen(1L) }
        vm.onBackupCloseClick()
        coVerify(exactly = 0) { accounts.updateWalletBackedUp(any()) }
        val dialog = vm.errorDialogStateLiveData.value!!.peekContent()
        assertEquals("res:" + R.string.backup_not_backed_up_message, dialog.message)
        dialog.positiveClick()
        main.dispatcher.scheduler.runCurrent()
        coVerify(exactly = 1) { accounts.updateWalletBackedUp(1L) }
    }

    @Test fun joiningAccountsAndDismissingTheirBannersUpdatesState() {
        val vm = model()
        main.dispatcher.scheduler.runCurrent()
        vm.onJoinSubOrEvmClicked(); vm.onJoinTonClicked()
        verify { router.openCreateSubstrateOrEvmAccountScreen(); router.openCreateTonAccountScreen() }
        vm.onJoinSubOrEvmCloseClick(); vm.onJoinTonCloseClick()
        assertTrue(vm.state.value.hasSubOrEvmAccounts)
        assertTrue(vm.state.value.hasTonAccounts)
        vm.updateAppClicked()
        assertNotNull(vm.openPlayMarket.value)
    }

    @Test fun assetRowsRouteSupportedAssetsAndExplainUnsupportedOnes() {
        val vm = model()
        val row = mockk<AssetListItemViewState> {
            every { isSupported } returns false
            every { chainId } returns chain.id
            every { chainAssetId } returns config.id
        }
        vm.assetClicked(row)
        main.dispatcher.scheduler.runCurrent()
        assertNotNull(vm.showUnsupportedChainAlert.value)
        verify(exactly = 0) { router.openAssetDetails(any()) }
        every { row.isSupported } returns true
        vm.assetClicked(row)
        main.dispatcher.scheduler.runCurrent()
        verify { router.openAssetDetails(AssetPayload(chain.id, config.id)) }
    }

    @OptIn(ExperimentalMaterialApi::class)
    @Test fun assetVisibilityAndReceiveActionsUseTheExactAsset() {
        val vm = model()
        val swipe = mockk<SwipeableState<SwipeState>>(relaxed = true)
        vm.actionItemClicked(ActionItemType.SHOW, chain.id, config.id, swipe)
        vm.actionItemClicked(ActionItemType.RECEIVE, chain.id, config.id, swipe)
        main.dispatcher.scheduler.runCurrent()
        coVerify(exactly = 2) { swipe.snapTo(SwipeState.INITIAL) }
        coVerify { wallet.markAssetAsShown(chain.id, config.id) }
        verify { router.openReceive(AssetPayload(chain.id, config.id)) }
    }

    @Test fun ordinaryTonAndEthereumQrCodesChooseTheirParsedAddresses() {
        coEvery { wallet.extractTonAddress("ton-link") } returns WalletTestModels.TON_RAW
        coEvery { wallet.extractEthAddress("eth-link") } returns WalletTestModels.ETH_A
        val vm = model()
        listOf("plain-address", "ton-link", "eth-link").forEach(vm::qrCodeScanned)
        main.dispatcher.scheduler.runCurrent()
        listOf("plain-address", WalletTestModels.TON_RAW, WalletTestModels.ETH_A).forEach { recipient ->
            verify { router.openSend(null, recipient, null, any()) }
        }
    }

    @Test fun soraQrPreservesTheRequestedAmountAndToken() {
        // The view model looks for the build's SORA network: the test network in debug builds, mainnet in release.
        val soraChainId = if (BuildConfig.DEBUG) soraTestChainId else soraMainChainId
        val soraAsset = config.copy(chainId = soraChainId, currencyId = "sora-token")
        every { wallet.getChains() } returns flowOf(listOf(WalletTestModels.chain(id = soraChainId, assets = listOf(soraAsset))))
        coEvery { wallet.tryReadSoraFormat("sora-qr") } returns QrContentSora(WalletTestModels.BOB, "", "Bob", "sora-token", "3.5")
        val vm = model()
        vm.qrCodeScanned("sora-qr")
        main.dispatcher.scheduler.runCurrent()
        verify { router.openLockedAmountSend(AssetPayload(soraChainId, config.id), WalletTestModels.BOB, "sora-token", BigDecimal("3.5")) }
    }

    @Test fun malformedSoraAmountDoesNotInventAnAmount() {
        coEvery { wallet.tryReadSoraFormat("sora-qr") } returns QrContentSora(WalletTestModels.BOB, "", "Bob", "missing-token", "oops")
        val vm = model()
        vm.qrCodeScanned("sora-qr")
        main.dispatcher.scheduler.runCurrent()
        verify { router.openLockedAmountSend(null, WalletTestModels.BOB, "missing-token", null) }
    }

    @Test fun walletConnectPairingShowsInvalidExpiredAndUnexpectedErrors() {
        val vm = model()
        every { walletConnect.pair(any(), any(), any()) } answers { thirdArg<(Throwable) -> Unit>()(WalletConnectUriException("bad uri")) }
        vm.qrCodeScanned("wc:invalid")
        await { vm.errorDialogStateLiveData.value != null }
        assertEquals("res:" + R.string.connection_invalid_url_error_message, vm.errorDialogStateLiveData.value!!.peekContent().message)
        every { walletConnect.pair(any(), any(), any()) } answers { thirdArg<(Throwable) -> Unit>()(WalletConnectPairingExpiredException()) }
        vm.qrCodeScanned("wc:expired")
        await { vm.errorLiveData.value != null }
        assertEquals("res:" + R.string.walletconnect_link_expired, vm.errorLiveData.value!!.peekContent())
        every { walletConnect.pair(any(), any(), any()) } answers { thirdArg<(Throwable) -> Unit>()(IllegalStateException("Pair failed")) }
        vm.qrCodeScanned("wc:failure")
        await { vm.errorLiveData.value?.peekContent() == "Pair failed" }
        assertEquals("Pair failed", vm.errorLiveData.value!!.peekContent())
    }

    @Test fun tonConnectQrReadsTheManifestAndReturnsTheReviewedResponse() {
        val app = AppEntity("https://dapp.test", "Example", "https://dapp.test/icon.png", null, null)
        val response = JSONObject().put("reviewed", true)
        coEvery { tonConnect.readManifest("https://dapp.test/manifest.json") } returns app
        coEvery { router.openTonConnectionAndWaitForResult(app, "proof") } returns response
        val vm = model()
        vm.qrCodeScanned(tonQr())
        main.dispatcher.scheduler.runCurrent()
        coVerify { tonConnect.respondDappConnectRequest("a".repeat(64), match { it.proofPayload == "proof" }, response, app) }
        assertNull(vm.errorLiveData.value)
    }

    @Test fun tonConnectManifestFailureIsReportedWithoutAConnectionPrompt() {
        coEvery { tonConnect.readManifest(any()) } throws IllegalStateException("Manifest unavailable")
        val vm = model()
        vm.qrCodeScanned(tonQr())
        await { vm.errorLiveData.value != null }
        assertEquals("Manifest unavailable", vm.errorLiveData.value!!.peekContent())
        coVerify(exactly = 0) { router.openTonConnectionAndWaitForResult(any(), any()) }
    }

    @Test fun invalidTonClientIsRejectedBeforeReadingAManifest() {
        val vm = model()
        vm.qrCodeScanned("tc://?v=2&id=short")
        await { vm.errorLiveData.value != null }
        assertEquals("res:" + R.string.ton_connect_invalid_link, vm.errorLiveData.value!!.peekContent())
        coVerify(exactly = 0) { tonConnect.readManifest(any()) }
        verify(exactly = 0) { router.openSend(any(), any(), any(), any()) }
    }

    @Test fun universalTonConnectLinkQrIsConnectedNotSentTo() {
        val app = AppEntity("https://dapp.test", "Example", "https://dapp.test/icon.png", null, null)
        val response = JSONObject().put("reviewed", true)
        coEvery { tonConnect.readManifest("https://dapp.test/manifest.json") } returns app
        coEvery { router.openTonConnectionAndWaitForResult(app, "proof") } returns response
        val vm = model()
        vm.qrCodeScanned(tonQr(base = "https://fearlesswallet.io/ton-connect/"))
        main.dispatcher.scheduler.runCurrent()
        coVerify { tonConnect.respondDappConnectRequest("a".repeat(64), match { it.proofPayload == "proof" }, response, app) }
        verify(exactly = 0) { router.openSend(any(), any(), any(), any()) }
        assertNull(vm.errorLiveData.value)
    }

    @Test fun passwordNoticeCanBeReviewedSwitchedAndDismissed() {
        val row = UniversalWalletPasswordNotice.Row(UniversalWalletSlot.TON, "old", "new")
        every { passwordNotice.isDismissed(1L) } returns false
        coEvery { passwordNotice.rows(1L) } returns listOf(row)
        coEvery { passwordNotice.switch(1L) } returns listOf(row)
        val vm = model()
        await { vm.showUniversalPasswordNotice.value != null }
        assertEquals(listOf(row), vm.showUniversalPasswordNotice.value!!.peekContent().rows)
        vm.onUniversalPasswordSwitch(1L)
        await { vm.universalPasswordSwitchResult.value != null }
        assertEquals(1L, vm.universalPasswordSwitchResult.value!!.peekContent().metaId)
        assertEquals(listOf(row), vm.universalPasswordSwitchResult.value!!.peekContent().rows)
        vm.onUniversalPasswordNoticeClosed(1L)
        verify { passwordNotice.dismiss(1L) }
    }

    @Test fun noticeReadFailureDoesNotPreventWalletRefresh() {
        every { passwordNotice.isDismissed(1L) } returns false
        coEvery { passwordNotice.rows(1L) } throws IllegalStateException("unavailable")
        coEvery { wallet.syncAssetsRates() } returns Result.failure(IllegalStateException("rates offline"))
        val vm = model()
        vm.onRefresh()
        main.dispatcher.scheduler.runCurrent()
        assertNull(vm.showUniversalPasswordNotice.value)
        verify { fiat.notifySyncFailed() }
        coVerify { passwordNotice.refresh(1L) }
    }

    @Test fun controllerDeprecationOffersTheCorrectRecoveryAction() {
        coEvery { wallet.checkControllerDeprecations() } returns listOf(ControllerDeprecationWarning.ChangeController(chain.id, chain.name))
        val vm = model()
        vm.onResume()
        await { vm.errorDialogStateLiveData.value != null }
        vm.errorDialogStateLiveData.value!!.peekContent().positiveClick()
        verify { router.openManageControllerAccount(chain.id) }
        coEvery { wallet.checkControllerDeprecations() } returns listOf(ControllerDeprecationWarning.ImportStash(chain.id, WalletTestModels.ALICE))
        vm.onResume()
        main.dispatcher.scheduler.runCurrent()
        vm.errorDialogStateLiveData.value!!.peekContent().positiveClick()
        verify { router.openImportAccountScreenFromWallet(0) }
    }

    @Test fun nftTabExposesFiltersAndLayoutControls() {
        val vm = model()
        vm.assetTypeChanged(AssetType.NFTs)
        await { vm.state.value.assetsState is WalletAssetsState.NftAssets }
        assertEquals(AssetType.NFTs, vm.state.value.multiToggleButtonState.currentSelection)
        vm.assetTypeChanged(AssetType.Currencies)
        await { vm.state.value.multiToggleButtonState.currentSelection == AssetType.Currencies && vm.state.value.assetsState !is WalletAssetsState.NftAssets }
        assertFalse(vm.state.value.assetsState is WalletAssetsState.NftAssets)
    }

    private fun tonQr(base: String = "tc://"): String {
        val request = """{"manifestUrl":"https://dapp.test/manifest.json","items":[{"name":"ton_addr"},{"name":"ton_proof","payload":"proof"}]}"""
        return "$base?v=2&id=${"a".repeat(64)}&r=${Uri.encode(request)}"
    }

    private fun model(): BalanceListViewModel {
        val icons = mockk<AddressIconGenerator>(relaxed = true) {
            coEvery { createAddressIcon(any(), any(), any()) } returns PictureDrawable(Picture())
            coEvery { createWalletIcon(any(), any()) } returns PictureDrawable(Picture())
        }
        val chains = mockk<ChainInteractor>(relaxed = true) {
            every { getChainsFlow() } returns flowOf(listOf(chain))
            coEvery { getChainAssets() } returns listOf(config)
        }
        val total = mockk<TotalBalanceUseCase> { every { observe(any()) } returns emptyFlow() }
        val currentAddress = mockk<CurrentAccountAddressUseCase>().also { coEvery { it.invoke(any()) } returns WalletTestModels.ALICE }
        val nft = mockk<NFTInteractor>(relaxed = true) {
            every { nftFiltersFlow() } returns flowOf(emptyMap())
            every { collectionsFlow(any(), any()) } returns flowOf(emptySequence())
        }
        val scan = mockk<NetworkScanStateStore> { every { states } returns MutableStateFlow(emptyMap()) }
        return BalanceListViewModel(wallet, chains, icons, router, currencies, fiat, accounts,
            resources, clipboard, currentAddress, total, mockk(relaxed = true), nft, walletConnect,
            CoroutineManager(main.dispatcher, main.dispatcher), tonConnect, scan, mockk(relaxed = true),
            ProductFeatureToggleStore(InMemoryPreferences(), mockk(relaxed = true)),
            SendPausePolicy({ true }, { true }, { error("No paused networks") }), bundledPortfolioVisibility(),
            PortfolioPresentationPreferences(InMemoryPreferences()), mockk {
                every { observeResolvedGroups() } returns flowOf(ResolvedTokenGroups.EMPTY)
                every { resolve(any()) } returns ResolvedTokenGroups.EMPTY
            }, UpdateSecurityNoticeTracker(InMemoryPreferences()), passwordNotice).also { models += it }
    }

    private fun await(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        do {
            main.dispatcher.scheduler.runCurrent()
            if (condition()) return
            Thread.sleep(10)
        } while (System.currentTimeMillis() < deadline)
        assertTrue("View-model condition did not complete", condition())
    }
}
