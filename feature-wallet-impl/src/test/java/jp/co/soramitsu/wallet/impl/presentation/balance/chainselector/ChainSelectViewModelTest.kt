package jp.co.soramitsu.wallet.impl.presentation.balance.chainselector

import java.math.BigInteger
import jp.co.soramitsu.common.utils.formatCrypto
import jp.co.soramitsu.wallet.impl.domain.model.AssetAmountState
import jp.co.soramitsu.wallet.impl.domain.model.AssetWithStatus
import jp.co.soramitsu.wallet.impl.domain.model.Asset as WalletAsset
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import io.mockk.*
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.compose.component.ChainSelectorViewStateWithFilters
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.tokengroups.*
import jp.co.soramitsu.wallet.impl.domain.ChainInteractor
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.wallet.impl.presentation.bundledPortfolioVisibility
import jp.co.soramitsu.wallet.impl.presentation.send.SendSharedState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChainSelectViewModelTest {
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
    private val impostor = chain("fake", listOf(asset("contract", "USDT")))
    private val balances = MutableStateFlow(7L to emptyList<AssetWithStatus>())
    private val knownChains = MutableStateFlow(listOf(source, hub, impostor))
    private val meta = mockk<MetaAccount>(relaxed = true) {
        every { id } returns 7L
        every { chainAccounts } returns emptyMap()
        every { substrateAccountId } returns byteArrayOf(1)
    }
    private var groups = ResolvedTokenGroups(listOf(ResolvedTokenGroup("usdt", "USDT", "Tether", 6,
        listOf(ResolvedTokenGroupMember("ethereum", "contract"), ResolvedTokenGroupMember("hub", "1984")))))

    @After fun stop() = runBlocking { jobs.forEach(Job::cancel); withTimeout(5000) { jobs.joinAll() } }

    @Test fun `asset chooser lists approved networks and selects exact AssetHub USDT not native DOT`() {
        val vm = viewModel(assetMode = true)
        await { vm.state.value.chains?.size == 2 }
        assertEquals(setOf("ethereum", "hub"), vm.state.value.chains!!.map { it.id }.toSet())
        assertFalse(vm.state.value.showAllChains)
        vm.onChainSelected(hub.toChainItemState())
        await { shared.chainId == "hub" }
        assertEquals("1984", shared.assetId)
    }

    @Test fun `address chooser with null currency id asks for exact asset on multi-asset network`() {
        val vm = viewModel(assetMode = false)
        await { vm.state.value.chains?.isNotEmpty() == true }
        vm.onChainSelected(hub.toChainItemState())
        await { runCatching { verify { router.openSelectChainAsset("hub") } }.isSuccess }
        assertNull(shared.assetId)
    }

    @Test fun `ungrouped source stays on its own network`() {
        groups = ResolvedTokenGroups.EMPTY
        val vm = viewModel(assetMode = true)
        await { vm.state.value.chains?.size == 1 }
        assertEquals("ethereum", vm.state.value.chains!!.single().id)
        vm.onChainSelected(impostor.toChainItemState())
        assertNull(shared.assetId)
    }

    @Test fun `registry removal and wallet switch cannot reuse a stale member selection`() {
        val vm = viewModel(assetMode = true)
        await { vm.state.value.chains?.size == 2 }
        groups = ResolvedTokenGroups.EMPTY
        vm.onChainSelected(hub.toChainItemState())
        assertNull(shared.assetId)
        groups = ResolvedTokenGroups(listOf(ResolvedTokenGroup("usdt", "USDT", "Tether", 6,
            listOf(ResolvedTokenGroupMember("ethereum", "contract"), ResolvedTokenGroupMember("hub", "1984")))))
        coEvery { wallet.getSelectedMetaAccount() } returns mockk(relaxed = true) { every { id } returns 8L }
        vm.onChainSelected(hub.toChainItemState())
        assertNull(shared.assetId)
    }

    @Test fun `wallet switch during suspended chain refresh cannot route the old selection`() {
        val vm = viewModel(assetMode = true)
        await { vm.state.value.chains?.size == 2 }
        val lookup = CompletableDeferred<List<Chain>>()
        every { chains.getChainsFlow() } returns flow { emit(lookup.await()) }
        vm.onChainSelected(hub.toChainItemState())
        coEvery { wallet.getSelectedMetaAccount() } returns mockk(relaxed = true) { every { id } returns 8L }
        lookup.complete(knownChains.value)
        assertNull(shared.assetId)
        verify(exactly = 0) { router.backWithResult(any(), any()) }
    }

    @Test fun `exact member balance clears on unknown missing and foreign wallet then recovers zero`() {
        val config = hub.assetsById.getValue("1984")
        every { config.chainId } returns "hub"
        every { config.precision } returns 0
        val known = WalletAsset.createEmpty(config, 7, byteArrayOf(1), minSupportedVersion = null)
            .copy(freeInPlanksState = AssetAmountState.Known(BigInteger.TEN))
        balances.value = 7L to listOf(AssetWithStatus(known, true, true))
        val vm = viewModel(assetMode = true)
        fun amount() = vm.state.value.chains?.singleOrNull { it.id == "hub" }?.balance
        await { amount() == BigInteger.TEN.toBigDecimal().formatCrypto("USDT") }
        for (next in listOf(
            7L to listOf(AssetWithStatus(known.copy(freeInPlanksState = AssetAmountState.Missing), true, true)),
            7L to emptyList(),
            8L to listOf(AssetWithStatus(known, true, true))
        )) {
            balances.value = 7L to listOf(AssetWithStatus(known, true, true))
            await { amount() == BigInteger.TEN.toBigDecimal().formatCrypto("USDT") }
            balances.value = next
            await { amount() == null }
        }
        balances.value = 7L to listOf(AssetWithStatus(known.copy(freeInPlanksState = AssetAmountState.Known(BigInteger.ZERO)), true, true))
        await { amount() == BigInteger.ZERO.toBigDecimal().formatCrypto("USDT") }
        vm.onChainSelected(hub.toChainItemState())
        await { shared.chainId == "hub" }
        assertEquals("1984", shared.assetId)
    }

    private fun viewModel(assetMode: Boolean): ChainSelectViewModel {
        every { chains.getChainsFlow() } returns knownChains
        every { accounts.selectedMetaAccountFlow() } returns flowOf(meta)
        every { accounts.observeSelectedMetaAccountFavoriteChains() } returns flowOf(emptyMap())
        every { wallet.observeSelectedAccountChainSelectFilter() } returns flowOf(ChainSelectorViewStateWithFilters.Filter.All)
        every { wallet.assetsFlowAndAccount() } returns balances
        coEvery { wallet.getSelectedMetaAccount() } returns meta
        every { registry.resolve(any()) } answers { groups }
        val arguments = mutableMapOf<String, Any?>(ChainSelectFragment.KEY_CHOOSER_MODE to false)
        if (assetMode) {
            arguments[ChainSelectFragment.KEY_SELECTED_CHAIN_ID] = "ethereum"
            arguments[ChainSelectFragment.KEY_SELECTED_ASSET_ID] = "contract"
        }
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
