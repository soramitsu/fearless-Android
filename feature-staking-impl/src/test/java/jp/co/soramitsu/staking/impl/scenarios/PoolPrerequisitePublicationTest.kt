package jp.co.soramitsu.staking.impl.scenarios

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.core.updater.UpdateSystem
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.state.SingleAssetSharedState
import jp.co.soramitsu.staking.api.data.StakingAssetSelection
import jp.co.soramitsu.staking.api.data.StakingSelectionRevision
import jp.co.soramitsu.staking.api.data.StakingSharedState
import jp.co.soramitsu.staking.api.domain.model.StakingState
import jp.co.soramitsu.staking.impl.data.model.PoolMember
import jp.co.soramitsu.staking.impl.data.repository.StakingPoolDataSource
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolSharedStateProvider
import jp.co.soramitsu.staking.impl.presentation.staking.main.ManageStakingBottomSheet
import jp.co.soramitsu.staking.impl.presentation.staking.main.StakingViewModel
import jp.co.soramitsu.staking.impl.presentation.staking.main.StakingViewState
import jp.co.soramitsu.staking.impl.presentation.staking.main.scenarios.BaseStakingViewModel
import jp.co.soramitsu.staking.impl.presentation.staking.main.scenarios.StakingPoolViewModel
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.*
import jp.co.soramitsu.core.models.Asset as ChainAsset

/** Actual pool interactor/scenario/root; only public metadata and remote data sources are held.
 * No private flow replacement, synthesized source result, Android device, or timed sleeps.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PoolPrerequisitePublicationTest {
    @get:Rule val instant = InstantTaskExecutorRule()
    private val dispatcher = StandardTestDispatcher()
    private var root: StakingViewModel? = null
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { root?.viewModelScope?.cancel(); dispatcher.scheduler.runCurrent(); Dispatchers.resetMain() }

    @Test fun `pending wallet publishes unavailable before any remote membership work`() = runTest(dispatcher) {
        val h = Harness(this); h.chains.emit(h.a); h.collectStates()
        assertEquals(listOf<StakingState?>(null), h.values); assertEquals(0, h.remoteStarts)
    }
    @Test fun `pending chain publishes unavailable before any remote membership work`() = runTest(dispatcher) {
        val h = Harness(this); h.accounts.emit(h.wallet); h.collectStates()
        assertEquals(listOf<StakingState?>(null), h.values); assertEquals(0, h.remoteStarts)
    }
    @Test fun `both metadata prerequisites pending still publish unavailable`() = runTest(dispatcher) {
        val h = Harness(this); h.collectStates()
        assertEquals(listOf<StakingState?>(null), h.values); assertEquals(0, h.remoteStarts)
    }
    @Test fun `pending metadata is never confirmed nonmembership`() = runTest(dispatcher) {
        val h = Harness(this); h.collectStates()
        assertTrue(h.values.isNotEmpty()); assertTrue(h.values.all { it == null })
        h.chains.emit(h.a); runCurrent(); assertTrue(h.values.all { it == null }); assertEquals(0, h.remoteStarts)
    }
    @Test fun `completing pending wallet restores exact confirmed nonmember only after remote result`() = runTest(dispatcher) {
        val h = Harness(this); h.chains.emit(h.a); h.collectStates()
        h.accounts.emit(h.wallet); runCurrent(); assertTrue(h.values.all { it == null }); assertEquals(1, h.remoteStarts)
        h.members.emit(Result.success(null)); runCurrent()
        val state = h.values.last() as StakingState.Pool.None
        assertSame(h.a, state.chain); assertTrue(state.accountId.contentEquals(h.account))
    }
    @Test fun `wallet completion observes latest chain rather than cancelled earlier selection`() = runTest(dispatcher) {
        val h = Harness(this); h.chains.emit(h.a); h.collectStates()
        h.chains.emit(h.b); runCurrent(); h.accounts.emit(h.wallet); runCurrent()
        assertEquals(listOf(h.b.id), h.remoteChains)
        h.members.emit(Result.success(null)); runCurrent(); assertSame(h.b, h.values.last()?.chain)
    }
    @Test fun `cancelling while metadata is pending releases both cold collectors`() = runTest(dispatcher) {
        val h = Harness(this); val owner = h.collectStates(); assertEquals(1, h.chainCollectors); assertEquals(1, h.accountCollectors)
        owner.cancelAndJoin(); runCurrent()
        assertEquals(0, h.chainCollectors); assertEquals(0, h.accountCollectors); assertEquals(0, h.remoteStarts)
    }
    @Test fun `unsupported chain remains unavailable after pending metadata completes`() = runTest(dispatcher) {
        val h = Harness(this); h.collectStates(); h.chains.emit(h.unsupported); h.accounts.emit(h.wallet); runCurrent()
        assertTrue(h.values.isNotEmpty()); assertTrue(h.values.all { it == null }); assertEquals(0, h.remoteStarts)
    }
    @Test fun `actual pool scenario emits unavailable while wallet metadata is pending`() = runTest(dispatcher) {
        val h = Harness(this); h.chains.emit(h.a); val bodies = h.collectBodies()
        assertEquals(listOf<StakingViewState?>(null), bodies); assertEquals(0, h.remoteStarts)
    }
    @Test fun `actual pool scenario emits unavailable while both metadata sources are pending`() = runTest(dispatcher) {
        val h = Harness(this); val bodies = h.collectBodies()
        assertEquals(listOf<StakingViewState?>(null), bodies); assertEquals(0, h.remoteStarts)
    }
    @Test fun `actual root publishes unavailable from pool source before pending wallet metadata`() = runTest(dispatcher) {
        val h = Harness(this); h.chains.emit(h.a)
        val publications = h.startRoot(); runCurrent()
        assertTrue("Public event must be initialized by production publication", root!!.showManageStakeEvent.isInitialized)
        assertTrue("Observer must see terminal source qualification, not only the initial clear",
            publications.any { it.contains("publishStakingBody") && it.contains("qualifyStakingBody") })
        assertNull(root!!.showManageStakeEvent.value); assertEquals(0, h.remoteStarts)
    }
    @Test fun `actual root does not invent a completed scenario before selection arrives`() = runTest(dispatcher) {
        val h = Harness(this); val publications = h.startRoot(selectionReady = false); runCurrent()
        assertFalse(root!!.showManageStakeEvent.isInitialized); assertTrue(publications.isEmpty()); assertEquals(0, h.remoteStarts)
    }

    private inner class Harness(private val scope: TestScope) {
        val account = ByteArray(32) { 3 }
        val a = chain("pool-a"); val b = chain("pool-b"); val unsupported = chain("no-pool", false)
        val chains = MutableSharedFlow<Chain>(replay = 1)
        val accounts = MutableSharedFlow<MetaAccount>(replay = 1)
        val members = MutableSharedFlow<Result<PoolMember?>>(replay = 1)
        var chainCollectors = 0; var accountCollectors = 0; var remoteStarts = 0
        val remoteChains = mutableListOf<String>(); val values = mutableListOf<StakingState?>()
        val wallet = mock<MetaAccount> { on { id } doReturn 7L; on { chainAccounts } doReturn emptyMap(); on { substrateAccountId } doReturn account }
        private val config = mock<ChainAsset> {
            on { chainId } doReturn "pool-a"; on { id } doReturn "native"; on { symbol } doReturn "LOCAL"; on { precision } doReturn 2
            on { supportStakingPool } doReturn true; on { staking } doReturn ChainAsset.StakingType.RELAYCHAIN
            on { chainName } doReturn "Local"; on { iconUrl } doReturn ""
        }
        private val assets = MutableStateFlow(Asset.createEmpty(config, 7, account, minSupportedVersion = null))
        private val trackedChains = flow { chainCollectors++; try { emitAll(chains) } finally { chainCollectors-- } }
        private val trackedAccounts = flow { accountCollectors++; try { emitAll(accounts) } finally { accountCollectors-- } }
        private val selection = mock<StakingInteractor> {
            on { selectedChainFlow() } doReturn trackedChains
            on { currentAssetFlow() } doReturn assets
            on { selectionStateFlow() } doReturn emptyFlow()
            on { selectedAccountProjectionFlow() } doReturn emptyFlow()
            onBlocking { getCurrentMetaAccount() } doReturn wallet
        }
        private val repository = mock<AccountRepository> { on { selectedMetaAccountFlow() } doReturn trackedAccounts }
        private val data = mock<StakingPoolDataSource> {
            on { observePoolMembers(any(), any()) } doAnswer { call ->
                flow { remoteStarts++; remoteChains += call.getArgument<String>(0); emitAll(members) }
            }
        }
        val pool = StakingPoolInteractor(mock(), data, selection, mock(), repository, mock(), mock(), mock())
        private val resource = mock<ResourceManager> {
            on { getString(any()) } doReturn "Public label"
            on { getString(any(), anyVararg()) } doReturn "Public label"
        }
        fun collectStates(): Job = scope.backgroundScope.launch { pool.stakingStateFlow().collect { values += it } }.also { scope.runCurrent() }
        suspend fun collectBodies(): List<StakingViewState?> {
            val base = mock<BaseStakingViewModel> { on { stakingStateScope } doReturn scope.backgroundScope; on { enteredAmountEvent } doReturn emptyFlow() }
            val vm = StakingPoolViewModel(pool, selection, resource, mock(), base)
            val bodies = mutableListOf<StakingViewState?>()
            scope.backgroundScope.launch { vm.getStakingViewStateFlow().collect { bodies += it } }; scope.runCurrent(); return bodies
        }
        fun startRoot(selectionReady: Boolean = true): List<String> {
            val selectedItem = StakingAssetSelection.Pool(a.id, config.id)
            val selections = MutableSharedFlow<StakingAssetSelection>(replay = 1).apply { if (selectionReady) tryEmit(selectedItem) }
            val revision = StakingSelectionRevision().apply { if (selectionReady) changed(selectedItem) }
            val shared = mock<StakingSharedState> {
                on { selectionRevision } doReturn revision; on { selectionItem } doReturn selections
                on { settledSelection } doReturn selections
                on { currentAssetFlow() } doReturn assets
                on { assetWithChain } doReturn flowOf(SingleAssetSharedState.AssetWithChain(a, config))
            }
            val updates = mock<UpdateSystem> { on { start() } doReturn emptyFlow() }
            root = StakingViewModel(selection, mock(), mock(), mock(), resource, mock(), updates, shared,
                mock(), mock(), mock(), mock(), pool, StakingPoolSharedStateProvider(), mock(), mock(), mock(), mock(), mock())
            val stacks = mutableListOf<String>()
            root!!.showManageStakeEvent.observeForever(Observer<Event<ManageStakingBottomSheet.Payload>?> {
                stacks += Thread.currentThread().stackTrace.joinToString("\n") { it.methodName }
            })
            return stacks
        }
        private fun chain(id: String, supports: Boolean = true) = mock<Chain> {
            on { this.id } doReturn id; on { supportStakingPool } doReturn supports
            on { ecosystem } doReturn Ecosystem.Substrate; on { addressPrefix } doReturn 42
        }
    }
}
