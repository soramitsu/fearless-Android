package jp.co.soramitsu.wallet.impl.presentation.manageassets

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.math.BigInteger
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
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.tokengroups.ResolvedTokenGroups
import jp.co.soramitsu.wallet.impl.data.network.blockchain.balance.InMemoryPreferences
import jp.co.soramitsu.wallet.impl.domain.ChainInteractor
import jp.co.soramitsu.wallet.impl.domain.SendPausePolicy
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.domain.model.AssetWithStatus
import jp.co.soramitsu.wallet.impl.domain.model.Token
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.wallet.impl.presentation.balance.list.PortfolioPresentationPreferences
import jp.co.soramitsu.wallet.impl.presentation.bundledPortfolioVisibility
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import jp.co.soramitsu.wallet.impl.domain.model.Asset as WalletAsset

/** Manage assets: the network title per filter, the initial filter, section and network toggles, and navigation. */
@OptIn(ExperimentalCoroutinesApi::class)
class ManageAssetsViewModelCoverageTest {

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private val preferences = InMemoryPreferences()
    private val first = coreAsset("first", "ONE")
    private val second = coreAsset("second", "TWO")
    private val walletInteractor = mockk<WalletInteractor>(relaxed = true)
    private val accountInteractor = mockk<AccountInteractor>()
    private val chainInteractor = mockk<ChainInteractor>()
    private val router = mockk<WalletRouter>(relaxed = true)
    private val resourceManager = mockk<ResourceManager>(relaxed = true) {
        every { getString(any()) } answers { "res:${firstArg<Int>()}" }
    }

    @Test
    fun `the network title names the saved network filter`() = runTest {
        stub(filter = ChainSelectorViewStateWithFilters.Filter.Popular)
        val popular = viewModel()
        advanceUntilIdle()
        assertEquals("res:${R.string.network_management_popular}", popular.state.value.selectedChainTitle)

        stub(filter = ChainSelectorViewStateWithFilters.Filter.Favorite)
        val favourite = viewModel()
        advanceUntilIdle()
        assertEquals("res:${R.string.network_managment_favourite}", favourite.state.value.selectedChainTitle)
    }

    @Test
    fun `only the first initial filter applies and an unknown one means All`() = runTest {
        stub()
        val vm = viewModel()
        vm.applyInitialFilter("Hidden")
        vm.applyInitialFilter("Shown")
        advanceUntilIdle()
        assertEquals(ManageAssetFilter.Hidden, vm.state.value.visibilityFilter)

        val other = viewModel()
        other.applyInitialFilter("no-such-filter")
        advanceUntilIdle()
        assertEquals(ManageAssetFilter.All, other.state.value.visibilityFilter)
    }

    @Test
    fun `a collapsed section is remembered for the wallet`() = runTest {
        stub()
        val vm = viewModel()
        advanceUntilIdle()

        vm.onSectionToggle(NETWORK_SECTION)

        assertEquals(setOf(NETWORK_SECTION), PortfolioPresentationPreferences(preferences).observeManageSections(WALLET_ID).first())
    }

    @Test
    fun `switching a network section off hides each of its assets on close`() = runTest {
        stub()
        val vm = viewModel()
        advanceUntilIdle()

        vm.onGroupChecked(NETWORK_SECTION, checked = false)
        vm.onGroupChecked("network:other-chain", checked = true) // no rows there
        vm.onDialogClose()
        advanceUntilIdle()

        val persisted = slot<List<AssetBooleanState>>()
        coVerify(exactly = 1) { walletInteractor.updateAssetsHiddenState(capture(persisted), WALLET_ID) }
        assertEquals(
            setOf(AssetBooleanState(CHAIN_ID, first.id, false), AssetBooleanState(CHAIN_ID, second.id, false)),
            persisted.captured.toSet()
        )
        verify { router.setChainSelectorPayload(null) }
    }

    @Test
    fun `done closes and the network button opens the filtering network picker`() = runTest {
        stub()
        val vm = viewModel()
        advanceUntilIdle()
        val row = vm.state.value.assets.orEmpty().values.flatten().first()

        vm.onItemClicked(row)
        vm.onEditClicked(row)
        vm.onDoneClicked()
        vm.onSelectedChainClicked()
        advanceUntilIdle()

        verify(exactly = 1) { router.back() }
        verify(exactly = 1) { router.openSelectChain(null, null, true, null, true, true, true) }
    }

    @Test
    fun `a TON network keeps the network picker closed`() = runTest {
        stub(savedChainId = TON_CHAIN_ID)
        coEvery { walletInteractor.getChain(TON_CHAIN_ID) } returns chain(emptyList(), id = TON_CHAIN_ID, ecosystem = Ecosystem.Ton)
        val vm = viewModel()
        advanceUntilIdle()

        vm.onSelectedChainClicked()
        advanceUntilIdle()

        assertEquals("Dormant network", vm.state.value.selectedChainTitle)
        verify(exactly = 0) { router.openSelectChain(any(), any(), any(), any(), any(), any(), any()) }
    }

    private fun stub(
        filter: ChainSelectorViewStateWithFilters.Filter = ChainSelectorViewStateWithFilters.Filter.All,
        savedChainId: String? = null
    ) {
        every { walletInteractor.assetsFlowAndAccount() } returns flowOf(
            WALLET_ID to listOf(walletAsset(first, BigInteger.TEN), walletAsset(second, BigInteger.ONE))
        )
        every { walletInteractor.selectedMetaAccountFlow() } returns flowOf(metaAccount())
        every { walletInteractor.observeSelectedAccountChainSelectFilter() } returns flowOf(filter)
        coEvery { walletInteractor.getSavedChainId(WALLET_ID) } returns savedChainId
        every { chainInteractor.getChainsFlow() } returns flowOf(listOf(chain(listOf(first, second))))
        coEvery { accountInteractor.selectedLightMetaAccount() } returns mockk<LightMetaAccount> { every { id } returns WALLET_ID }
        every { router.chainSelectorPayloadFlow } returns emptyFlow()
    }

    private fun viewModel() = ManageAssetsViewModel(
        walletRouter = router,
        walletInteractor = walletInteractor,
        accountInteractor = accountInteractor,
        chainInteractor = chainInteractor,
        resourceManager = resourceManager,
        portfolioVisibility = bundledPortfolioVisibility(),
        sendPausePolicy = SendPausePolicy.Unrestricted,
        tokenGroupRegistry = mockk {
            every { observeResolvedGroups() } returns flowOf(ResolvedTokenGroups.EMPTY)
            every { resolve(any()) } returns ResolvedTokenGroups.EMPTY
        },
        portfolioPreferences = PortfolioPresentationPreferences(preferences)
    )

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

    private fun coreAsset(id: String, symbol: String) = Asset(
        id = id,
        name = id,
        symbol = symbol,
        iconUrl = "",
        chainId = CHAIN_ID,
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

    private fun walletAsset(asset: Asset, free: BigInteger) = AssetWithStatus(
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
            enabled = null,
            minSupportedVersion = null,
            chainAccountName = null,
            markedNotNeed = false,
            status = null
        ),
        hasAccount = true,
        hasChainAccount = true
    )

    private fun chain(assets: List<Asset>, id: String = CHAIN_ID, ecosystem: Ecosystem = Ecosystem.Substrate) = Chain(
        id = id,
        paraId = null,
        rank = null,
        name = "Dormant network",
        minSupportedVersion = null,
        assets = assets,
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
        ecosystem = ecosystem,
        remoteAssetsSource = null
    )

    private companion object {
        const val WALLET_ID = 42L
        const val CHAIN_ID = "dormant-chain"
        const val TON_CHAIN_ID = "ton-chain"
        const val NETWORK_SECTION = "network:$CHAIN_ID"
    }
}
