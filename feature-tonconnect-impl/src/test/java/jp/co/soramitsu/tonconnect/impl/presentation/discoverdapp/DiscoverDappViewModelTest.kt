package jp.co.soramitsu.tonconnect.impl.presentation.discoverdapp

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.common.compose.component.ChainSelectorViewStateWithFilters
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.coredb.model.ConnectionSource
import jp.co.soramitsu.tonconnect.api.domain.TonConnectInteractor
import jp.co.soramitsu.tonconnect.api.model.DappConfig
import jp.co.soramitsu.tonconnect.api.model.DappModel
import jp.co.soramitsu.wallet.impl.domain.ChainInteractor
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DiscoverDappViewModelTest {
    @get:Rule val instant = InstantTaskExecutorRule()
    private val dispatcher = StandardTestDispatcher()
    private val router = mockk<WalletRouter>(relaxed = true) {
        every { chainSelectorPayloadFlow } returns emptyFlow()
    }
    private val wallet = mockk<WalletInteractor> {
        every { selectedLightMetaAccountFlow() } returns emptyFlow()
        every { observeSelectedAccountChainSelectFilter() } returns flowOf(ChainSelectorViewStateWithFilters.Filter.All)
    }
    private val chains = mockk<ChainInteractor> {
        every { getChainsFlow() } returns flowOf(emptyList())
    }
    private val resources = mockk<ResourceManager> {
        every { getString(any()) } returns "Search"
    }
    private val apps = (1..5).map {
        DappModel(it.toString(), listOf("-239"), "App " + it, "https://app.example/" + it, null, null, null, null)
    }
    private val connectedApp = apps.first().copy(metaId = 7, connectionSource = ConnectionSource.WEB)
    private val connected = MutableStateFlow(DappConfig(null, listOf(connectedApp)))
    private val ton = mockk<TonConnectInteractor>(relaxed = true) {
        coEvery { getDappsConfig() } returns listOf(DappConfig("featured", apps))
        every { getConnectedDappsFlow(ConnectionSource.WEB) } returns connected
    }
    private val viewModels = mutableListOf<DiscoverDappViewModel>()

    @Before
    fun installMain() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun clearMain() {
        viewModels.forEach { it.cancel() }
        Dispatchers.resetMain()
    }

    @Test
    fun discoveryLimitsCategoryPreviewAndConnectedModeTracksConnections() = runTest {
        val vm = viewModel()
        runCurrent()
        assertEquals(apps.take(3), vm.state.value.dapps.single().apps)
        vm.onButtonToggleChanged(DappListType.Connected)
        runCurrent()
        assertEquals(DappListType.Connected, vm.state.value.multiToggleButtonState.currentSelection)
        assertEquals(listOf(connectedApp), vm.state.value.dapps.single().apps)
        connected.value = DappConfig(null, emptyList())
        runCurrent()
        assertEquals(emptyList<DappModel>(), vm.state.value.dapps.single().apps)
    }

    @Test
    fun seeAllUsesFullCategoryAndDoesNotReplaceAnOpenSheet() = runTest {
        val vm = viewModel()
        vm.onSeeAllClick("missing")
        runCurrent()
        assertNull(vm.dappsListBottomSheetState.first())
        vm.onSeeAllClick("featured")
        runCurrent()
        assertEquals(DappsListState("Featured", apps), vm.dappsListBottomSheetState.first())
        vm.openSearch()
        runCurrent()
        assertEquals("Featured", vm.dappsListBottomSheetState.first()?.title)
        vm.onBottomSheetDappClose()
        assertNull(vm.dappsListBottomSheetState.first())
        vm.openSearch()
        runCurrent()
        assertEquals(DappsListState("Search", apps), vm.dappsListBottomSheetState.first())
    }

    @Test
    fun connectedSearchAndSelectionUseConnectedApps() = runTest {
        val vm = viewModel()
        vm.onButtonToggleChanged(DappListType.Connected)
        vm.openSearch()
        runCurrent()
        assertEquals(DappsListState("Search", listOf(connectedApp)), vm.dappsListBottomSheetState.first())
        vm.bottomSheetDappSelected(connectedApp)
        runCurrent()
        assertNull(vm.dappsListBottomSheetState.first())
        verify { router.openDappScreen(connectedApp) }
    }

    @Test
    fun incompleteDappsCannotOpenAndOnlyConnectionsCanDisconnect() = runTest {
        val vm = viewModel()
        val invalid = apps.first().copy(name = null)
        vm.onDappClick(invalid)
        vm.onDappClick(apps.first().copy(url = null))
        vm.onDappLongClick(apps.first())
        runCurrent()
        verify(exactly = 0) { router.openDappScreen(any()) }
        coVerify(exactly = 0) { ton.disconnect(any()) }
        vm.onDappLongClick(connectedApp)
        runCurrent()
        coVerify { ton.disconnect(connectedApp.connectionIdentityOrNull()!!) }
        assertEquals("dApp disconnected", vm.messageLiveData.value?.peekContent())
        vm.openSelectChain()
        verify { router.openSelectChain(null, isFilteringEnabled = true) }
    }

    private fun viewModel() = DiscoverDappViewModel(
        wallet, chains, mockk(), router, resources, ton
    ).also { viewModels += it }
}
