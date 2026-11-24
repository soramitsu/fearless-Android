package jp.co.soramitsu.wallet.impl.presentation.balance.chainselector

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.compose.component.ChainSelectorViewStateWithFilters
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.tokengroups.ResolvedTokenGroups
import jp.co.soramitsu.runtime.multiNetwork.tokengroups.TokenGroupRegistry
import jp.co.soramitsu.wallet.api.domain.model.XcmChainType
import jp.co.soramitsu.wallet.impl.domain.ChainInteractor
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.domain.model.AssetWithStatus
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.wallet.impl.presentation.bundledPortfolioVisibility
import jp.co.soramitsu.wallet.impl.presentation.send.SendSharedState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import jp.co.soramitsu.wallet.api.presentation.WalletRouter as WalletRouterApi

/** The network picker's chooser, XCM and filter-id modes, search, favourites and closing. */
@OptIn(ExperimentalCoroutinesApi::class)
class ChainSelectViewModelCoverageTest {
    @get:Rule val main = MainCoroutineRule(UnconfinedTestDispatcher())

    private val shared = SendSharedState()
    private val router = mockk<WalletRouter>(relaxed = true)
    private val wallet = mockk<WalletInteractor>(relaxed = true)
    private val accounts = mockk<AccountInteractor>(relaxed = true)
    private val chains = mockk<ChainInteractor>(relaxed = true)
    private val registry = mockk<TokenGroupRegistry>()
    private val jobs = mutableListOf<Job>()
    private val source = chain("ethereum", listOf(asset("contract", "USDT")))
    private val hub = chain("hub", listOf(asset("dot", "DOT"), asset("1984", "USDT")))
    private val fake = chain("fake", listOf(asset("contract", "USDT")))
    private val meta = mockk<MetaAccount>(relaxed = true) {
        every { id } returns 7L
        every { chainAccounts } returns emptyMap()
        every { substrateAccountId } returns byteArrayOf(1)
    }

    @After fun stop() = runBlocking { jobs.forEach(Job::cancel); withTimeout(5000) { jobs.joinAll() } }

    @Test fun `a chooser remembers the pick, saves the applied filter and closes`() {
        val vm = viewModel(ChainSelectFragment.KEY_CHOOSER_MODE to true)
        await { vm.state.value.chains?.size == 3 }

        vm.onFilterApplied(ChainSelectorViewStateWithFilters.Filter.Favorite)
        vm.onChainSelected(hub.toChainItemState())
        vm.onDialogClose()

        await { vm.state.value.selectedChainId == "hub" }
        coVerify(timeout = 5_000) { wallet.saveChainSelectFilter(7L, "Favorite") }
        verify { router.setChainSelectorPayload("hub") }
        verify { router.back() }
        verify(exactly = 0) { router.popOutOfSend() }
    }

    @Test fun `closing without a choice leaves the send flow`() {
        val vm = viewModel()
        await { vm.state.value.chains != null }

        vm.onBackButtonClick()
        vm.onDialogClose()

        verify { router.back() }
        verify { router.popOutOfSend() }
    }

    @Test fun `an XCM picker lists only the networks the route allows`() {
        every { chains.getXcmChainIdsFlow(XcmChainType.Destination, "ethereum", "contract", "USDT") } returns flowOf(listOf("hub"))
        val vm = viewModel(
            ChainSelectFragment.KEY_XCM_CHAIN_TYPE to XcmChainType.Destination,
            ChainSelectFragment.KEY_XCM_SELECTED_ORIGIN_CHAIN_ID to "ethereum",
            ChainSelectFragment.KEY_XCM_ORIGIN_ASSET_ID to "contract",
            ChainSelectFragment.KEY_XCM_ASSET_SYMBOL to "USDT"
        )

        await { vm.state.value.chains != null }

        assertEquals(listOf("hub"), vm.state.value.chains!!.map { it.id })
    }

    @Test fun `filter ids and the search narrow the list`() {
        val filtered = viewModel(ChainSelectFragment.KEY_FILTER_CHAIN_IDS to listOf("fake", "hub"))
        await { filtered.state.value.chains != null }
        assertEquals(setOf("fake", "hub"), filtered.state.value.chains!!.map { it.id }.toSet())

        filtered.onSearchInput("HU")
        await { filtered.state.value.chains?.size == 1 }
        assertEquals("hub", filtered.state.value.chains!!.single().id)
        assertEquals("HU", filtered.state.value.searchQuery)
    }

    @Test fun `an address picker returns the network alone when no asset is wanted`() {
        val vm = viewModel(ChainSelectFragment.KEY_SELECT_ASSET to false)
        await { vm.state.value.chains?.size == 3 }

        vm.onChainSelected(hub.toChainItemState())

        verify(timeout = 5_000) {
            router.backWithResult(WalletRouterApi.KEY_CHAIN_ID to "hub", WalletRouterApi.KEY_ASSET_ID to null)
        }
        assertNull(shared.assetId)
        vm.onDialogClose()
        verify(exactly = 0) { router.popOutOfSend() }
    }

    @Test fun `a network with a single asset returns that asset`() {
        val vm = viewModel()
        await { vm.state.value.chains?.size == 3 }

        vm.onChainSelected(source.toChainItemState())

        verify(timeout = 5_000) {
            router.backWithResult(WalletRouterApi.KEY_CHAIN_ID to "ethereum", WalletRouterApi.KEY_ASSET_ID to "contract")
        }
        assertEquals("contract", shared.assetId)
        assertEquals("ethereum", shared.chainId)
    }

    @Test fun `marking a network favourite shows at once and is stored for the wallet`() {
        val vm = viewModel(ChainSelectFragment.KEY_FILTERING_ENABLED to true)
        await { vm.state.value.chains?.size == 3 }
        val hubRow = vm.state.value.chains!!.single { it.id == "hub" }
            as ChainSelectScreenContract.State.ItemState.Impl.FilteringDecorator
        assertEquals(false, hubRow.isMarkedAsFavorite)

        vm.onChainMarkedFavorite(hub.toChainItemState()) // a plain row cannot be marked
        vm.onChainMarkedFavorite(hubRow)

        await { (vm.state.value.chains?.single { it.id == "hub" } as? ChainSelectScreenContract.State.ItemState.Impl.FilteringDecorator)?.isMarkedAsFavorite == true }
        coVerify(timeout = 5_000, exactly = 1) { accounts.updateFavoriteChain(chainId = "hub", isFavorite = true, metaId = 7L) }
        assertTrue(vm.state.value is ChainSelectScreenContract.State.Impl.FilteringDecorator)
    }

    private fun viewModel(vararg extra: Pair<String, Any?>): ChainSelectViewModel {
        every { chains.getChainsFlow() } returns MutableStateFlow(listOf(source, hub, fake))
        every { accounts.selectedMetaAccountFlow() } returns flowOf(meta)
        every { accounts.observeSelectedMetaAccountFavoriteChains() } returns flowOf(emptyMap())
        every { wallet.observeSelectedAccountChainSelectFilter() } returns flowOf(ChainSelectorViewStateWithFilters.Filter.All)
        every { wallet.assetsFlowAndAccount() } returns MutableStateFlow(7L to emptyList<AssetWithStatus>())
        coEvery { wallet.getSelectedMetaAccount() } returns meta
        every { registry.resolve(any()) } returns ResolvedTokenGroups.EMPTY
        val arguments = mutableMapOf<String, Any?>(ChainSelectFragment.KEY_CHOOSER_MODE to false)
        arguments.putAll(extra)
        return ChainSelectViewModel(router, wallet, chains, SavedStateHandle(arguments), shared, accounts, bundledPortfolioVisibility(), registry)
            .also { jobs += it.viewModelScope.coroutineContext.job }
    }

    private fun await(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5000
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(5)
        assertTrue(condition())
    }

    private fun asset(assetId: String, assetSymbol: String): Asset = mockk(relaxed = true) {
        every { id } returns assetId
        every { symbol } returns assetSymbol
    }

    private fun chain(chainId: String, chainAssets: List<Asset>): Chain = mockk(relaxed = true) {
        every { id } returns chainId
        every { name } returns chainId
        every { ecosystem } returns Ecosystem.Substrate
        every { assets } returns chainAssets
        every { assetsById } returns chainAssets.associateBy { it.id }
        every { rank } returns null
        every { icon } returns ""
    }
}
