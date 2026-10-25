package jp.co.soramitsu.wallet.impl.viewmodels

import android.graphics.drawable.PictureDrawable
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.SwipeableState
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import co.jp.soramitsu.walletconnect.domain.WalletConnectInteractor
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.verify
import jp.co.soramitsu.account.api.domain.PendulumPreInstalledAccountsScenario
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.interfaces.NomisScoreInteractor
import jp.co.soramitsu.account.api.domain.interfaces.TotalBalanceUseCase
import jp.co.soramitsu.account.api.domain.model.LightMetaAccount
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.model.NomisScoreData
import jp.co.soramitsu.account.api.domain.model.TotalBalance
import jp.co.soramitsu.account.api.domain.model.PortfolioBalance
import jp.co.soramitsu.wallet.impl.domain.model.AssetAmountState
import jp.co.soramitsu.wallet.impl.domain.model.FiatValuationContext
import jp.co.soramitsu.wallet.impl.domain.model.asAssetAmountState
import jp.co.soramitsu.common.data.network.coingecko.FiatCurrency
import jp.co.soramitsu.androidfoundation.coroutine.CoroutineManager
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.AddressModel
import jp.co.soramitsu.common.address.createAddressModel
import jp.co.soramitsu.common.compose.component.ActionItemType
import jp.co.soramitsu.common.compose.component.ChainSelectorViewStateWithFilters
import jp.co.soramitsu.common.compose.component.SwipeState
import jp.co.soramitsu.common.domain.GetAvailableFiatCurrencies
import jp.co.soramitsu.common.domain.SelectedFiat
import jp.co.soramitsu.common.model.AssetDiscoveryCoverage
import jp.co.soramitsu.common.resources.ClipboardManager
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.model.NetworkScanKey
import jp.co.soramitsu.common.model.UniversalWalletRegistry
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.nft.domain.NFTInteractor
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.tonconnect.api.domain.TonConnectInteractor
import jp.co.soramitsu.wallet.impl.domain.ChainInteractor
import jp.co.soramitsu.wallet.impl.domain.CurrentAccountAddressUseCase
import jp.co.soramitsu.wallet.impl.domain.SendPausePolicy
import jp.co.soramitsu.wallet.impl.domain.SendPausedException
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.domain.model.AssetWithStatus
import jp.co.soramitsu.wallet.impl.domain.model.Token
import jp.co.soramitsu.common.data.storage.UpdateSecurityNoticeTracker
import jp.co.soramitsu.wallet.impl.data.network.blockchain.balance.InMemoryPreferences
import jp.co.soramitsu.wallet.impl.data.network.blockchain.balance.NetworkScanStateStore
import jp.co.soramitsu.common.model.AssetMetadataDescriptorStore
import jp.co.soramitsu.common.data.network.config.ProductFeatureToggleStore
import jp.co.soramitsu.wallet.impl.presentation.AssetPayload
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.wallet.impl.presentation.balance.list.AssetsLoadingState
import jp.co.soramitsu.wallet.impl.presentation.balance.list.BalanceListViewModel
import jp.co.soramitsu.wallet.impl.presentation.balance.list.PortfolioPresentationPreferences
import jp.co.soramitsu.wallet.impl.presentation.bundledPortfolioVisibility
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.wallet.impl.presentation.balance.list.WalletAssetsState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.wallet.impl.domain.model.Asset as WalletAsset

@OptIn(ExperimentalCoroutinesApi::class)
class BalanceListViewModelTests {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var walletInteractor: WalletInteractor

    @MockK
    private lateinit var chainInteractor: ChainInteractor

    @MockK
    private lateinit var addressIconGenerator: AddressIconGenerator

    @MockK
    private lateinit var walletRouter: WalletRouter

    @MockK
    private lateinit var getAvailableFiatCurrencies: GetAvailableFiatCurrencies

    @MockK
    private lateinit var selectedFiat: SelectedFiat

    @MockK
    private lateinit var accountInteractor: AccountInteractor

    @MockK
    private lateinit var nomisScoreInteractor: NomisScoreInteractor

    @MockK
    private lateinit var resourceManager: ResourceManager

    @MockK
    private lateinit var clipboardManager: ClipboardManager

    @MockK
    private lateinit var currentAccountAddressUseCase: CurrentAccountAddressUseCase

    @MockK
    private lateinit var totalBalanceUseCase: TotalBalanceUseCase

    @MockK
    private lateinit var pendulumPreInstalledAccountsScenario: PendulumPreInstalledAccountsScenario

    @MockK
    private lateinit var nFTInteractor: NFTInteractor

    @MockK
    private lateinit var walletConnectInteractor: WalletConnectInteractor

    @MockK
    private lateinit var picture: PictureDrawable

    @MockK
    private lateinit var coroutineManager: CoroutineManager

    @MockK
    private lateinit var tonConnectInteractor: TonConnectInteractor

    @MockK
    private lateinit var networkScanStateStore: NetworkScanStateStore

    @MockK
    private lateinit var assetMetadataDescriptorStore: AssetMetadataDescriptorStore

    @MockK
    private lateinit var featureToggleStore: ProductFeatureToggleStore

    private val createdViewModels = mutableListOf<BalanceListViewModel>()

    @After
    fun disposeViewModels() {
        createdViewModels.forEach { it.viewModelScope.cancel() }
        // Default-dispatcher work can enqueue its final cancellation on Main after
        // one drain. Keep servicing Main until those jobs actually finish.
        runBlocking {
            withTimeout(5000) {
                while (createdViewModels.any { !it.viewModelScope.coroutineContext.job.isCompleted }) {
                    mainCoroutineRule.dispatcher.scheduler.runCurrent()
                    kotlinx.coroutines.delay(10)
                }
            }
        }
    }

    private lateinit var vm: BalanceListViewModel
    private var bitcoinSendEnabled = true
    private val sendPausePolicy = SendPausePolicy(
        bitcoinSendEnabled = { bitcoinSendEnabled },
        solanaSendEnabled = { true },
        pausedErrorFactory = { SendPausedException("Sending paused", "Sending is paused") }
    )
    private lateinit var recoveryRequiredFlow: MutableStateFlow<Boolean>
    private val portfolioVisibility = bundledPortfolioVisibility()
    private val portfolioPreferencesStore = InMemoryPreferences()
    private val noticePreferences = InMemoryPreferences()
    private lateinit var portfolioViewModel: BalanceListViewModel

    @OptIn(ExperimentalStdlibApi::class)
    @Before
    fun setUp() = runTest {
        every { resourceManager.getString(any()) } answers { "text-${firstArg<Int>()}" }
        every { resourceManager.getString(any(), *anyVararg()) } answers { "text-${firstArg<Int>()}" }
        every { resourceManager.getQuantityString(any(), any(), *anyVararg()) } answers { "text-${firstArg<Int>()}-${secondArg<Int>()}" }
        every { coroutineManager.io } returns this.coroutineContext[CoroutineDispatcher]!!
        every { coroutineManager.default } returns this.coroutineContext[CoroutineDispatcher]!!
        coEvery { chainInteractor.getChainAssets() } returns listOf(createAsset())
        every { walletInteractor.assetsFlowAndAccount() } returns flowOf(123L to emptyList())
        every { chainInteractor.getChainsFlow() } returns flowOf(emptyList())
        every { walletInteractor.selectedMetaAccountFlow() } returns flowOf(createMetaAccount())
        every { walletInteractor.observeSelectedAccountChainSelectFilter() } returns flowOf(ChainSelectorViewStateWithFilters.Filter.All)
        every { nFTInteractor.nftFiltersFlow() } returns flowOf(emptyMap())
        every { nFTInteractor.collectionsFlow(any(), any()) } returns flowOf(emptySequence())
        every { walletInteractor.getAssetManagementIntroPassed() } returns true
        coEvery { walletInteractor.saveAssetManagementIntroPassed() } just runs
        every { walletInteractor.selectedLightMetaAccountFlow() } returns flowOf(
            createLightMetaAccount()
        )
        every { accountInteractor.selectedLightMetaAccountFlow() } returns flowOf(
            createLightMetaAccount()
        )
        recoveryRequiredFlow = MutableStateFlow(false)
        every {
            accountInteractor.walletRecoveryRequiredFlow(any())
        } returns recoveryRequiredFlow
        coEvery { accountInteractor.selectedLightMetaAccount() } returns createLightMetaAccount()
        coEvery { accountInteractor.lightMetaAccountsFlow() } returns flowOf(
            listOf(createLightMetaAccount())
        )
        coEvery { currentAccountAddressUseCase.invoke(any()) } returns ""
        every { totalBalanceUseCase.observe(any()) } returns flowOf(
            portfolioTotal()
        )
        every { nomisScoreInteractor.observeCurrentAccountScore() } returns flowOf(createNomis())
        every { walletInteractor.networkIssuesFlow() } returns flowOf(emptyMap())
        every { networkScanStateStore.states } returns MutableStateFlow(emptyMap())
        every { assetMetadataDescriptorStore.get(any()) } returns null
        every { featureToggleStore.assetDiscoveryShadowEnabled } returns false
        every { selectedFiat.flow() } returns flowOf("selected fiat")
        every { getAvailableFiatCurrencies.flow() } returns flowOf(emptyList())
        every { walletRouter.chainSelectorPayloadFlow } returns flowOf("")
        coEvery { walletInteractor.getSelectedMetaAccount() } returns createMetaAccount()
        every { walletInteractor.saveChainId(any(), any()) } just runs
        every { pendulumPreInstalledAccountsScenario.isPendulumMode(any()) } returns false
        coEvery { walletInteractor.getSavedChainId(any()) } returns ""

        mockkStatic("jp.co.soramitsu.common.address.AddressIconGeneratorKt")
        coEvery {
            addressIconGenerator.createAddressModel(
                accountAddress = any(),
                sizeInDp = any(),
                accountName = any(),
            )
        } returns AddressModel("", picture, "")
        coEvery {
            addressIconGenerator.createAddressIcon(
                accountId = any(),
                sizeInDp = any(),
                backgroundColorRes = any(),
            )
        } returns picture
//        mockkObject(BalanceUpdateTrigger)
//        every { BalanceUpdateTrigger.observe() } returns flowOf("")
        vm = createViewModel(networkScanStateStore)
    }

    private fun createViewModel(scanStateStore: NetworkScanStateStore) = BalanceListViewModel(
            interactor = walletInteractor,
            chainInteractor = chainInteractor,
            addressIconGenerator = addressIconGenerator,
            router = walletRouter,
            getAvailableFiatCurrencies = getAvailableFiatCurrencies,
            selectedFiat = selectedFiat,
            accountInteractor = accountInteractor,
            nomisScoreInteractor = nomisScoreInteractor,
            resourceManager = resourceManager,
            clipboardManager = clipboardManager,
            currentAccountAddress = currentAccountAddressUseCase,
            getTotalBalance = totalBalanceUseCase,
            pendulumPreInstalledAccountsScenario = pendulumPreInstalledAccountsScenario,
            nftInteractor = nFTInteractor,
            walletConnectInteractor = walletConnectInteractor,
            coroutineManager = coroutineManager,
            tonConnectInteractor = tonConnectInteractor,
            networkScanStateStore = scanStateStore,
            assetMetadataDescriptorStore = assetMetadataDescriptorStore,
            featureToggleStore = featureToggleStore,
            sendPausePolicy = sendPausePolicy,
            portfolioVisibility = portfolioVisibility,
            tokenGroupRegistry = mockk {
                every { observeResolvedGroups() } returns flowOf(jp.co.soramitsu.runtime.multiNetwork.tokengroups.ResolvedTokenGroups.EMPTY)
                every { resolve(any()) } returns jp.co.soramitsu.runtime.multiNetwork.tokengroups.ResolvedTokenGroups.EMPTY
            },
            portfolioPreferences = PortfolioPresentationPreferences(portfolioPreferencesStore),
            updateSecurityNotice = UpdateSecurityNoticeTracker(noticePreferences)
        ).also { createdViewModels += it }

    @Test
    fun `update security notice shows once and not after it was closed`() = runTest {
        // Nothing decided yet: the setUp view model shows nothing.
        assertNull(vm.showUpdateSecurityNotice.value)

        UpdateSecurityNoticeTracker(noticePreferences).recordStartup(hadStoredWallets = true)
        val firstLaunch = createViewModel(networkScanStateStore)
        assertNotNull(firstLaunch.showUpdateSecurityNotice.value?.getContentIfNotHandled())

        firstLaunch.onUpdateSecurityNoticeClosed()
        assertNull(createViewModel(networkScanStateStore).showUpdateSecurityNotice.value)
    }

    @Test
    fun `update security notice that was not closed shows at the next launch`() = runTest {
        UpdateSecurityNoticeTracker(noticePreferences).recordStartup(hadStoredWallets = true)
        createViewModel(networkScanStateStore)

        assertNotNull(createViewModel(networkScanStateStore).showUpdateSecurityNotice.value)
    }

    @Test
    fun `fresh install never shows the update security notice`() = runTest {
        UpdateSecurityNoticeTracker(noticePreferences).recordStartup(hadStoredWallets = false)

        assertNull(createViewModel(networkScanStateStore).showUpdateSecurityNotice.value)
    }

    @Test
    fun `default state check`() = runTest {
        advanceUntilIdle()
        val state = vm.state.value

        assertEquals(true, state.isBackedUp)
    }

    @Test
    fun `runtime quarantine updates recovery state without selected wallet emission`() = runTest {
        advanceUntilIdle()
        assertFalse(vm.state.value.isRecoveryRequired)

        recoveryRequiredFlow.value = true
        advanceUntilIdle()

        assertTrue(vm.state.value.isRecoveryRequired)
    }

    @Test
    fun `recreated scan store reaches portfolio and retains inactive network state`() = runTest {
        val walletId = 12L
        val chainId = "restored-chain"
        val inactiveChainId = "disabled.network"
        val asset = createAsset().copy(
            id = "restored-asset",
            name = "Restored asset",
            symbol = "RST",
            chainId = chainId,
            chainName = "Restored network",
            precision = 0,
            currencyId = "restored-asset",
            isTestNet = false
        )
        val chain = createChain(chainId, asset)
        val metaAccount = createMetaAccount(walletId)
        val lightMetaAccount = createLightMetaAccount(walletId)

        every { walletInteractor.assetsFlowAndAccount() } returns
            flowOf(walletId to listOf(createWalletAsset(walletId, asset)))
        every { chainInteractor.getChainsFlow() } returns flowOf(listOf(chain))
        coEvery { chainInteractor.getChainAssets() } returns listOf(asset)
        every { walletInteractor.selectedMetaAccountFlow() } returns flowOf(metaAccount)
        every { walletInteractor.selectedLightMetaAccountFlow() } returns flowOf(lightMetaAccount)
        every { accountInteractor.selectedLightMetaAccountFlow() } returns flowOf(lightMetaAccount)
        coEvery { accountInteractor.selectedLightMetaAccount() } returns lightMetaAccount
        coEvery { accountInteractor.lightMetaAccountsFlow() } returns flowOf(listOf(lightMetaAccount))
        coEvery { walletInteractor.getSelectedMetaAccount() } returns metaAccount
        coEvery { walletInteractor.getSavedChainId(walletId) } returns null
        every { walletRouter.chainSelectorPayloadFlow } returns emptyFlow()

        val preferences = InMemoryPreferences()
        NetworkScanStateStore(preferences).apply {
            scanStarted(walletId, chainId, AssetDiscoveryCoverage.CatalogOnly, attemptedAtMillis = 10)
            scanSucceeded(walletId, chainId, AssetDiscoveryCoverage.CatalogOnly, succeededAtMillis = 20)
            scanFailed(
                walletId = walletId,
                chainId = chainId,
                coverage = AssetDiscoveryCoverage.CatalogOnly,
                errorMessage = "restored endpoint error",
                failedAtMillis = 30
            )
            scanStarted(
                walletId,
                inactiveChainId,
                AssetDiscoveryCoverage.Limited,
                attemptedAtMillis = 15
            )
            scanSucceeded(
                walletId,
                inactiveChainId,
                AssetDiscoveryCoverage.Limited,
                succeededAtMillis = 25
            )
            scanFailed(
                walletId = walletId,
                chainId = inactiveChainId,
                coverage = AssetDiscoveryCoverage.Limited,
                errorMessage = "network disabled",
                failedAtMillis = 40
            )
        }

        val restoredStore = NetworkScanStateStore(preferences)
        val restoredViewModel = createViewModel(restoredStore)
        advanceUntilIdle()

        val assetsState = restoredViewModel.state.value.assetsState as WalletAssetsState.Assets
        val restoredAsset = (assetsState.assets as AssetsLoadingState.Loaded).assets.single()
        assertEquals(20L, restoredAsset.networkLastSuccessMillis)
        assertTrue(restoredAsset.networkIsStale)
        assertEquals("restored endpoint error", restoredAsset.networkSyncError)
        assertEquals(AssetDiscoveryCoverage.CatalogOnly.name, restoredAsset.networkScanCoverage)

        val inactive = restoredStore.states.value.getValue(NetworkScanKey(walletId, inactiveChainId))
        assertEquals(AssetDiscoveryCoverage.Limited, inactive.coverage)
        assertEquals(40L, inactive.lastAttemptMillis)
        assertEquals(25L, inactive.lastSuccessMillis)
        assertTrue(inactive.isStale)
        assertEquals("network disabled", inactive.errorMessage)
    }

    @OptIn(ExperimentalMaterialApi::class)
    @Test
    fun `swipe send on a vetoed bitcoin row shows the paused sheet instead of the send flow`() = runTest {
        bitcoinSendEnabled = false
        val bitcoin = createChain(UniversalWalletRegistry.bitcoinMainnet.id, createAsset())
        val payload = AssetPayload(bitcoin.id, "BTC")
        coEvery { walletInteractor.getChain(bitcoin.id) } returns bitcoin
        every { resourceManager.getString(any()) } returns "Sending paused"
        every { resourceManager.getString(any(), *anyVararg()) } returns "Sending on Bitcoin is paused"
        every { walletRouter.openReceive(any()) } just runs
        advanceUntilIdle()

        vm.actionItemClicked(ActionItemType.SEND, payload.chainId, payload.chainAssetId, mockk<SwipeableState<SwipeState>>(relaxed = true))
        advanceUntilIdle()

        val dialog = requireNotNull(vm.errorDialogStateLiveData.value).peekContent()
        assertEquals("Sending paused", dialog.title)
        verify(exactly = 0) { walletRouter.openSend(any(), any(), any(), any()) }
        dialog.positiveClick()
        verify { walletRouter.openReceive(payload) }
    }

    @Test
    fun `paused group action rechecks the original wallet before Receive`() = runTest {
        bitcoinSendEnabled = false
        val bitcoinId = UniversalWalletRegistry.bitcoinMainnet.id
        val btc = portfolioAsset("btc", "BTC", bitcoinId, isUtility = true, priceId = "bitcoin")
        val bitcoin = portfolioChain(bitcoinId, rank = 1, btc)
        loadedPortfolio(listOf(bitcoin), listOf(portfolioRow(btc, BigInteger.TEN, enabled = true)))
        coEvery { walletInteractor.getChain(bitcoinId) } returns bitcoin
        every { resourceManager.getString(any()) } returns "Sending paused"
        every { resourceManager.getString(any(), *anyVararg()) } returns "Sending on Bitcoin is paused"
        every { walletRouter.openReceive(any()) } just runs
        portfolioViewModel.onPortfolioMemberAction(WALLET_ID, bitcoinId, "btc", ActionItemType.SEND)
        advanceUntilIdle()
        val dialog = requireNotNull(portfolioViewModel.errorDialogStateLiveData.value).peekContent()
        coEvery { walletInteractor.getSelectedMetaAccount() } returns createMetaAccount(WALLET_ID + 1)
        dialog.positiveClick()
        advanceUntilIdle()
        verify(exactly = 0) { walletRouter.openReceive(any()) }
        coEvery { walletInteractor.getSelectedMetaAccount() } returns createMetaAccount(WALLET_ID)
        dialog.positiveClick()
        advanceUntilIdle()
        verify { walletRouter.openReceive(AssetPayload(bitcoinId, "btc")) }
    }

    @OptIn(ExperimentalMaterialApi::class)
    @Test
    fun `swipe send opens the send flow when no veto applies`() = runTest {
        val bitcoin = createChain(UniversalWalletRegistry.bitcoinMainnet.id, createAsset())
        val payload = AssetPayload(bitcoin.id, "BTC")
        coEvery { walletInteractor.getChain(bitcoin.id) } returns bitcoin
        every { walletRouter.openSend(any(), any(), any(), any()) } just runs
        advanceUntilIdle()

        vm.actionItemClicked(ActionItemType.SEND, payload.chainId, payload.chainAssetId, mockk<SwipeableState<SwipeState>>(relaxed = true))
        advanceUntilIdle()

        verify { walletRouter.openSend(payload, any(), any(), any()) }
    }

    @Test
    fun `explicit shown zero row is listed and an auto zero non-default row is not`() = runTest {
        val shownSecondary = portfolioAsset("shown-secondary", "SEC", TOKEN_CHAIN_ID)
        val autoUtility = portfolioAsset("auto-utility", "TKN", TOKEN_CHAIN_ID, isUtility = true)
        val tokenChain = portfolioChain(TOKEN_CHAIN_ID, rank = null, autoUtility, shownSecondary)

        val loaded = loadedPortfolio(
            chains = listOf(tokenChain),
            assets = listOf(
                portfolioRow(shownSecondary, free = BigInteger.ZERO, enabled = true),
                portfolioRow(autoUtility, free = BigInteger.ZERO, enabled = null)
            )
        )

        assertEquals(listOf("shown-secondary"), loaded.assets.map { it.chainAssetId })
    }

    @Test
    fun `zero-balance auto utility of a ranked mainnet is listed`() = runTest {
        val dot = portfolioAsset(DOT_ASSET_ID, "DOT", POLKADOT_CHAIN_ID, isUtility = true, priceId = "polkadot")

        val loaded = loadedPortfolio(
            chains = listOf(portfolioChain(POLKADOT_CHAIN_ID, rank = 8, dot)),
            assets = listOf(portfolioRow(dot, free = BigInteger.ZERO, enabled = null))
        )

        assertEquals(listOf(DOT_ASSET_ID), loaded.assets.map { it.chainAssetId })
        assertFalse(loaded.walletHasPositiveBalance)
    }

    @Test
    fun `hidden funded row is excluded from list and subtotal and loaded state reports it`() = runTest {
        val dot = portfolioAsset(DOT_ASSET_ID, "DOT", POLKADOT_CHAIN_ID, isUtility = true, priceId = "polkadot")
        val hiddenToken = portfolioAsset("hidden-token", "HID", POLKADOT_CHAIN_ID, priceId = "hidden")
        val polkadot = portfolioChain(POLKADOT_CHAIN_ID, rank = 8, dot, hiddenToken)

        val loaded = loadedPortfolio(
            chains = listOf(polkadot),
            assets = listOf(
                portfolioRow(dot, free = BigInteger.TEN, enabled = null, rate = "5"),
                portfolioRow(hiddenToken, free = BigInteger.valueOf(100), enabled = false, rate = "1")
            )
        )

        assertEquals(listOf(DOT_ASSET_ID), loaded.assets.map { it.chainAssetId })
        assertEquals(BigDecimal("50").formatFiat("$"), loaded.assets.single().networkFiatSubtotal)
        assertEquals(
            setOf(PortfolioPresentationPreferences.assetKey(POLKADOT_CHAIN_ID, "hidden-token")),
            loaded.exclusions.hiddenFundedAssetKeys
        )
        assertEquals(1, loaded.hiddenFundedNoticeCount)
        assertTrue(loaded.walletHasPositiveBalance)
    }

    @Test
    fun `what the total leaves out is wallet-wide, whatever network the list is filtered to`() = runTest {
        val dot = portfolioAsset(DOT_ASSET_ID, "DOT", POLKADOT_CHAIN_ID, isUtility = true, priceId = "polkadot")
        val hiddenToken = portfolioAsset("hidden-token", "HID", POLKADOT_CHAIN_ID, priceId = "hidden")
        val unpriced = portfolioAsset("unpriced", "UNP", POLKADOT_CHAIN_ID)
        val tokenUtility = portfolioAsset("token-utility", "TKN", TOKEN_CHAIN_ID, isUtility = true, priceId = "token")
        val chains = listOf(
            portfolioChain(POLKADOT_CHAIN_ID, rank = 8, dot, hiddenToken, unpriced).copy(supportStakingPool = true),
            portfolioChain(TOKEN_CHAIN_ID, rank = null, tokenUtility)
        )

        val loaded = loadedPortfolio(
            chains = chains,
            assets = listOf(
                portfolioRow(dot, free = BigInteger.TEN, enabled = null, rate = "5"),
                portfolioRow(hiddenToken, free = BigInteger.valueOf(100), enabled = false, rate = "1"),
                portfolioRow(unpriced, free = BigInteger.valueOf(7), enabled = null),
                portfolioRow(tokenUtility, free = BigInteger.ONE, enabled = null, rate = "2")
            ),
            selectedChainId = TOKEN_CHAIN_ID
        )

        assertEquals(listOf("token-utility"), loaded.assets.map { it.chainAssetId })
        assertEquals(1, loaded.exclusions.hiddenFundedCount)
        assertEquals(1, loaded.exclusions.unpricedFundedCount)
        assertTrue("DOT is held on a network with nomination pools", loaded.exclusions.poolStakesExcluded)
    }

    /** Shared contract §10: a listed asset with a balance and a registry price adds 0 fiat. */
    @Test
    fun `an asset on the bundled untrusted list shows no price and is counted as without price`() = runTest {
        val ksm = portfolioAsset(KSM_ASSET_ID, "KSM", KUSAMA_ASSET_HUB_CHAIN_ID, isUtility = true, priceId = "kusama")
        val thirdPartyUsdt = portfolioAsset(KUSAMA_ASSET_HUB_THIRD_PARTY_USDT, "USDT", KUSAMA_ASSET_HUB_CHAIN_ID, priceId = "tether")

        val loaded = loadedPortfolio(
            chains = listOf(portfolioChain(KUSAMA_ASSET_HUB_CHAIN_ID, rank = null, ksm, thirdPartyUsdt)),
            assets = listOf(
                portfolioRow(ksm, free = BigInteger.valueOf(2), enabled = null, rate = "10"),
                portfolioRow(thirdPartyUsdt, free = BigInteger.valueOf(500_000), enabled = null, rate = "1")
            )
        )

        val rows = loaded.assets.associateBy { it.chainAssetId }
        assertEquals(setOf(KSM_ASSET_ID, KUSAMA_ASSET_HUB_THIRD_PARTY_USDT), rows.keys)
        assertEquals(null, rows.getValue(KUSAMA_ASSET_HUB_THIRD_PARTY_USDT).assetTotalBalanceFiat)
        assertEquals("untrusted", rows.getValue(KUSAMA_ASSET_HUB_THIRD_PARTY_USDT).priceTrust)
        assertEquals(
            "the network subtotal is the KSM alone",
            BigDecimal("20").formatFiat("$"),
            rows.getValue(KSM_ASSET_ID).networkFiatSubtotal
        )
        assertEquals(1, loaded.exclusions.unpricedFundedCount)
    }

    /**
     * Upgrade from Play 4.2.0: it hid every empty asset of a funded wallet and never revealed a
     * later deposit, and database 81 keeps such a funded row hidden (enabled = 0).
     */
    @Test
    fun `hidden funds are announced once, and again only when another hidden asset holds funds`() = runTest {
        val dot = portfolioAsset(DOT_ASSET_ID, "DOT", POLKADOT_CHAIN_ID, isUtility = true, priceId = "polkadot")
        val hiddenBy420 = portfolioAsset("hidden-by-4-2-0", "OLD", POLKADOT_CHAIN_ID, priceId = "old")
        val laterDeposit = portfolioAsset("later-deposit", "NEW", POLKADOT_CHAIN_ID, priceId = "new")
        val polkadot = portfolioChain(POLKADOT_CHAIN_ID, rank = 8, dot, hiddenBy420, laterDeposit)
        val dotRow = portfolioRow(dot, free = BigInteger.TEN, enabled = null, rate = "5")
        val hiddenRow = portfolioRow(hiddenBy420, free = BigInteger.valueOf(3), enabled = false, rate = "1")
        val reviewedKey = PortfolioPresentationPreferences.assetKey(POLKADOT_CHAIN_ID, "hidden-by-4-2-0")

        val upgraded = loadedPortfolio(
            chains = listOf(polkadot),
            assets = listOf(dotRow, hiddenRow, portfolioRow(laterDeposit, free = BigInteger.ZERO, enabled = false))
        )
        assertEquals(1, upgraded.hiddenFundedNoticeCount)
        assertEquals(
            "the first list of this version records what earlier versions left hidden",
            setOf(reviewedKey, PortfolioPresentationPreferences.assetKey(POLKADOT_CHAIN_ID, "later-deposit")),
            portfolioPreferencesStore.getStringSet(PortfolioPresentationPreferences.legacyHiddenKey(WALLET_ID), emptySet())
        )

        every { walletRouter.openManageAssets("Hidden") } just runs
        portfolioViewModel.onHiddenFundsReviewClick()
        verify { walletRouter.openManageAssets("Hidden") }
        portfolioViewModel.onHiddenFundsKeepHiddenClick()
        advanceUntilIdle()
        assertTrue(portfolioPreferencesStore.getString(PortfolioPresentationPreferences.hiddenFundedNoticeKey(WALLET_ID))?.length == 64)

        val keptHidden = loadedPortfolio(
            chains = listOf(polkadot),
            assets = listOf(dotRow, hiddenRow, portfolioRow(laterDeposit, free = BigInteger.ZERO, enabled = false))
        )
        assertEquals(0, keptHidden.hiddenFundedNoticeCount)
        assertEquals("it is still left out of the total and still labelled", 1, keptHidden.exclusions.hiddenFundedCount)

        val afterDeposit = loadedPortfolio(
            chains = listOf(polkadot),
            assets = listOf(dotRow, hiddenRow, portfolioRow(laterDeposit, free = BigInteger.valueOf(8), enabled = false, rate = "1"))
        )
        assertEquals(2, afterDeposit.hiddenFundedNoticeCount)
        assertEquals(2, afterDeposit.exclusions.hiddenFundedCount)
        assertEquals(
            "the legacy snapshot is written once",
            2,
            portfolioPreferencesStore.getStringSet(PortfolioPresentationPreferences.legacyHiddenKey(WALLET_ID), emptySet()).size
        )
    }

    @OptIn(ExperimentalMaterialApi::class)
    @Test
    fun `hiding a funded row is the user's own choice and is not announced as hidden funds`() = runTest {
        val dot = portfolioAsset(DOT_ASSET_ID, "DOT", POLKADOT_CHAIN_ID, isUtility = true, priceId = "polkadot")
        val token = portfolioAsset("token", "TKN", POLKADOT_CHAIN_ID, priceId = "token")
        val emptyShown = portfolioAsset("empty-shown", "EMP", POLKADOT_CHAIN_ID, priceId = "empty")
        coEvery { walletInteractor.markAssetAsHidden(any(), any()) } just runs
        loadedPortfolio(
            chains = listOf(portfolioChain(POLKADOT_CHAIN_ID, rank = 8, dot, token, emptyShown)),
            assets = listOf(
                portfolioRow(dot, free = BigInteger.TEN, enabled = null, rate = "5"),
                portfolioRow(token, free = BigInteger.valueOf(4), enabled = null, rate = "1"),
                portfolioRow(emptyShown, free = BigInteger.ZERO, enabled = true)
            )
        )

        portfolioViewModel.actionItemClicked(ActionItemType.HIDE, POLKADOT_CHAIN_ID, "token", mockk<SwipeableState<SwipeState>>(relaxed = true))
        portfolioViewModel.actionItemClicked(ActionItemType.HIDE, POLKADOT_CHAIN_ID, "empty-shown", mockk<SwipeableState<SwipeState>>(relaxed = true))
        advanceUntilIdle()

        coVerify { walletInteractor.markAssetAsHidden(POLKADOT_CHAIN_ID, "token") }
        coVerify { walletInteractor.markAssetAsHidden(POLKADOT_CHAIN_ID, "empty-shown") }
        assertEquals(
            "an asset hidden while empty is announced if it is funded later",
            setOf(PortfolioPresentationPreferences.assetKey(POLKADOT_CHAIN_ID, "token")),
            portfolioPreferencesStore.getStringSet(PortfolioPresentationPreferences.reviewedHiddenFundedKey(WALLET_ID), emptySet())
        )
    }

    @Test
    fun `funded testnet rows stay behind the Testnets toggle and never count`() = runTest {
        val dot = portfolioAsset(DOT_ASSET_ID, "DOT", POLKADOT_CHAIN_ID, isUtility = true, priceId = "polkadot")
        val wnd = portfolioAsset("wnd", "WND", WESTEND_CHAIN_ID, isUtility = true, priceId = "westend")
        val chains = listOf(
            portfolioChain(POLKADOT_CHAIN_ID, rank = 8, dot),
            portfolioChain(WESTEND_CHAIN_ID, rank = 108, wnd, isTestNet = true)
        )
        val rows = listOf(
            portfolioRow(dot, free = BigInteger.ZERO, enabled = null),
            portfolioRow(wnd, free = BigInteger.TEN, enabled = true, rate = "3")
        )

        val hiddenByDefault = loadedPortfolio(chains, rows)
        assertEquals(listOf(DOT_ASSET_ID), hiddenByDefault.assets.map { it.chainAssetId })
        assertFalse(hiddenByDefault.walletHasPositiveBalance)

        portfolioPreferencesStore.putBoolean(PortfolioPresentationPreferences.showTestnetsKey(WALLET_ID), true)
        val shown = loadedPortfolio(chains, rows)
        val westendRow = shown.assets.single { it.chainAssetId == "wnd" }
        assertTrue(westendRow.isTestnet)
        assertEquals("a testnet never has a subtotal", null, westendRow.networkFiatSubtotal)
        assertEquals("a testnet token has no value", null, westendRow.assetTotalBalanceFiat)
        assertFalse(shown.walletHasPositiveBalance)
    }

    @Test
    fun `swipe hide is offered with the All networks filter`() = runTest {
        val dot = portfolioAsset(DOT_ASSET_ID, "DOT", POLKADOT_CHAIN_ID, isUtility = true, priceId = "polkadot")
        loadedPortfolio(
            chains = listOf(portfolioChain(POLKADOT_CHAIN_ID, rank = 8, dot)),
            assets = listOf(portfolioRow(dot, free = BigInteger.TEN, enabled = null))
        )

        val assetsState = portfolioViewModel.state.value.assetsState as WalletAssetsState.Assets
        assertTrue(assetsState.isHideVisible)
    }

    @Test
    fun `header shows the portfolio balance and leaves hidden holdings out`() = runTest {
        every { totalBalanceUseCase.observe(any()) } returns flowOf(
            portfolioTotal(shown = BigDecimal("6"), hidden = BigDecimal("35"), hiddenCount = 2)
        )
        val viewModel = createViewModel(networkScanStateStore)
        advanceUntilIdle()

        assertEquals(BigDecimal("6").formatFiat("$"), viewModel.state.value.balance.transferableBalance)
    }

    /**
     * Upgrade from Play 4.2.0 (DB 75): it wrote enabled = 1 for funded and popular zero rows and
     * auto-hid every other zero row with enabled = 0, which database 81 (WP-3) returns to Auto.
     */
    @Test
    fun `a 4_2_0 wallet after the 81 migration shows its funded and popular rows and counts the same total`() = runTest {
        val dot = portfolioAsset(DOT_ASSET_ID, "DOT", POLKADOT_CHAIN_ID, isUtility = true, priceId = "polkadot")
        val ksm = portfolioAsset(KSM_ASSET_ID, "KSM", KUSAMA_CHAIN_ID, isUtility = true, priceId = "kusama")
        val formerAutoHidden = portfolioAsset("former-auto-hidden", "OLD", TOKEN_CHAIN_ID, priceId = "old")
        val tokenUtility = portfolioAsset("token-utility", "TKN", TOKEN_CHAIN_ID, isUtility = true)
        val deposit = portfolioAsset("later-deposit", "NEW", TOKEN_CHAIN_ID, priceId = "new")
        val chains = listOf(
            portfolioChain(POLKADOT_CHAIN_ID, rank = 8, dot),
            portfolioChain(KUSAMA_CHAIN_ID, rank = 9, ksm),
            portfolioChain(TOKEN_CHAIN_ID, rank = null, tokenUtility, formerAutoHidden, deposit)
        )
        val rows = listOf(
            portfolioRow(dot, free = BigInteger.TEN, enabled = true, rate = "5"), // funded, 4.2.0 wrote 1
            portfolioRow(ksm, free = BigInteger.ZERO, enabled = true), // popular zero, 4.2.0 wrote 1
            portfolioRow(formerAutoHidden, free = BigInteger.ZERO, enabled = null), // 0 -> Auto at 81
            portfolioRow(tokenUtility, free = BigInteger.ZERO, enabled = null), // 0 -> Auto at 81
            portfolioRow(deposit, free = BigInteger.valueOf(4), enabled = null, rate = "2") // was 0, funded later
        )

        val loaded = loadedPortfolio(chains, rows)

        assertEquals(
            setOf(DOT_ASSET_ID, KSM_ASSET_ID, "later-deposit"),
            loaded.assets.map { it.chainAssetId }.toSet()
        )
        assertTrue("nothing is left out of the total", loaded.exclusions.isEmpty)
        assertEquals(0, loaded.hiddenFundedNoticeCount)
        val subtotals = loaded.assets.associate { it.chainAssetId to it.networkFiatSubtotal }
        assertEquals(BigDecimal("50").formatFiat("$"), subtotals[DOT_ASSET_ID])
        assertEquals(BigDecimal("8").formatFiat("$"), subtotals["later-deposit"])
    }

    @Test
    fun `portfolio lens is saved only for the displayed wallet and restored when the wallet changes`() = runTest {
        val dot = portfolioAsset(DOT_ASSET_ID, "DOT", POLKADOT_CHAIN_ID, isUtility = true, priceId = "polkadot")
        val chains = listOf(portfolioChain(POLKADOT_CHAIN_ID, rank = 8, dot))
        val rows = listOf(portfolioRow(dot, free = BigInteger.TEN, enabled = null))
        val byNetwork = jp.co.soramitsu.wallet.impl.presentation.balance.list.PortfolioLens.ByNetwork
        val byToken = jp.co.soramitsu.wallet.impl.presentation.balance.list.PortfolioLens.ByToken
        assertEquals(byToken, loadedPortfolio(chains, rows).portfolioUi.lens)
        portfolioViewModel.onPortfolioLensChanged(byNetwork)
        advanceUntilIdle()
        assertEquals(byNetwork, loadedPortfolio(chains, rows).portfolioUi.lens)
        val other = WALLET_ID + 1
        assertEquals(byToken, loadedPortfolio(chains, rows, walletId = other).portfolioUi.lens)
        portfolioViewModel.onPortfolioLensChanged(byToken)
        advanceUntilIdle()
        assertEquals(byNetwork, loadedPortfolio(chains, rows).portfolioUi.lens)
    }

    private fun kotlinx.coroutines.test.TestScope.loadedPortfolio(
        chains: List<Chain>,
        assets: List<AssetWithStatus>,
        selectedChainId: String? = null,
        walletId: Long = WALLET_ID
    ): AssetsLoadingState.Loaded {
        val metaAccount = createMetaAccount(walletId)
        val lightMetaAccount = createLightMetaAccount(walletId)
        every { walletInteractor.assetsFlowAndAccount() } returns flowOf(walletId to assets)
        every { chainInteractor.getChainsFlow() } returns flowOf(chains)
        coEvery { chainInteractor.getChainAssets() } returns chains.flatMap { it.assets }
        every { walletInteractor.selectedMetaAccountFlow() } returns flowOf(metaAccount)
        every { walletInteractor.selectedLightMetaAccountFlow() } returns flowOf(lightMetaAccount)
        every { accountInteractor.selectedLightMetaAccountFlow() } returns flowOf(lightMetaAccount)
        coEvery { accountInteractor.selectedLightMetaAccount() } returns lightMetaAccount
        coEvery { accountInteractor.lightMetaAccountsFlow() } returns flowOf(listOf(lightMetaAccount))
        coEvery { walletInteractor.getSelectedMetaAccount() } returns metaAccount
        coEvery { walletInteractor.getSavedChainId(walletId) } returns selectedChainId
        every { walletRouter.chainSelectorPayloadFlow } returns emptyFlow()

        portfolioViewModel = createViewModel(networkScanStateStore)
        advanceUntilIdle()

        val assetsState = portfolioViewModel.state.value.assetsState as WalletAssetsState.Assets
        return assetsState.assets as AssetsLoadingState.Loaded
    }

    private fun portfolioAsset(
        id: String,
        symbol: String,
        chainId: String,
        isUtility: Boolean = false,
        priceId: String? = null
    ) = createAsset().copy(
        id = id,
        name = symbol,
        symbol = symbol,
        chainId = chainId,
        chainName = chainId,
        isTestNet = false,
        priceId = priceId,
        precision = 0,
        isUtility = isUtility,
        type = ChainAssetType.Normal,
        currencyId = id
    )

    private fun portfolioChain(
        chainId: String,
        rank: Int?,
        vararg assets: Asset,
        isTestNet: Boolean = false
    ) = createChain(chainId, assets.first()).copy(
        rank = rank,
        name = chainId,
        assets = assets.toList(),
        isTestNet = isTestNet
    )

    private fun portfolioRow(
        asset: Asset,
        free: BigInteger,
        enabled: Boolean?,
        rate: String? = null
    ) = createWalletAsset(WALLET_ID, asset).let { row ->
        row.copy(
            asset = row.asset.copy(
                token = Token(asset, rate?.let(::BigDecimal).asAssetAmountState(), AssetAmountState.Known(BigDecimal.ZERO),
                    FiatValuationContext("usd", FiatCurrency("usd", "$", "Dollar", "")), "usd", asset.priceId),
                freeInPlanksState = AssetAmountState.Known(free),
                enabled = enabled
            )
        )
    }

    private fun portfolioTotal(shown: BigDecimal = BigDecimal.ZERO, hidden: BigDecimal = BigDecimal.ZERO, hiddenCount: Int = 0): TotalBalance.Ready {
        fun holdings(amount: BigDecimal, count: Int) = PortfolioBalance(amount, amount, BigDecimal.ZERO, BigDecimal.ZERO, count, count, 0, 0)
        val shownCount = if (shown.signum() > 0) 1 else 0
        return TotalBalance.Ready(createMetaAccount(), emptyMap(), "usd", "$", true,
            holdings(shown, shownCount), holdings(hidden, hiddenCount), holdings(shown + hidden, shownCount + hiddenCount))
    }

    private fun createNomis() = NomisScoreData(
        metaId = 1,
        score = 3,
        updated = 4,
        nativeBalanceUsd = BigDecimal.ZERO,
        holdTokensUsd = BigDecimal.ZERO,
        walletAgeInMonths = 4,
        totalTransactions = 9,
        rejectedTransactions = 5,
        avgTransactionTimeInHours = 8.3,
        maxTransactionTimeInHours = 4.7,
        minTransactionTimeInHours = 3.7,
        scoredAt = 33,
    )

    private fun createLightMetaAccount(id: Long = 13) = LightMetaAccount(
        id = id,
        substratePublicKey = ByteArray(32),
        substrateCryptoType = CryptoType.ECDSA,
        substrateAccountId = ByteArray(32),
        ethereumAddress = ByteArray(32),
        ethereumPublicKey = ByteArray(32),
        tonPublicKey = null,
        isSelected = true,
        name = "name",
        isBackedUp = true,
        initialized = true,
    )

    private fun createMetaAccount(id: Long = 12) = MetaAccount(
        id = id,
        chainAccounts = emptyMap(),
        favoriteChains = emptyMap(),
        substratePublicKey = ByteArray(32),
        substrateCryptoType = CryptoType.ECDSA,
        substrateAccountId = ByteArray(32),
        ethereumAddress = ByteArray(32),
        ethereumPublicKey = ByteArray(32),
        tonPublicKey = ByteArray(32),
        isSelected = false,
        isBackedUp = true,
        googleBackupAddress = "",
        name = "",
        initialized = true,
    )

    private fun createAsset() = Asset(
        id = "0x0200000000000000000000000000000000000000000000000000000000000000",
        name = "name",
        symbol = "symbol",
        iconUrl = "icon url",
        chainId = "chain id",
        chainName = "chain name",
        chainIcon = "chain icon",
        isTestNet = true,
        priceId = "price id",
        precision = 18,
        staking = Asset.StakingType.RELAYCHAIN,
        purchaseProviders = emptyList(),
        supportStakingPool = false,
        isUtility = true,
        type = ChainAssetType.SoraAsset,
        currencyId = "0x0200000000000000000000000000000000000000000000000000000000000000",
        existentialDeposit = "ex dep",
        color = "color",
        isNative = false,
    )

    private fun createWalletAsset(walletId: Long, asset: Asset) = AssetWithStatus(
        asset = WalletAsset(
            metaId = walletId,
            token = Token(asset, fiatRate = null, fiatSymbol = "$", recentRateChange = null),
            accountId = byteArrayOf(1),
            freeInPlanks = BigInteger.TEN,
            reservedInPlanks = BigInteger.ZERO,
            miscFrozenInPlanks = BigInteger.ZERO,
            feeFrozenInPlanks = BigInteger.ZERO,
            bondedInPlanks = null,
            redeemableInPlanks = null,
            unbondingInPlanks = null,
            sortIndex = 0,
            enabled = null,
            minSupportedVersion = null,
            chainAccountName = "restored account",
            markedNotNeed = false,
            status = null
        ),
        hasAccount = true,
        hasChainAccount = true
    )

    private fun createChain(chainId: String, asset: Asset) = Chain(
        id = chainId,
        paraId = null,
        rank = null,
        name = "Restored network",
        minSupportedVersion = null,
        assets = listOf(asset),
        nodes = emptyList(),
        explorers = emptyList(),
        externalApi = null,
        icon = "",
        addressPrefix = 0,
        isEthereumBased = false,
        isTestNet = false,
        hasCrowdloans = false,
        parentId = null,
        supportStakingPool = false,
        isEthereumChain = false,
        chainlinkProvider = false,
        supportNft = false,
        isUsesAppId = false,
        identityChain = null,
        ecosystem = Ecosystem.Substrate,
        remoteAssetsSource = null
    )

    private companion object {
        const val WALLET_ID = 12L
        const val POLKADOT_CHAIN_ID = "91b171bb158e2d3848fa23a9f1c25182fb8e20313b2c1eb49219da7a70ce90c3"
        const val KUSAMA_CHAIN_ID = "b0a8d493285c2df73290dfb7e61f870f17b41801197a149ca93654499ea3dafe"
        const val WESTEND_CHAIN_ID = "e143f23803ac50e8f6f8e62695d1ce9e4e1d68aa36c1cd2cfd15340213f3423e"
        const val TOKEN_CHAIN_ID = "unranked-token-chain"
        const val DOT_ASSET_ID = "887a17c7-1370-4de0-97dd-5422e294fa75"
        const val KSM_ASSET_ID = "1e0c2ec6-935f-49bd-a854-5e12ee6c9f1b"
        const val KUSAMA_ASSET_HUB_CHAIN_ID = "48239ef607d7928874027a43a67689209727dfb3d3dc5e5b03a39bdc2eda771a"
        const val KUSAMA_ASSET_HUB_THIRD_PARTY_USDT = "27768227-8a9a-4443-a57d-6013c24f85e9"
    }
}
