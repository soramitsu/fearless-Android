package jp.co.soramitsu.wallet.impl.presentation.balance.assetselector

import androidx.lifecycle.SavedStateHandle
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.api.presentation.AssetSelectPurpose
import jp.co.soramitsu.wallet.impl.data.network.blockchain.balance.InMemoryPreferences
import jp.co.soramitsu.wallet.impl.domain.ChainInteractor
import jp.co.soramitsu.wallet.impl.domain.SendPausePolicy
import jp.co.soramitsu.wallet.impl.domain.XcmInteractor
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.domain.model.AssetWithStatus
import jp.co.soramitsu.wallet.impl.domain.model.Token
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.wallet.impl.presentation.balance.list.PortfolioPresentationPreferences
import jp.co.soramitsu.wallet.impl.presentation.bundledPortfolioVisibility
import jp.co.soramitsu.wallet.impl.presentation.send.SendSharedState
import java.math.BigInteger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import jp.co.soramitsu.wallet.impl.domain.model.AssetAmountState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import jp.co.soramitsu.wallet.api.presentation.WalletRouter as WalletRouterApi
import jp.co.soramitsu.wallet.impl.domain.model.Asset as WalletAsset

/**
 * The asset pickers list by purpose and by the portfolio's rule, not by an explicit Shown flag
 * that sync never writes (AND-G-03): with only Shown rows they were empty for new wallets, so a new
 * user could neither pick an asset to send nor one to receive.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AssetSelectViewModelTest {

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private val preferences = InMemoryPreferences()
    private val router = mockk<WalletRouter>(relaxed = true)
    private val sharedSendState = SendSharedState()

    private val dot = asset(DOT_ASSET_ID, "DOT", POLKADOT_CHAIN_ID, isUtility = true)
    private val hubDot = asset(DOT_ASSET_ID, "DOT", ASSET_HUB_CHAIN_ID, isUtility = true)
    private val hubUsdt = asset(USDT_ASSET_ID, "USDT", ASSET_HUB_CHAIN_ID)
    private val hubUsdc = asset(USDC_ASSET_ID, "USDC", ASSET_HUB_CHAIN_ID)
    private val hubDetected = asset("detected", "USDT", ASSET_HUB_CHAIN_ID, type = ChainAssetType.Unknown)
    private val wnd = asset("wnd", "WND", WESTEND_CHAIN_ID, isUtility = true)

    private val chains = listOf(
        chain(POLKADOT_CHAIN_ID, "Polkadot", rank = 8, dot),
        chain(ASSET_HUB_CHAIN_ID, "Polkadot Asset Hub", rank = null, hubDot, hubUsdt, hubUsdc, hubDetected),
        chain(WESTEND_CHAIN_ID, "Westend", rank = 108, wnd, isTestNet = true)
    )

    @Test
    fun `send purpose lists auto funded asset (regression A9)`() = runTest {
        val viewModel = viewModel(
            sendBundle(),
            listOf(walletAsset(hubUsdt, free = BigInteger.valueOf(5_000_000)))
        )
        advanceUntilIdle()

        val asset = viewModel.state.value.assets.single()
        assertEquals(ASSET_HUB_CHAIN_ID to USDT_ASSET_ID, asset.chainId to asset.id)
        assertEquals("USDT · Polkadot Asset Hub", asset.label)
    }

    @Test
    fun `unknown amounts cannot be spent but remain visible for receiving`() = runTest {
        val initial = walletAsset(hubUsdt, free = BigInteger.TEN)
        val unknown = initial.copy(asset = initial.asset.copy(freeInPlanksState = AssetAmountState.Missing))
        val spend = viewModel(sendBundle(), listOf(unknown))
        val receive = viewModel(bundle(AssetSelectFragment.KEY_PURPOSE to AssetSelectPurpose.RECEIVE.name),
            listOf(walletAsset(hubUsdc, free = BigInteger.ZERO), unknown))
        advanceUntilIdle()
        assertTrue(spend.state.value.assets.isEmpty())
        assertTrue(spend.state.value.hiddenAssets.isEmpty())
        assertEquals("unknown Auto holding retains portfolio priority", USDT_ASSET_ID, receive.state.value.assets.first().id)
        val row = receive.state.value.assets.single { it.id == USDT_ASSET_ID }
        assertNull("rendering receives absence, never numeric zero", row.amount)
        assertEquals("", row.fiatAmount)
        receive.onAssetSelected(row)
        assertEquals(ASSET_HUB_CHAIN_ID, sharedSendState.chainId)
        assertEquals(USDT_ASSET_ID, sharedSendState.assetId)
    }

    @Test
    fun `send purpose excludes zero balance`() = runTest {
        val viewModel = viewModel(
            sendBundle(),
            listOf(
                walletAsset(dot, free = BigInteger.ZERO),
                walletAsset(hubUsdt, free = BigInteger.ZERO, enabled = true),
                walletAsset(hubUsdc, free = BigInteger.valueOf(3), frozen = BigInteger.valueOf(3))
            )
        )
        advanceUntilIdle()

        assertTrue(viewModel.state.value.assets.isEmpty())
        assertEquals(AssetSelectEmptyMessage.NothingToSend, viewModel.state.value.emptyMessage)
    }

    @Test
    fun `hidden funded assets stay spendable from a collapsed Hidden section`() = runTest {
        val viewModel = viewModel(
            sendBundle(),
            listOf(
                walletAsset(hubUsdt, free = BigInteger.valueOf(5_000_000)),
                walletAsset(hubUsdc, free = BigInteger.valueOf(7_000_000), enabled = false)
            )
        )
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(listOf(USDT_ASSET_ID), state.assets.map { it.id })
        assertEquals(listOf(USDC_ASSET_ID), state.hiddenAssets.map { it.id })
        assertFalse(state.isHiddenExpanded)

        viewModel.onHiddenSectionToggle()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.isHiddenExpanded)

        viewModel.onHiddenSectionToggle()
        viewModel.onSearchInput("usdc")
        advanceUntilIdle()
        assertTrue("a search shows matching hidden assets", viewModel.state.value.isHiddenExpanded)
        assertEquals(listOf(USDC_ASSET_ID), viewModel.state.value.hiddenAssets.map { it.id })
    }

    @Test
    fun `receive purpose lists zero-balance registry asset`() = runTest {
        val viewModel = viewModel(
            bundle(
                AssetSelectFragment.KEY_FILTER_CHAIN_ID to ASSET_HUB_CHAIN_ID,
                AssetSelectFragment.KEY_SELECTED_ASSET_ID to USDT_ASSET_ID,
                AssetSelectFragment.KEY_PURPOSE to AssetSelectPurpose.RECEIVE.name
            ),
            listOf(
                walletAsset(dot, free = BigInteger.ZERO),
                walletAsset(hubDot, free = BigInteger.ZERO),
                walletAsset(hubUsdt, free = BigInteger.ZERO),
                walletAsset(hubUsdc, free = BigInteger.valueOf(7_000_000)),
                walletAsset(hubDetected, free = BigInteger.ZERO)
            )
        )
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(
            "every reviewed asset of the network, what the portfolio shows first; never a detected token",
            listOf(USDC_ASSET_ID, DOT_ASSET_ID, USDT_ASSET_ID),
            state.assets.map { it.id }
        )
        assertTrue(state.assets.all { it.chainId == ASSET_HUB_CHAIN_ID })
        assertTrue(state.hiddenAssets.isEmpty())
        assertEquals(listOf(USDT_ASSET_ID), state.assets.filter { it.isSelected }.map { it.id })
    }

    @Test
    fun `exclude and selection are keyed by chain and asset (shared DOT id)`() = runTest {
        val sendPicker = viewModel(
            bundle(
                AssetSelectFragment.KEY_SELECTED_ASSET_ID to DOT_ASSET_ID,
                AssetSelectFragment.KEY_SELECTED_CHAIN_ID to POLKADOT_CHAIN_ID,
                AssetSelectFragment.KEY_PURPOSE to AssetSelectPurpose.SEND.name
            ),
            listOf(
                walletAsset(dot, free = BigInteger.TEN),
                walletAsset(hubDot, free = BigInteger.TEN)
            )
        )
        advanceUntilIdle()

        val selected = sendPicker.state.value.assets.filter { it.isSelected }
        assertEquals(2, sendPicker.state.value.assets.size)
        assertEquals(listOf(POLKADOT_CHAIN_ID to DOT_ASSET_ID), selected.map { it.chainId to it.id })

        val swapToPicker = viewModel(
            bundle(
                AssetSelectFragment.KEY_FILTER_CHAIN_ID to ASSET_HUB_CHAIN_ID,
                AssetSelectFragment.KEY_EXCLUDE_ASSET_ID to DOT_ASSET_ID,
                AssetSelectFragment.KEY_PURPOSE to AssetSelectPurpose.SWAP_TO.name
            ),
            listOf(
                walletAsset(dot, free = BigInteger.TEN),
                walletAsset(hubDot, free = BigInteger.TEN),
                walletAsset(hubUsdt, free = BigInteger.ZERO)
            )
        )
        advanceUntilIdle()

        assertEquals(listOf(ASSET_HUB_CHAIN_ID to USDT_ASSET_ID), swapToPicker.state.value.assets.map { it.chainId to it.id })
    }

    @Test
    fun `legacy bundle without purpose behaves as SEND`() = runTest {
        val viewModel = viewModel(
            bundle(AssetSelectFragment.KEY_SELECTED_ASSET_ID to USDT_ASSET_ID),
            listOf(
                walletAsset(dot, free = BigInteger.ZERO),
                walletAsset(hubUsdt, free = BigInteger.valueOf(5_000_000))
            )
        )
        advanceUntilIdle()

        val assets = viewModel.state.value.assets
        assertEquals(listOf(USDT_ASSET_ID), assets.map { it.id })
        assertTrue("without a network the id alone marks the selection", assets.single().isSelected)
    }

    @Test
    fun `testnets wait for the Testnets toggle unless the picker is limited to that testnet`() = runTest {
        val assets = listOf(walletAsset(wnd, free = BigInteger.TEN))

        val allNetworks = viewModel(sendBundle(), assets)
        advanceUntilIdle()
        assertTrue(allNetworks.state.value.assets.isEmpty())

        val westendOnly = viewModel(bundle(AssetSelectFragment.KEY_FILTER_CHAIN_ID to WESTEND_CHAIN_ID), assets)
        advanceUntilIdle()
        assertTrue(westendOnly.state.value.assets.single().isTestnet)

        preferences.putBoolean(PortfolioPresentationPreferences.showTestnetsKey(WALLET_ID), true)
        val withToggle = viewModel(sendBundle(), assets)
        advanceUntilIdle()
        assertEquals(listOf("wnd"), withToggle.state.value.assets.map { it.id })
    }

    @Test
    fun `selecting returns the exact chain and asset`() = runTest {
        val viewModel = viewModel(
            sendBundle(),
            listOf(walletAsset(dot, free = BigInteger.TEN), walletAsset(hubDot, free = BigInteger.TEN))
        )
        advanceUntilIdle()

        viewModel.onAssetSelected(viewModel.state.value.assets.single { it.chainId == ASSET_HUB_CHAIN_ID })

        assertEquals(ASSET_HUB_CHAIN_ID, sharedSendState.chainId)
        assertEquals(DOT_ASSET_ID, sharedSendState.assetId)
        verify {
            router.backWithResult(
                WalletRouterApi.KEY_CHAIN_ID to ASSET_HUB_CHAIN_ID,
                WalletRouterApi.KEY_ASSET_ID to DOT_ASSET_ID
            )
        }
    }

    private fun sendBundle() = bundle(
        AssetSelectFragment.KEY_SELECTED_ASSET_ID to DOT_ASSET_ID,
        AssetSelectFragment.KEY_PURPOSE to AssetSelectPurpose.SEND.name
    )

    private fun bundle(vararg values: Pair<String, Any?>) = SavedStateHandle(mapOf(*values))

    private fun viewModel(savedStateHandle: SavedStateHandle, assets: List<AssetWithStatus>): AssetSelectViewModel {
        val walletInteractor = mockk<WalletInteractor>(relaxed = true)
        val chainInteractor = mockk<ChainInteractor>()
        every { walletInteractor.assetsFlow() } returns flowOf(assets)
        every { walletInteractor.selectedMetaAccountFlow() } returns flowOf(metaAccount())
        every { chainInteractor.getChainsFlow() } returns flowOf(chains)

        return AssetSelectViewModel(
            walletRouter = router,
            walletInteractor = walletInteractor,
            xcmInteractor = mockk<XcmInteractor>(relaxed = true),
            chainInteractor = chainInteractor,
            savedStateHandle = savedStateHandle,
            sharedSendState = sharedSendState,
            portfolioVisibility = bundledPortfolioVisibility(),
            sendPausePolicy = SendPausePolicy.Unrestricted,
            portfolioPreferences = PortfolioPresentationPreferences(preferences)
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

    private fun asset(
        id: String,
        symbol: String,
        chainId: String,
        isUtility: Boolean = false,
        type: ChainAssetType = ChainAssetType.Normal
    ) = Asset(
        id = id,
        name = symbol,
        symbol = symbol,
        iconUrl = "",
        chainId = chainId,
        chainName = "",
        chainIcon = null,
        isTestNet = false,
        priceId = null,
        precision = 6,
        staking = Asset.StakingType.UNSUPPORTED,
        purchaseProviders = emptyList(),
        supportStakingPool = false,
        isUtility = isUtility,
        type = type,
        currencyId = null,
        existentialDeposit = null,
        color = null,
        isNative = null
    )

    private fun walletAsset(
        asset: Asset,
        free: BigInteger,
        enabled: Boolean? = null,
        frozen: BigInteger = BigInteger.ZERO
    ): AssetWithStatus {
        val configuration = chains.single { it.id == asset.chainId }.assetsById.getValue(asset.id)
        return AssetWithStatus(
            asset = WalletAsset(
                metaId = WALLET_ID,
                token = Token(configuration, fiatRate = null, fiatSymbol = "$", recentRateChange = null),
                accountId = byteArrayOf(1),
                freeInPlanks = free,
                reservedInPlanks = BigInteger.ZERO,
                miscFrozenInPlanks = frozen,
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
    }

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
        const val ASSET_HUB_CHAIN_ID = "68d56f15f85d3136970ec16946040bc1752654e906147f7e43e9d539d7c3de2f"
        const val WESTEND_CHAIN_ID = "e143f23803ac50e8f6f8e62695d1ce9e4e1d68aa36c1cd2cfd15340213f3423e"
        const val DOT_ASSET_ID = "887a17c7-1370-4de0-97dd-5422e294fa75"
        const val USDT_ASSET_ID = "ea33432f-9bd9-4d42-93bc-cd45a0e8a44c"
        const val USDC_ASSET_ID = "951ca8aa-c390-4512-a35c-d3851440b580"
    }
}
