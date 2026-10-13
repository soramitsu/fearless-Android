package jp.co.soramitsu.wallet.impl.viewmodels

import android.graphics.drawable.PictureDrawable
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import co.jp.soramitsu.walletconnect.domain.WalletConnectInteractor
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import io.mockk.just
import io.mockk.mockkStatic
import io.mockk.runs
import jp.co.soramitsu.account.api.domain.PendulumPreInstalledAccountsScenario
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.interfaces.NomisScoreInteractor
import jp.co.soramitsu.account.api.domain.interfaces.TotalBalanceUseCase
import jp.co.soramitsu.account.api.domain.model.LightMetaAccount
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.model.NomisScoreData
import jp.co.soramitsu.account.api.domain.model.TotalBalance
import jp.co.soramitsu.androidfoundation.coroutine.CoroutineManager
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.AddressModel
import jp.co.soramitsu.common.address.createAddressModel
import jp.co.soramitsu.common.compose.component.ChainSelectorViewStateWithFilters
import jp.co.soramitsu.common.domain.GetAvailableFiatCurrencies
import jp.co.soramitsu.common.domain.SelectedFiat
import jp.co.soramitsu.common.model.AssetDiscoveryCoverage
import jp.co.soramitsu.common.resources.ClipboardManager
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.model.NetworkScanKey
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.nft.domain.NFTInteractor
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.tonconnect.api.domain.TonConnectInteractor
import jp.co.soramitsu.wallet.impl.domain.ChainInteractor
import jp.co.soramitsu.wallet.impl.domain.CurrentAccountAddressUseCase
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.domain.model.AssetWithStatus
import jp.co.soramitsu.wallet.impl.domain.model.Token
import jp.co.soramitsu.wallet.impl.data.network.blockchain.balance.InMemoryPreferences
import jp.co.soramitsu.wallet.impl.data.network.blockchain.balance.NetworkScanStateStore
import jp.co.soramitsu.common.model.AssetMetadataDescriptorStore
import jp.co.soramitsu.common.data.network.config.ProductFeatureToggleStore
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.wallet.impl.presentation.balance.list.AssetsLoadingState
import jp.co.soramitsu.wallet.impl.presentation.balance.list.BalanceListViewModel
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

    private lateinit var vm: BalanceListViewModel
    private lateinit var recoveryRequiredFlow: MutableStateFlow<Boolean>

    @OptIn(ExperimentalStdlibApi::class)
    @Before
    fun setUp() = runTest {
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
            TotalBalance(
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "e",
            )
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
            featureToggleStore = featureToggleStore
        )

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
}
