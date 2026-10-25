package jp.co.soramitsu.wallet.impl.presentation.balance.searchAssets

import androidx.lifecycle.SavedStateHandle
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.compose.viewstate.AssetListItemViewState
import jp.co.soramitsu.common.data.network.config.ProductFeatureToggleStore
import jp.co.soramitsu.common.model.AssetMetadataDescriptorStore
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.impl.data.network.blockchain.balance.InMemoryPreferences
import jp.co.soramitsu.wallet.impl.domain.ChainInteractor
import jp.co.soramitsu.wallet.impl.domain.SendPausePolicy
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.domain.model.AssetWithStatus
import jp.co.soramitsu.wallet.impl.domain.model.Token
import jp.co.soramitsu.wallet.impl.presentation.AssetPayload
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.wallet.impl.presentation.balance.list.PortfolioPresentationPreferences
import jp.co.soramitsu.wallet.impl.presentation.bundledPortfolioVisibility
import java.math.BigInteger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import jp.co.soramitsu.wallet.impl.domain.model.Asset as WalletAsset

/**
 * Search lists by the portfolio's own rule (AND-G-03): sync stores every new row as Auto, so a
 * search that needed an explicit Shown was empty for new wallets and missed everything received
 * after the upgrade. A result opens the exact asset detail, never the old intermediate screen that
 * still required an explicit Shown (AND-V-02).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchAssetsViewModelTest {

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private val preferences = InMemoryPreferences()
    private val router = mockk<WalletRouter>(relaxed = true)

    private val dot = asset(DOT_ASSET_ID, "DOT", "Polkadot", POLKADOT_CHAIN_ID, isUtility = true, priceId = "polkadot")
    private val ksm = asset(KSM_ASSET_ID, "KSM", "Kusama", KUSAMA_CHAIN_ID, isUtility = true, priceId = "kusama")
    private val hubDot = asset(DOT_ASSET_ID, "DOT", "Polkadot", ASSET_HUB_CHAIN_ID, isUtility = true, priceId = "polkadot")
    private val hubUsdt = asset(USDT_ASSET_ID, "USDT", "Tether USD", ASSET_HUB_CHAIN_ID, priceId = "tether")
    private val hubUsdc = asset(USDC_ASSET_ID, "USDC", "USD Coin", ASSET_HUB_CHAIN_ID, priceId = "usd-coin")
    private val wnd = asset("wnd", "WND", "Westend", WESTEND_CHAIN_ID, isUtility = true)

    private val chains = listOf(
        chain(POLKADOT_CHAIN_ID, "Polkadot", rank = 8, dot),
        chain(KUSAMA_CHAIN_ID, "Kusama", rank = 9, ksm),
        chain(ASSET_HUB_CHAIN_ID, "Polkadot Asset Hub", rank = null, hubDot, hubUsdt, hubUsdc),
        chain(WESTEND_CHAIN_ID, "Westend", rank = 108, wnd, isTestNet = true)
    )

    @Test
    fun `a new wallet whose rows are all Auto finds its zero defaults and its funded assets`() = runTest {
        val viewModel = viewModel(
            listOf(
                walletAsset(dot, free = BigInteger.ZERO),
                walletAsset(ksm, free = BigInteger.ZERO),
                walletAsset(hubDot, free = BigInteger.ZERO),
                walletAsset(hubUsdt, free = BigInteger.valueOf(5_000_000)),
                walletAsset(hubUsdc, free = BigInteger.ZERO)
            )
        )
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(
            setOf(POLKADOT_CHAIN_ID to DOT_ASSET_ID, KUSAMA_CHAIN_ID to KSM_ASSET_ID, ASSET_HUB_CHAIN_ID to USDT_ASSET_ID),
            state.portfolioAssets.keys()
        )
        assertTrue("an empty query never lists hidden or other assets", state.hiddenAssets.isEmpty() && state.otherAssets.isEmpty())
    }

    @Test
    fun `auto funded asset appears in portfolio section`() = runTest {
        val viewModel = viewModel(listOf(walletAsset(hubUsdt, free = BigInteger.valueOf(5_000_000))))
        viewModel.onAssetSearchEntered("usdt")
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(setOf(ASSET_HUB_CHAIN_ID to USDT_ASSET_ID), state.portfolioAssets.keys())
        assertEquals("Polkadot Asset Hub", state.portfolioAssets.single().assetChainName)
        assertTrue(state.otherAssets.isEmpty())
    }

    @Test
    fun `auto zero default appears`() = runTest {
        val viewModel = viewModel(listOf(walletAsset(dot, free = BigInteger.ZERO)))
        viewModel.onAssetSearchEntered("DOT")
        advanceUntilIdle()

        assertEquals(setOf(POLKADOT_CHAIN_ID to DOT_ASSET_ID), viewModel.state.value.portfolioAssets.keys())
    }

    @Test
    fun `hidden asset appears only in Hidden section`() = runTest {
        val viewModel = viewModel(
            listOf(
                walletAsset(dot, free = BigInteger.ZERO),
                walletAsset(hubUsdc, free = BigInteger.valueOf(7_000_000), enabled = false)
            )
        )
        advanceUntilIdle()
        assertTrue(viewModel.state.value.hiddenAssets.isEmpty())
        assertTrue((ASSET_HUB_CHAIN_ID to USDC_ASSET_ID) !in viewModel.state.value.portfolioAssets.keys())

        viewModel.onAssetSearchEntered("usdc")
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(setOf(ASSET_HUB_CHAIN_ID to USDC_ASSET_ID), state.hiddenAssets.keys())
        assertTrue(state.hiddenAssets.single().isHidden)
        assertTrue(state.portfolioAssets.isEmpty() && state.otherAssets.isEmpty())
    }

    @Test
    fun `other assets only for non-empty query`() = runTest {
        val viewModel = viewModel(
            listOf(
                walletAsset(dot, free = BigInteger.ZERO),
                walletAsset(hubDot, free = BigInteger.ZERO)
            )
        )
        advanceUntilIdle()
        assertTrue(viewModel.state.value.otherAssets.isEmpty())

        viewModel.onAssetSearchEntered("dot")
        advanceUntilIdle()

        val state = viewModel.state.value
        // DOT's registry id is the same on both networks: the rows stay apart by network.
        assertEquals(setOf(POLKADOT_CHAIN_ID to DOT_ASSET_ID), state.portfolioAssets.keys())
        assertEquals(setOf(ASSET_HUB_CHAIN_ID to DOT_ASSET_ID), state.otherAssets.keys())
    }

    @Test
    fun `a query matches the network name as well as the token`() = runTest {
        val viewModel = viewModel(
            listOf(
                walletAsset(dot, free = BigInteger.ZERO),
                walletAsset(hubDot, free = BigInteger.ZERO),
                walletAsset(hubUsdt, free = BigInteger.valueOf(5_000_000)),
                walletAsset(hubUsdc, free = BigInteger.ZERO)
            )
        )
        viewModel.onAssetSearchEntered("asset hub")
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(setOf(ASSET_HUB_CHAIN_ID to USDT_ASSET_ID), state.portfolioAssets.keys())
        assertEquals(setOf(ASSET_HUB_CHAIN_ID to DOT_ASSET_ID, ASSET_HUB_CHAIN_ID to USDC_ASSET_ID), state.otherAssets.keys())
    }

    @Test
    fun `testnets stay behind the Testnets toggle`() = runTest {
        val assets = listOf(walletAsset(wnd, free = BigInteger.TEN))
        val hidden = viewModel(assets)
        hidden.onAssetSearchEntered("wnd")
        advanceUntilIdle()
        assertTrue(!hidden.state.value.hasResults)

        preferences.putBoolean(PortfolioPresentationPreferences.showTestnetsKey(WALLET_ID), true)
        val shown = viewModel(assets)
        shown.onAssetSearchEntered("wnd")
        advanceUntilIdle()
        assertEquals(setOf(WESTEND_CHAIN_ID to "wnd"), shown.state.value.portfolioAssets.keys())
        assertTrue(shown.state.value.portfolioAssets.single().isTestnet)
    }

    @Test
    fun `tap routes to openAssetDetails with exact chain and asset`() = runTest {
        val viewModel = viewModel(listOf(walletAsset(hubDot, free = BigInteger.ZERO)))
        viewModel.onAssetSearchEntered("dot")
        advanceUntilIdle()

        viewModel.assetClicked(viewModel.state.value.otherAssets.single())

        verify { router.openAssetDetails(AssetPayload(ASSET_HUB_CHAIN_ID, DOT_ASSET_ID)) }
        @Suppress("DEPRECATION")
        verify(exactly = 0) { router.openAssetIntermediateDetails(any()) }
    }

    @Test
    fun `other assets are capped and the exact symbol comes first`() = runTest {
        val many = (1..80).map { index ->
            asset("token-$index", "DOTX$index", "Token $index", ASSET_HUB_CHAIN_ID)
        }
        val hub = chain(ASSET_HUB_CHAIN_ID, "Polkadot Asset Hub", rank = null, hubDot, *many.toTypedArray())
        val viewModel = viewModel(
            assets = (many + hubDot).map { walletAsset(it, free = BigInteger.ZERO) },
            chains = listOf(hub)
        )
        viewModel.onAssetSearchEntered("dot")
        advanceUntilIdle()

        val other = viewModel.state.value.otherAssets
        assertEquals(SearchAssetsViewModel.MAX_OTHER_ASSETS, other.size)
        assertEquals(DOT_ASSET_ID, other.first().chainAssetId)
    }

    private fun List<AssetListItemViewState>.keys() = map { it.chainId to it.chainAssetId }.toSet()

    @Test
    fun `hidden unknown quantity remains searchable without outranking known funding`() = runTest {
        val currentAssets = chains.single { it.id == ASSET_HUB_CHAIN_ID }.assetsById
        val unknown = walletAsset(currentAssets.getValue(USDC_ASSET_ID), BigInteger.ZERO, enabled = false).let {
            it.copy(asset = it.asset.copy(freeInPlanksState = jp.co.soramitsu.wallet.impl.domain.model.AssetAmountState.Missing))
        }
        val vm = viewModel(listOf(unknown, walletAsset(currentAssets.getValue(USDT_ASSET_ID), BigInteger.TEN, enabled = false)))
        vm.onAssetSearchEntered("asset hub")
        advanceUntilIdle()
        assertEquals(listOf(ASSET_HUB_CHAIN_ID to USDT_ASSET_ID, ASSET_HUB_CHAIN_ID to USDC_ASSET_ID),
            vm.state.value.hiddenAssets.map { it.chainId to it.chainAssetId })
    }

    private fun viewModel(
        assets: List<AssetWithStatus>,
        chains: List<Chain> = this.chains
    ): SearchAssetsViewModel {
        val walletInteractor = mockk<WalletInteractor>(relaxed = true)
        val chainInteractor = mockk<ChainInteractor>()
        every { walletInteractor.assetsFlowAndAccount() } returns flowOf(WALLET_ID to assets)
        every { walletInteractor.selectedMetaAccountFlow() } returns flowOf(metaAccount())
        coEvery { walletInteractor.getSavedChainId(WALLET_ID) } returns null
        every { chainInteractor.getChainsFlow() } returns flowOf(chains)
        val metadataDescriptorStore = mockk<AssetMetadataDescriptorStore>()
        every { metadataDescriptorStore.get(any()) } returns null

        return SearchAssetsViewModel(
            savedStateHandle = SavedStateHandle(),
            interactor = walletInteractor,
            chainInteractor = chainInteractor,
            assetMetadataDescriptorStore = metadataDescriptorStore,
            router = router,
            portfolioVisibility = bundledPortfolioVisibility(),
            sendPausePolicy = SendPausePolicy.Unrestricted,
            portfolioPreferences = PortfolioPresentationPreferences(preferences),
            // The production default: unverified Auto rows are reviewed in the portfolio only.
            featureToggleStore = mockk<ProductFeatureToggleStore> { every { assetDiscoveryShadowEnabled } returns true }
        )
    }

    private fun metaAccount() = MetaAccount(
        id = WALLET_ID,
        chainAccounts = emptyMap(),
        favoriteChains = emptyMap(),
        substratePublicKey = ByteArray(32) { 1 },
        substrateCryptoType = CryptoType.SR25519,
        substrateAccountId = ByteArray(32) { 1 },
        ethereumAddress = null,
        ethereumPublicKey = null,
        tonPublicKey = null,
        isSelected = true,
        isBackedUp = true,
        googleBackupAddress = null,
        name = "wallet",
        initialized = true
    )

    @Suppress("LongParameterList")
    private fun asset(
        id: String,
        symbol: String,
        name: String,
        chainId: String,
        isUtility: Boolean = false,
        priceId: String? = null
    ) = Asset(
        id = id,
        name = name,
        symbol = symbol,
        iconUrl = "",
        chainId = chainId,
        chainName = "",
        chainIcon = null,
        isTestNet = false,
        priceId = priceId,
        precision = 6,
        staking = Asset.StakingType.UNSUPPORTED,
        purchaseProviders = emptyList(),
        supportStakingPool = false,
        isUtility = isUtility,
        type = ChainAssetType.Normal,
        currencyId = null,
        existentialDeposit = null,
        color = null,
        isNative = null
    )

    private fun walletAsset(asset: Asset, free: BigInteger, enabled: Boolean? = null) = AssetWithStatus(
        asset = WalletAsset(
            metaId = WALLET_ID,
            token = Token(asset, fiatRate = null, fiatSymbol = "$", recentRateChange = null),
            accountId = byteArrayOf(1),
            freeInPlanks = free,
            reservedInPlanks = BigInteger.ZERO,
            miscFrozenInPlanks = BigInteger.ZERO,
            feeFrozenInPlanks = BigInteger.ZERO,
            bondedInPlanks = null,
            redeemableInPlanks = null,
            unbondingInPlanks = null,
            sortIndex = 0,
            enabled = enabled,
            minSupportedVersion = null,
            chainAccountName = null,
            markedNotNeed = false,
            status = null
        ),
        hasAccount = true,
        hasChainAccount = true
    )

    private fun chain(id: String, name: String, rank: Int?, vararg assets: Asset, isTestNet: Boolean = false) = Chain(
        id = id,
        paraId = null,
        rank = rank,
        name = name,
        minSupportedVersion = null,
        assets = assets.map { it.copy(chainName = name, isTestNet = isTestNet) },
        nodes = emptyList(),
        explorers = emptyList(),
        externalApi = null,
        icon = "",
        addressPrefix = 0,
        isEthereumBased = false,
        isTestNet = isTestNet,
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
        const val WALLET_ID = 42L
        const val POLKADOT_CHAIN_ID = "91b171bb158e2d3848fa23a9f1c25182fb8e20313b2c1eb49219da7a70ce90c3"
        const val KUSAMA_CHAIN_ID = "b0a8d493285c2df73290dfb7e61f870f17b41801197a149ca93654499ea3dafe"
        const val ASSET_HUB_CHAIN_ID = "68d56f15f85d3136970ec16946040bc1752654e906147f7e43e9d539d7c3de2f"
        const val WESTEND_CHAIN_ID = "e143f23803ac50e8f6f8e62695d1ce9e4e1d68aa36c1cd2cfd15340213f3423e"
        const val DOT_ASSET_ID = "887a17c7-1370-4de0-97dd-5422e294fa75"
        const val KSM_ASSET_ID = "1e0c2ec6-935f-49bd-a854-5e12ee6c9f1b"
        const val USDT_ASSET_ID = "ea33432f-9bd9-4d42-93bc-cd45a0e8a44c"
        const val USDC_ASSET_ID = "951ca8aa-c390-4512-a35c-d3851440b580"
    }
}
