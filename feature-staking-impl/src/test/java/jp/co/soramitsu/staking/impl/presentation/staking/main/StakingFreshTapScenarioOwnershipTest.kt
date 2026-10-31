package jp.co.soramitsu.staking.impl.presentation.staking.main

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.common.data.storage.WalletSelectionEpoch
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.state.SingleAssetSharedState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.validation.ValidationExecutor
import jp.co.soramitsu.core.updater.UpdateSystem
import jp.co.soramitsu.staking.api.data.StakingAssetSelection
import jp.co.soramitsu.staking.api.data.StakingSelectionRevision
import jp.co.soramitsu.staking.api.data.StakingSharedState
import jp.co.soramitsu.staking.api.data.StakingType
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.staking.impl.domain.rewards.RewardCalculator
import jp.co.soramitsu.staking.impl.domain.rewards.RewardCalculatorFactory
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.common.SetupStakingProcess
import jp.co.soramitsu.staking.impl.presentation.common.SetupStakingSharedState
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolSharedStateProvider
import jp.co.soramitsu.staking.impl.presentation.staking.main.scenarios.StakingRelaychainScenarioViewModel
import jp.co.soramitsu.staking.impl.presentation.staking.main.scenarios.StakingScenarioViewModel
import jp.co.soramitsu.staking.impl.scenarios.relaychain.StakingRelayChainScenarioInteractor
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
import java.math.BigDecimal
import jp.co.soramitsu.core.models.Asset as ChainAsset

/**
 * Baseline against 489492b57. Actual root + relay + ValidationExecutor.
 * The controlled replay source models selection intent advancing before the asynchronous
 * selectionItem/scenario publication. It does NOT prove the production flow scheduler order.
 * First execute these unchanged at the frozen baseline; a separate real StakingSharedState
 * held-preference-flow control should establish that lower-boundary lag independently.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StakingFreshTapScenarioOwnershipTest {
    @get:Rule val instant = InstantTaskExecutorRule()
    private val dispatcher = StandardTestDispatcher()
    private val revision = StakingSelectionRevision()
    private var observedChain = "chain-a"
    private val publicAccount = ByteArray(32) { (it + 1).toByte() }
    private var currentMeta = meta(7, publicAccount)
    private val chainMetadata = MutableSharedFlow<SingleAssetSharedState.AssetWithChain>(replay = 1)
    private fun meta(wallet: Long, account: ByteArray) = mock<MetaAccount> {
        on { id } doReturn wallet
        on { chainAccounts } doReturn emptyMap()
        on { substrateAccountId } doReturn account
    }
    private val assets = MutableSharedFlow<Asset>(replay = 1)
    private val scenarioAssets = MutableSharedFlow<Asset>(replay = 1)
    private val selected = mock<StakingSharedState> {
        on { selectionRevision } doReturn revision
        // Root presentation initialization is intentionally unrelated to this action seam.
        on { selectionItem } doReturn emptyFlow()
        on { settledSelection } doReturn emptyFlow()
        on { currentAssetFlow() } doReturn assets
        on { assetWithChain } doReturn chainMetadata
        onBlocking { chainId() } doAnswer { observedChain }
    }
    private val interactor = mock<StakingInteractor> {
        onBlocking { getCurrentMetaAccount() } doAnswer { currentMeta }
        on { currentAssetFlow() } doReturn scenarioAssets
        on { selectionStateFlow() } doReturn emptyFlow()
        on { selectedChainFlow() } doReturn emptyFlow()
        on { selectedAccountProjectionFlow() } doReturn emptyFlow()
    }
    private val resources = mock<ResourceManager> {
        on { getString(any()) } doAnswer { "text-${it.getArgument<Int>(0)}" }
        on { getString(any(), anyVararg()) } doAnswer { "text-${it.getArgument<Int>(0)}" }
    }
    private val router = mock<StakingRouter>()
    private val setupProcess = MutableStateFlow<SetupStakingProcess>(SetupStakingProcess.Initial(StakingType.RELAYCHAIN))
    private val setupState = mock<SetupStakingSharedState> { on { setupStakingProcess } doReturn setupProcess }
    private val executor = ValidationExecutor(resources)
    private val relay = mock<StakingRelayChainScenarioInteractor> {
        on { stakingStateFlow() } doReturn emptyFlow()
        onBlocking { maxNumberOfStakesIsReached(any()) } doReturn false
    }
    private val calculator = mock<RewardCalculator> {
        onBlocking { calculateMaxAPY(any()) } doReturn BigDecimal("0.12")
        onBlocking { calculateAvgAPY() } doReturn BigDecimal("0.08")
    }
    private val rewardFactory = mock<RewardCalculatorFactory> {
        onBlocking { create(any()) } doReturn calculator
    }
    private val updateSystem = mock<UpdateSystem> { on { start() } doReturn emptyFlow() }
    private lateinit var vm: StakingViewModel
    private lateinit var scenario: StakingRelaychainScenarioViewModel

    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() {
        if (::vm.isInitialized) runTest(dispatcher) { vm.viewModelScope.coroutineContext[Job]!!.cancelAndJoin() }
        Dispatchers.resetMain()
    }

    @Test fun `initial coherent relay action uses current chain and displayed amount`() = runTest(dispatcher) {
        createRoot(); performStart()
        verify(relay).maxNumberOfStakesIsReached("chain-a")
        assertOneSetup()
    }

    @Test fun `fresh tap after parachain intent cannot use old relay replay`() = runTest(dispatcher) {
        createRoot()
        revision.changed(StakingAssetSelection.ParachainStaking("chain-b", "native"))
        performStart()
        verify(relay, never()).maxNumberOfStakesIsReached(any())
        assertNoSetup()
    }

    @Test fun `fresh tap after relay chain intent cannot validate old chain metadata`() = runTest(dispatcher) {
        createRoot()
        revision.changed(StakingAssetSelection.RelayChainStaking("chain-b", "native"))
        performStart()
        verify(relay, never()).maxNumberOfStakesIsReached("chain-a")
        assertNoSetup()
    }

    @Test fun `fresh coherent new relay selection remains usable`() = runTest(dispatcher) {
        createRoot()
        revision.changed(StakingAssetSelection.RelayChainStaking("chain-b", "native"))
        observedChain = "chain-b"
        publishAsset("chain-b", "native")
        performStart()
        verify(relay, never()).maxNumberOfStakesIsReached("chain-a")
        verify(relay).maxNumberOfStakesIsReached("chain-b")
        assertOneSetup()
    }

    @Test fun `fresh rewards tap cannot quote old chain after new intent`() = runTest(dispatcher) {
        createRoot()
        revision.changed(StakingAssetSelection.RelayChainStaking("chain-b", "native"))
        performReward()
        verify(rewardFactory, never()).create(any())
        assertNull(vm.showRewardEstimationEvent.value)
    }

    @Test fun `fresh rewards tap cannot quote old asset under same chain`() = runTest(dispatcher) {
        createRoot()
        revision.changed(StakingAssetSelection.RelayChainStaking("chain-a", "other-asset"))
        performReward()
        verify(rewardFactory, never()).create(any())
        assertNull(vm.showRewardEstimationEvent.value)
    }

    @Test fun `fresh coherent new reward selection publishes only matching quote`() = runTest(dispatcher) {
        createRoot()
        revision.changed(StakingAssetSelection.RelayChainStaking("chain-b", "native"))
        observedChain = "chain-b"
        val fresh = publishAsset("chain-b", "native")
        performReward()
        verify(rewardFactory).create(fresh.token.configuration)
        verify(calculator).calculateMaxAPY("chain-b")
        assertNotNull(vm.showRewardEstimationEvent.value)
        verify(setupState, never()).set(any()); verify(router, never()).openSetupStaking()
    }

    @Test fun `fresh start binds metadata to coherent asset despite lagging chain holder`() = runTest(dispatcher) {
        createRoot()
        revision.changed(StakingAssetSelection.RelayChainStaking("chain-b", "native"))
        publishAsset("chain-b", "native")
        performStart()
        verify(relay, never()).maxNumberOfStakesIsReached("chain-a")
        verify(relay).maxNumberOfStakesIsReached("chain-b")
        assertOneSetup()
    }

    @Test fun `fresh reward uses selected asset when scenario asset replay lags`() = runTest(dispatcher) {
        createRoot()
        revision.changed(StakingAssetSelection.RelayChainStaking("chain-b", "native"))
        val fresh = publishAsset("chain-b", "native", publishToScenario = false)
        performReward()
        verify(rewardFactory).create(fresh.token.configuration)
        verify(calculator).calculateMaxAPY("chain-b")
        verify(calculator, never()).calculateMaxAPY("chain-a")
        assertNotNull(vm.showRewardEstimationEvent.value)
    }

    @Test fun `fresh relay start rejects asset configured for another staking type`() = runTest(dispatcher) {
        createRoot()
        revision.changed(StakingAssetSelection.RelayChainStaking("chain-a", "native"))
        publishAsset("chain-a", "native", type = ChainAsset.StakingType.PARACHAIN)
        performStart()
        verify(relay, never()).maxNumberOfStakesIsReached(any())
        assertNoSetup()
    }

    @Test fun `fresh pool rewards intent cannot use cached relay scenario`() = runTest(dispatcher) {
        createRoot()
        revision.changed(StakingAssetSelection.Pool("chain-a", "native"))
        performReward()
        verify(rewardFactory, never()).create(any())
        assertNull(vm.showRewardEstimationEvent.value)
    }

    @Test fun `fresh wallet tap cannot quote previous wallet cached asset`() = runTest(dispatcher) {
        createRoot()
        WalletSelectionEpoch.beginChange()
        currentMeta = meta(8, ByteArray(32) { 8 })
        WalletSelectionEpoch.endChange()
        performReward()
        verify(rewardFactory, never()).create(any())
        assertNull(vm.showRewardEstimationEvent.value)
    }

    @Test fun `fresh wallet tap cannot prepare previous wallet cached asset`() = runTest(dispatcher) {
        createRoot()
        WalletSelectionEpoch.beginChange()
        currentMeta = meta(8, ByteArray(32) { 8 })
        WalletSelectionEpoch.endChange()
        performStart()
        verify(relay, never()).maxNumberOfStakesIsReached(any())
        assertNoSetup()
    }

    @Test fun `fresh same-wallet changed account cannot authorize old account asset`() = runTest(dispatcher) {
        createRoot()
        WalletSelectionEpoch.beginChange()
        currentMeta = meta(7, ByteArray(32) { 9 })
        WalletSelectionEpoch.endChange()
        performStart()
        verify(relay, never()).maxNumberOfStakesIsReached(any())
        assertNoSetup()
    }

    @Test fun `fresh coherent wallet remains usable after cache catches up`() = runTest(dispatcher) {
        createRoot()
        val account = ByteArray(32) { 8 }
        WalletSelectionEpoch.beginChange()
        currentMeta = meta(8, account)
        WalletSelectionEpoch.endChange()
        publishAsset("chain-a", "native", wallet = 8, account = account)
        performStart()
        verify(relay).maxNumberOfStakesIsReached("chain-a")
        assertOneSetup()
    }

    @Test fun `fresh coherent changed account remains usable for rewards`() = runTest(dispatcher) {
        createRoot()
        val account = ByteArray(32) { 9 }
        WalletSelectionEpoch.beginChange()
        currentMeta = meta(7, account)
        WalletSelectionEpoch.endChange()
        val fresh = publishAsset("chain-a", "native", account = account)
        performReward()
        verify(rewardFactory).create(fresh.token.configuration)
        assertNotNull(vm.showRewardEstimationEvent.value)
    }

    @Test fun `fresh action rejects chain metadata that still belongs to previous asset`() = runTest(dispatcher) {
        createRoot()
        val previous = chainMetadata.replayCache.single()
        revision.changed(StakingAssetSelection.RelayChainStaking("chain-b", "native"))
        publishAsset("chain-b", "native")
        check(chainMetadata.tryEmit(previous))
        performStart()
        verify(relay, never()).maxNumberOfStakesIsReached(any())
        assertNoSetup()
    }


    @Test fun `fresh start rejects authoritative unsupported staking metadata with same id`() = runTest(dispatcher) {
        createRoot()
        replaceMetadata(type = ChainAsset.StakingType.UNSUPPORTED)
        performStart()
        verify(relay, never()).maxNumberOfStakesIsReached(any())
        assertNoSetup()
    }

    @Test fun `fresh reward rejects authoritative changed staking type with same id`() = runTest(dispatcher) {
        createRoot()
        replaceMetadata(type = ChainAsset.StakingType.PARACHAIN)
        performReward()
        verify(rewardFactory, never()).create(any())
        assertNull(vm.showRewardEstimationEvent.value)
    }

    @Test fun `fresh action rejects metadata asset chain id disagreement`() = runTest(dispatcher) {
        createRoot()
        replaceMetadata(chainId = "chain-b")
        performStart()
        verify(relay, never()).maxNumberOfStakesIsReached(any())
        assertNoSetup()
    }

    @Test fun `fresh pool reward rejects authoritative disabled pool capability`() = runTest(dispatcher) {
        createRoot()
        publishAsset("chain-a", "native", pool = true)
        val poolScenario = mock<StakingScenarioViewModel> { on { stakingType } doReturn StakingType.POOL }
        StakingViewModel::class.java.getDeclaredField("scenarioViewModelFlow").apply { isAccessible = true }
            .set(vm, MutableStateFlow<StakingScenarioViewModel>(poolScenario))
        revision.changed(StakingAssetSelection.Pool("chain-a", "native"))
        replaceMetadata(pool = false)
        performReward()
        verify(poolScenario, never()).rewardEstimationPayload(any())
        assertNull(vm.showRewardEstimationEvent.value)
    }

    private fun replaceMetadata(type: ChainAsset.StakingType = ChainAsset.StakingType.RELAYCHAIN, chainId: String = "chain-a", pool: Boolean = false) {
        val original = chainMetadata.replayCache.single()
        val config = mock<ChainAsset> {
            on { this.chainId } doReturn chainId
            on { id } doReturn "native"
            on { staking } doReturn type
            on { supportStakingPool } doReturn pool
        }
        check(chainMetadata.tryEmit(SingleAssetSharedState.AssetWithChain(original.chain, config)))
    }

    private suspend fun TestScope.createRoot() {
        vm = StakingViewModel(interactor, mock(), mock(), router, resources, executor, updateSystem, selected,
            mock(), relay, rewardFactory, setupState, mock(), StakingPoolSharedStateProvider(), mock(), mock(), mock(), mock(), mock())
        scenario = StakingRelaychainScenarioViewModel(interactor, relay, resources, vm, mock(), mock(), mock(),
            selected, rewardFactory, setupState, router, executor, mock())
        scenario.enteredAmountFlow.value = BigDecimal("7")
        StakingViewModel::class.java.getDeclaredField("scenarioViewModelFlow").apply { isAccessible = true }
            .set(vm, MutableStateFlow<StakingScenarioViewModel>(scenario))
        publishAsset("chain-a", "native")
        runCurrent()
    }

    private fun publishAsset(chain: String, id: String, publishToScenario: Boolean = true, type: ChainAsset.StakingType = ChainAsset.StakingType.RELAYCHAIN, wallet: Long = 7, account: ByteArray = publicAccount, pool: Boolean = false): Asset {
        val config = mock<ChainAsset> {
            on { chainId } doReturn chain
            on { this.id } doReturn id
            on { symbol } doReturn "LOCAL"
            on { precision } doReturn 10
            on { staking } doReturn type
            on { supportStakingPool } doReturn pool
        }
        val chainModel = mock<Chain> {
            on { this.id } doReturn chain
            on { ecosystem } doReturn Ecosystem.Substrate
            on { addressPrefix } doReturn 42
        }
        check(chainMetadata.tryEmit(SingleAssetSharedState.AssetWithChain(chainModel, config)))
        return Asset.createEmpty(config, wallet, account, minSupportedVersion = null)
            .also { check(assets.tryEmit(it)); if (publishToScenario) check(scenarioAssets.tryEmit(it)) }
    }

    private suspend fun TestScope.performStart() = perform { vm.startNonPoolStakingClick { true } }
    private suspend fun TestScope.performReward() = perform { vm.onEstimatedEarningsInfoClick { true } }
    private suspend fun TestScope.perform(action: () -> Unit) {
        val parent = vm.viewModelScope.coroutineContext[Job]!!
        val before = parent.children.toSet()
        action()
        val actions = parent.children.filter { it !in before }.toList()
        runCurrent()
        withContext(Dispatchers.Default) { withTimeout(5_000) { actions.joinAll() } }
        runCurrent()
    }
    private fun assertNoSetup() {
        verify(setupState, never()).set(any()); verify(router, never()).openSetupStaking()
    }
    private fun assertOneSetup() {
        verify(setupState).set(check {
            assertTrue(it is SetupStakingProcess.SetupStep.Stash)
            assertEquals(BigDecimal("7"), (it as SetupStakingProcess.SetupStep.Stash).amount)
        })
        verify(router).openSetupStaking()
    }
}
