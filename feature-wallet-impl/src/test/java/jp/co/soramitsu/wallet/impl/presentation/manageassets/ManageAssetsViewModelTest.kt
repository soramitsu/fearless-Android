package jp.co.soramitsu.wallet.impl.presentation.manageassets

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.model.LightMetaAccount
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.compose.component.ChainSelectorViewStateWithFilters
import jp.co.soramitsu.common.model.AssetBooleanState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.impl.domain.ChainInteractor
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.domain.model.AssetWithStatus
import jp.co.soramitsu.wallet.impl.domain.model.Token
import jp.co.soramitsu.wallet.impl.domain.model.AssetAmountState
import jp.co.soramitsu.wallet.impl.domain.model.FiatValuationContext
import jp.co.soramitsu.common.data.network.coingecko.FiatCurrency
import kotlinx.coroutines.flow.MutableStateFlow
import jp.co.soramitsu.wallet.impl.data.network.blockchain.balance.InMemoryPreferences
import jp.co.soramitsu.wallet.impl.domain.SendPausePolicy
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.wallet.impl.presentation.balance.list.PortfolioPresentationPreferences
import jp.co.soramitsu.wallet.impl.presentation.bundledPortfolioVisibility
import jp.co.soramitsu.common.utils.formatCrypto
import jp.co.soramitsu.feature_wallet_impl.R
import java.math.BigDecimal
import java.math.BigInteger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import jp.co.soramitsu.wallet.impl.domain.model.Asset as WalletAsset

@OptIn(ExperimentalCoroutinesApi::class)
class ManageAssetsViewModelTest {

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private val preferences = InMemoryPreferences()

    @Test
    fun `auto renders from portfolio eligibility and only toggles persist explicit choices`() = runTest {
        val visibleAuto = coreAsset(id = "holding", symbol = "HOLD")
        val hiddenAuto = coreAsset(id = "empty", symbol = "EMPTY")
        val chain = chain(listOf(visibleAuto, hiddenAuto))
        val assets = listOf(
            walletAsset(visibleAuto, free = BigInteger.TEN),
            walletAsset(hiddenAuto, free = BigInteger.ZERO)
        )
        val walletInteractor = mockk<WalletInteractor>(relaxed = true)
        val accountInteractor = mockk<AccountInteractor>()
        val chainInteractor = mockk<ChainInteractor>()
        val router = mockk<WalletRouter>(relaxed = true)

        every { walletInteractor.assetsFlowAndAccount() } returns flowOf(WALLET_ID to assets)
        every { walletInteractor.selectedMetaAccountFlow() } returns flowOf(metaAccount())
        every { walletInteractor.observeSelectedAccountChainSelectFilter() } returns
            flowOf(ChainSelectorViewStateWithFilters.Filter.All)
        coEvery { walletInteractor.getSavedChainId(WALLET_ID) } returns null
        every { chainInteractor.getChainsFlow() } returns flowOf(listOf(chain))
        coEvery { accountInteractor.selectedLightMetaAccount() } returns
            mockk<LightMetaAccount> { every { id } returns WALLET_ID }
        every { router.chainSelectorPayloadFlow } returns emptyFlow()

        val viewModel = viewModel(router, walletInteractor, accountInteractor, chainInteractor)
        advanceUntilIdle()

        val items = viewModel.state.value.assets.orEmpty().values.flatten().associateBy { it.id }
        assertTrue(items.getValue(visibleAuto.id).isChecked)
        assertFalse(items.getValue(hiddenAuto.id).isChecked)

        viewModel.onDialogClose()
        advanceUntilIdle()
        coVerify(exactly = 0) { walletInteractor.updateAssetsHiddenState(any(), WALLET_ID) }

        viewModel.onChecked(items.getValue(visibleAuto.id), checked = false)
        viewModel.onChecked(items.getValue(hiddenAuto.id), checked = true)
        viewModel.onDialogClose()
        advanceUntilIdle()

        val persisted = slot<List<AssetBooleanState>>()
        coVerify(exactly = 1) { walletInteractor.updateAssetsHiddenState(capture(persisted), WALLET_ID) }
        assertTrue(
            AssetBooleanState(CHAIN_ID, visibleAuto.id, false) in persisted.captured
        )
        assertTrue(
            AssetBooleanState(CHAIN_ID, hiddenAuto.id, true) in persisted.captured
        )
    }

    @Test
    fun `auto utility of a ranked mainnet is checked at zero and testnets wait for the toggle`() = runTest {
        val dot = coreAsset(id = DOT_ASSET_ID, symbol = "DOT", chainId = POLKADOT_CHAIN_ID).copy(isUtility = true)
        val wnd = coreAsset(id = "wnd", symbol = "WND", chainId = WESTEND_CHAIN_ID).copy(isUtility = true)
        val polkadot = chain(listOf(dot), id = POLKADOT_CHAIN_ID, rank = 8)
        val westend = chain(listOf(wnd), id = WESTEND_CHAIN_ID, rank = 108, isTestNet = true)
        val walletInteractor = mockk<WalletInteractor>(relaxed = true)
        val accountInteractor = mockk<AccountInteractor>()
        val chainInteractor = mockk<ChainInteractor>()
        val router = mockk<WalletRouter>(relaxed = true)

        every { walletInteractor.assetsFlowAndAccount() } returns flowOf(WALLET_ID to
            listOf(walletAsset(dot, free = BigInteger.ZERO), walletAsset(wnd, free = BigInteger.TEN))
        )
        every { walletInteractor.selectedMetaAccountFlow() } returns flowOf(metaAccount())
        every { walletInteractor.observeSelectedAccountChainSelectFilter() } returns
            flowOf(ChainSelectorViewStateWithFilters.Filter.All)
        coEvery { walletInteractor.getSavedChainId(WALLET_ID) } returns null
        every { chainInteractor.getChainsFlow() } returns flowOf(listOf(polkadot, westend))
        coEvery { accountInteractor.selectedLightMetaAccount() } returns
            mockk<LightMetaAccount> { every { id } returns WALLET_ID }
        every { router.chainSelectorPayloadFlow } returns emptyFlow()

        val hiddenTestnets = viewModel(router, walletInteractor, accountInteractor, chainInteractor)
        advanceUntilIdle()
        val items = hiddenTestnets.state.value.assets.orEmpty().values.flatten().associateBy { it.id }
        assertTrue(items.getValue(DOT_ASSET_ID).isChecked)
        assertFalse("testnets are listed only behind the toggle", "wnd" in items)
        assertFalse(hiddenTestnets.state.value.showTestnets)

        hiddenTestnets.onShowTestnetsChanged(true)
        advanceUntilIdle()
        assertTrue(preferences.getBoolean(PortfolioPresentationPreferences.showTestnetsKey(WALLET_ID), false))

        val shownTestnets = viewModel(router, walletInteractor, accountInteractor, chainInteractor)
        advanceUntilIdle()
        val withTestnets = shownTestnets.state.value.assets.orEmpty().values.flatten().associateBy { it.id }
        assertTrue(shownTestnets.state.value.showTestnets)
        assertTrue("a funded testnet row is shown once testnets are on", withTestnets.getValue("wnd").isChecked)
    }

    /**
     * Upgrade from Play 4.2.0: it hid every empty asset of a funded wallet (enabled = 0) and never
     * revealed a later deposit; database 81 keeps such a funded row hidden. The first portfolio
     * list of this version records which assets were hidden by then.
     */
    @Test
    fun `hidden assets that hold funds are listed first with their amount and where the hide came from`() = runTest {
        val shown = coreAsset(id = "shown", symbol = "SHOWN")
        val legacyHidden = coreAsset(id = "legacy-hidden", symbol = "OLD")
        val newlyHidden = coreAsset(id = "newly-hidden", symbol = "NEW")
        val hiddenEmpty = coreAsset(id = "hidden-empty", symbol = "EMPTY")
        val chain = chain(listOf(shown, hiddenEmpty, newlyHidden, legacyHidden))
        PortfolioPresentationPreferences(preferences).rememberLegacyHiddenAssets(WALLET_ID) {
            setOf(
                PortfolioPresentationPreferences.assetKey(CHAIN_ID, legacyHidden.id),
                PortfolioPresentationPreferences.assetKey(CHAIN_ID, hiddenEmpty.id)
            )
        }
        val resourceManager = mockk<ResourceManager>(relaxed = true) {
            every { getString(R.string.portfolio_hidden_in_previous_version) } returns "Hidden in previous version"
            every { getString(R.string.portfolio_asset_hidden) } returns "Hidden"
        }
        val (router, walletInteractor, accountInteractor, chainInteractor) = screenDependencies(
            chains = listOf(chain),
            assets = listOf(
                walletAsset(shown, free = BigInteger.valueOf(1000)),
                walletAsset(hiddenEmpty, free = BigInteger.ZERO, enabled = false),
                walletAsset(newlyHidden, free = BigInteger.valueOf(4), enabled = false),
                walletAsset(legacyHidden, free = BigInteger.valueOf(3), enabled = false)
            )
        )

        val viewModel = viewModel(router, walletInteractor, accountInteractor, chainInteractor, resourceManager)
        advanceUntilIdle()

        val ordered = viewModel.state.value.assets.orEmpty().values.flatten()
        val items = ordered.associateBy { it.id }
        assertEquals(setOf(legacyHidden.id, newlyHidden.id), ordered.take(2).map { it.id }.toSet())
        assertEquals("Hidden in previous version", items.getValue(legacyHidden.id).hiddenNote)
        assertEquals("Hidden", items.getValue(newlyHidden.id).hiddenNote)
        assertEquals(BigDecimal("3").formatCrypto(), items.getValue(legacyHidden.id).amount)
        assertFalse("the switch still says hidden; only the user changes it", items.getValue(legacyHidden.id).isChecked)
        assertNull("an empty hidden asset leaves nothing out of the total", items.getValue(hiddenEmpty.id).hiddenNote)
        assertNull(items.getValue(shown.id).hiddenNote)
    }

    @Test
    fun `row amount is the total, like the portfolio row`() = runTest {
        val staked = coreAsset(id = "staked", symbol = "STK")
        val (router, walletInteractor, accountInteractor, chainInteractor) = screenDependencies(
            chains = listOf(chain(listOf(staked))),
            assets = listOf(walletAsset(staked, free = BigInteger.valueOf(1000), frozen = BigInteger.valueOf(990)))
        )

        val viewModel = viewModel(router, walletInteractor, accountInteractor, chainInteractor)
        advanceUntilIdle()

        val item = viewModel.state.value.assets.orEmpty().values.flatten().single()
        assertEquals(BigDecimal("1000").formatCrypto(), item.amount)
        assertFalse(item.isZeroAmount)
    }

    @Test
    fun `an asset without a trusted price shows no fiat value, not a zero one`() = runTest {
        val priced = coreAsset(id = "priced", symbol = "PRC").copy(priceId = "priced")
        val thirdPartyUsdt = coreAsset(id = THIRD_PARTY_USDT_ID, symbol = "USDT", chainId = KUSAMA_ASSET_HUB_CHAIN_ID)
            .copy(priceId = "tether")
        val cEnj = coreAsset(id = "cenj", symbol = "CENJ", chainId = CANARY_CHAIN_ID)
            .copy(priceId = "enjincoin", isTestNet = true)
        val (router, walletInteractor, accountInteractor, chainInteractor) = screenDependencies(
            chains = listOf(
                chain(listOf(priced)),
                chain(listOf(thirdPartyUsdt), id = KUSAMA_ASSET_HUB_CHAIN_ID),
                chain(listOf(cEnj), id = CANARY_CHAIN_ID, isTestNet = true)
            ),
            assets = listOf(
                qualified(walletAsset(priced, free = BigInteger.ZERO)),
                walletAsset(thirdPartyUsdt, free = BigInteger.valueOf(50)),
                walletAsset(cEnj, free = BigInteger.valueOf(100))
            )
        )
        preferences.putBoolean(PortfolioPresentationPreferences.showTestnetsKey(WALLET_ID), true)

        val viewModel = viewModel(router, walletInteractor, accountInteractor, chainInteractor)
        advanceUntilIdle()

        val items = viewModel.state.value.assets.orEmpty().values.flatten().associateBy { it.id }
        assertEquals("$0", items.getValue(priced.id).fiatAmount)
        assertNull(items.getValue(THIRD_PARTY_USDT_ID).fiatAmount)
        assertNull(items.getValue(cEnj.id).fiatAmount)
    }

    @Test
    fun `switching a funded asset off is the user's own hide and is never announced as hidden funds`() = runTest {
        val funded = coreAsset(id = "holding", symbol = "HOLD")
        val shownEmpty = coreAsset(id = "shown-empty", symbol = "EMPTY")
        val (router, walletInteractor, accountInteractor, chainInteractor) = screenDependencies(
            chains = listOf(chain(listOf(funded, shownEmpty))),
            assets = listOf(
                walletAsset(funded, free = BigInteger.TEN),
                walletAsset(shownEmpty, free = BigInteger.ZERO, enabled = true)
            )
        )
        val viewModel = viewModel(router, walletInteractor, accountInteractor, chainInteractor)
        advanceUntilIdle()
        val items = viewModel.state.value.assets.orEmpty().values.flatten().associateBy { it.id }

        viewModel.onChecked(items.getValue(funded.id), checked = false)
        viewModel.onChecked(items.getValue(shownEmpty.id), checked = false)
        viewModel.onDialogClose()
        advanceUntilIdle()

        assertEquals(
            "only the asset hidden while it held a balance is acknowledged; a later deposit into the other is announced",
            setOf(PortfolioPresentationPreferences.assetKey(CHAIN_ID, funded.id)),
            preferences.getStringSet(PortfolioPresentationPreferences.reviewedHiddenFundedKey(WALLET_ID), emptySet())
        )
        coVerify(exactly = 1) { walletInteractor.updateAssetsHiddenState(any(), WALLET_ID) }
    }

    @Test fun `hidden filter excludes automatic off rows and group toggle includes members outside search`() = runTest {
        val first = coreAsset("one", "USDT", "a")
        val second = coreAsset("two", "USDT", "b")
        val automatic = coreAsset("automatic", "AUTO", "a")
        val deps = screenDependencies(listOf(chain(listOf(first, automatic), "a"), chain(listOf(second), "b")),
            listOf(walletAsset(first, BigInteger.TEN, false), walletAsset(second, BigInteger.ZERO, true), walletAsset(automatic, BigInteger.ZERO)))
        val groups = jp.co.soramitsu.runtime.multiNetwork.tokengroups.ResolvedTokenGroups(listOf(
            jp.co.soramitsu.runtime.multiNetwork.tokengroups.ResolvedTokenGroup("usdt", "USDT", "Tether", 0, listOf(
                jp.co.soramitsu.runtime.multiNetwork.tokengroups.ResolvedTokenGroupMember("a", "one"),
                jp.co.soramitsu.runtime.multiNetwork.tokengroups.ResolvedTokenGroupMember("b", "two")))))
        val vm = viewModel(deps.router, deps.walletInteractor, deps.accountInteractor, deps.chainInteractor, groups = groups)
        advanceUntilIdle()
        vm.onFilterChanged(ManageAssetFilter.Hidden)
        advanceUntilIdle()
        assertEquals(listOf("one"), vm.state.value.assets.orEmpty().values.flatten().map { it.id })
        vm.onSearchInput("one")
        advanceUntilIdle()
        vm.onGroupChecked("group:usdt", true)
        advanceUntilIdle()
        vm.onDialogClose()
        advanceUntilIdle()
        coVerify { deps.walletInteractor.updateAssetsHiddenState(match { updates ->
            updates.toSet() == setOf(AssetBooleanState("a", "one", true), AssetBooleanState("b", "two", true))
        }, WALLET_ID) }
    }

    @Test fun `wallet switch with delayed balances never shows or writes the previous wallet rows`() = runTest {
        val token = coreAsset("holding", "HOLD")
        val oldRows = listOf(walletAsset(token, BigInteger.TEN, false))
        val deps = screenDependencies(listOf(chain(listOf(token))), oldRows)
        val meta = kotlinx.coroutines.flow.MutableStateFlow(metaAccount())
        val tagged = kotlinx.coroutines.flow.MutableStateFlow(WALLET_ID to oldRows)
        every { deps.walletInteractor.selectedMetaAccountFlow() } returns meta
        every { deps.walletInteractor.assetsFlowAndAccount() } returns tagged
        PortfolioPresentationPreferences(preferences).setLens(WALLET_ID, jp.co.soramitsu.wallet.impl.presentation.balance.list.PortfolioLens.ByNetwork)
        val vm = viewModel(deps.router, deps.walletInteractor, deps.accountInteractor, deps.chainInteractor)
        advanceUntilIdle()
        assertEquals(jp.co.soramitsu.wallet.impl.presentation.balance.list.PortfolioLens.ByNetwork, vm.state.value.lens)
        val prior = vm.state.value.assets.orEmpty().values.flatten().single()
        vm.onChecked(prior, true)
        advanceUntilIdle()
        meta.value = metaAccount(WALLET_ID + 1)
        advanceUntilIdle()
        assertTrue(vm.state.value.assets.orEmpty().isEmpty())
        assertEquals(jp.co.soramitsu.wallet.impl.presentation.balance.list.PortfolioLens.ByToken, vm.state.value.lens)
        vm.onDialogClose()
        advanceUntilIdle()
        coVerify(exactly = 0) { deps.walletInteractor.updateAssetsHiddenState(any(), any()) }
        tagged.value = (WALLET_ID + 1) to listOf(walletAsset(token, BigInteger.ZERO, true))
        advanceUntilIdle()
        val next = vm.state.value.assets.orEmpty().values.flatten().single()
        assertTrue(next.isZeroAmount)
        assertEquals("shown", next.preference)
    }

    @Test
    fun `unknown holdings and quotes clear values while known zero remains exact`() = runTest {
        val config = coreAsset("priced", "PRC").copy(priceId = "priced")
        val known = qualified(walletAsset(config, BigInteger.TEN))
        val deps = screenDependencies(listOf(chain(listOf(config))), listOf(known))
        val values = MutableStateFlow(WALLET_ID to listOf(known))
        every { deps.walletInteractor.assetsFlowAndAccount() } returns values
        val resources = mockk<ResourceManager>(relaxed = true) {
            every { getString(R.string.portfolio_balance_unavailable) } returns "Unavailable"
        }
        val vm = viewModel(deps.router, deps.walletInteractor, deps.accountInteractor, deps.chainInteractor, resources)
        advanceUntilIdle()
        fun row() = vm.state.value.assets.orEmpty().values.flatten().single()
        assertEquals("$20", row().fiatAmount)
        for (replacement in listOf(
            listOf(known.copy(asset = known.asset.copy(freeInPlanksState = AssetAmountState.Missing))),
            emptyList()
        )) {
            values.value = WALLET_ID to replacement
            advanceUntilIdle()
            assertEquals("Unavailable", row().amount)
            assertNull(row().fiatAmount)
            assertFalse(row().isZeroAmount)
            assertTrue("unknown Auto holding is not silently hidden", row().isChecked)
        }
        val zero = qualified(walletAsset(config, BigInteger.ZERO))
        values.value = WALLET_ID to listOf(zero)
        advanceUntilIdle()
        assertEquals(BigDecimal.ZERO.formatCrypto(), row().amount)
        assertEquals("$0", row().fiatAmount)
        assertTrue(row().isZeroAmount)
        for (token in listOf(
            zero.asset.token.copy(quoteCurrencyId = "eur"),
            zero.asset.token.copy(fiatRateInput = AssetAmountState.Missing)
        )) {
            values.value = WALLET_ID to listOf(zero.copy(asset = zero.asset.copy(token = token)))
            advanceUntilIdle()
            assertNull("zero quantity does not authorize an unqualified quote", row().fiatAmount)
            assertEquals(BigDecimal.ZERO.formatCrypto(), row().amount)
        }
    }

    private fun qualified(row: AssetWithStatus): AssetWithStatus = row.copy(asset = row.asset.copy(
        token = Token(row.asset.token.configuration, AssetAmountState.Known(BigDecimal("2")),
            AssetAmountState.Known(BigDecimal.ZERO), FiatValuationContext("usd", FiatCurrency("usd", "$", "Dollar", "")),
            "usd", row.asset.token.configuration.priceId)
    ))

    private data class ScreenDependencies(
        val router: WalletRouter,
        val walletInteractor: WalletInteractor,
        val accountInteractor: AccountInteractor,
        val chainInteractor: ChainInteractor
    )

    private fun screenDependencies(chains: List<Chain>, assets: List<AssetWithStatus>): ScreenDependencies {
        val walletInteractor = mockk<WalletInteractor>(relaxed = true)
        val accountInteractor = mockk<AccountInteractor>()
        val chainInteractor = mockk<ChainInteractor>()
        val router = mockk<WalletRouter>(relaxed = true)

        every { walletInteractor.assetsFlowAndAccount() } returns flowOf(WALLET_ID to assets)
        every { walletInteractor.selectedMetaAccountFlow() } returns flowOf(metaAccount())
        every { walletInteractor.observeSelectedAccountChainSelectFilter() } returns
            flowOf(ChainSelectorViewStateWithFilters.Filter.All)
        coEvery { walletInteractor.getSavedChainId(WALLET_ID) } returns null
        every { chainInteractor.getChainsFlow() } returns flowOf(chains)
        coEvery { accountInteractor.selectedLightMetaAccount() } returns
            mockk<LightMetaAccount> { every { id } returns WALLET_ID }
        every { router.chainSelectorPayloadFlow } returns emptyFlow()

        return ScreenDependencies(router, walletInteractor, accountInteractor, chainInteractor)
    }

    private fun viewModel(
        router: WalletRouter,
        walletInteractor: WalletInteractor,
        accountInteractor: AccountInteractor,
        chainInteractor: ChainInteractor,
        resourceManager: ResourceManager = mockk<ResourceManager>(relaxed = true),
        groups: jp.co.soramitsu.runtime.multiNetwork.tokengroups.ResolvedTokenGroups = jp.co.soramitsu.runtime.multiNetwork.tokengroups.ResolvedTokenGroups.EMPTY
    ) = ManageAssetsViewModel(
        walletRouter = router,
        walletInteractor = walletInteractor,
        accountInteractor = accountInteractor,
        chainInteractor = chainInteractor,
        resourceManager = resourceManager,
        portfolioVisibility = bundledPortfolioVisibility(),
        sendPausePolicy = SendPausePolicy.Unrestricted,
        tokenGroupRegistry = mockk {
            every { observeResolvedGroups() } returns flowOf(groups)
            every { resolve(any()) } returns groups
        },
        portfolioPreferences = PortfolioPresentationPreferences(preferences)
    )

    private fun metaAccount(walletId: Long = WALLET_ID) = MetaAccount(
        id = walletId,
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

    private fun coreAsset(id: String, symbol: String, chainId: String = CHAIN_ID) = Asset(
        id = id,
        name = id,
        symbol = symbol,
        iconUrl = "",
        chainId = chainId,
        chainName = "Dormant network",
        chainIcon = null,
        isTestNet = false,
        priceId = null,
        precision = 0,
        staking = Asset.StakingType.UNSUPPORTED,
        purchaseProviders = emptyList(),
        supportStakingPool = false,
        isUtility = false,
        type = ChainAssetType.Normal,
        currencyId = id,
        existentialDeposit = null,
        color = null,
        isNative = false
    )

    private fun walletAsset(
        asset: Asset,
        free: BigInteger,
        enabled: Boolean? = null,
        frozen: BigInteger = BigInteger.ZERO
    ) = AssetWithStatus(
        asset = WalletAsset(
            metaId = WALLET_ID,
            token = Token(asset, fiatRate = null, fiatSymbol = "$", recentRateChange = null),
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

    private fun chain(
        assets: List<Asset>,
        id: String = CHAIN_ID,
        rank: Int? = null,
        isTestNet: Boolean = false
    ) = Chain(
        id = id,
        paraId = null,
        rank = rank,
        name = "Dormant network",
        minSupportedVersion = null,
        assets = assets,
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
        const val CHAIN_ID = "dormant-chain"
        const val POLKADOT_CHAIN_ID = "91b171bb158e2d3848fa23a9f1c25182fb8e20313b2c1eb49219da7a70ce90c3"
        const val WESTEND_CHAIN_ID = "e143f23803ac50e8f6f8e62695d1ce9e4e1d68aa36c1cd2cfd15340213f3423e"
        const val DOT_ASSET_ID = "887a17c7-1370-4de0-97dd-5422e294fa75"
        const val KUSAMA_ASSET_HUB_CHAIN_ID = "48239ef607d7928874027a43a67689209727dfb3d3dc5e5b03a39bdc2eda771a"
        const val THIRD_PARTY_USDT_ID = "27768227-8a9a-4443-a57d-6013c24f85e9"
        const val CANARY_CHAIN_ID = "a37725fd8943d2a524cb7ecc65da438f9fa644db78ba24dcd0003e2f95645e8f"
    }
}
