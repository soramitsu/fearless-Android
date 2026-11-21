package jp.co.soramitsu.staking.impl.presentation.staking.main

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.common.data.network.subquery.SubsquidCollatorsApyResponse
import jp.co.soramitsu.common.data.network.subquery.SubsquidResponse
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.formatAsPercentage
import jp.co.soramitsu.common.validation.ValidationExecutor
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.core.updater.UpdateSystem
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.state.SingleAssetSharedState
import jp.co.soramitsu.staking.api.data.*
import jp.co.soramitsu.staking.api.domain.api.StakingRepository
import jp.co.soramitsu.staking.api.domain.model.StakingState
import jp.co.soramitsu.staking.impl.data.network.subquery.StakingApi
import jp.co.soramitsu.staking.impl.data.network.subquery.request.SubsquidCollatorsApyRequest
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.staking.impl.domain.rewards.RewardCalculatorFactory
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.common.*
import jp.co.soramitsu.staking.impl.presentation.staking.main.scenarios.StakingParachainScenarioViewModel
import jp.co.soramitsu.staking.impl.presentation.staking.main.scenarios.StakingScenarioViewModel
import jp.co.soramitsu.staking.impl.scenarios.parachain.StakingParachainScenarioInteractor
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.mockito.kotlin.*
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.core.models.Asset as ChainAsset

/** Actual root + parachain scenario + factory + SubqueryRewardCalculator.
 * Only metadata/state/API edges are local fixtures. No Retrofit/network call is performed.
 * Baseline 12dcb4db3 reproduces the stale chain and average; corrected source retains these assertions.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StakingParachainRewardContextTest {
    @get:Rule val instant = InstantTaskExecutorRule()
    private val dispatcher = StandardTestDispatcher()
    private val revision = StakingSelectionRevision()
    private val account = ByteArray(32) { (it + 1).toByte() }
    private val meta = mock<MetaAccount> {
        on { id } doReturn 7L
        on { chainAccounts } doReturn emptyMap()
        on { substrateAccountId } doReturn account
    }
    private val assets = MutableSharedFlow<Asset>(replay = 1)
    private val metadata = MutableSharedFlow<SingleAssetSharedState.AssetWithChain>(replay = 1)
    private val states = MutableSharedFlow<StakingState>(replay = 1)
    private val selected = mock<StakingSharedState> {
        on { selectionRevision } doReturn revision
        on { selectionItem } doReturn emptyFlow()
        on { settledSelection } doReturn emptyFlow()
        on { currentAssetFlow() } doReturn assets
        on { assetWithChain } doReturn metadata
    }
    private val interactor = mock<StakingInteractor> {
        onBlocking { getCurrentMetaAccount() } doReturn meta
        on { currentAssetFlow() } doReturn assets
        on { selectionStateFlow() } doReturn emptyFlow()
        on { selectedChainFlow() } doReturn emptyFlow()
        on { selectedAccountProjectionFlow() } doReturn emptyFlow()
    }
    private val resources = mock<ResourceManager> {
        on { getString(any()) } doAnswer { "text-${it.getArgument<Int>(0)}" }
        on { getString(any(), anyVararg()) } doAnswer { "text-${it.getArgument<Int>(0)}" }
    }
    private val router = mock<StakingRouter>()
    private val setupState = mock<SetupStakingSharedState> {
        on { setupStakingProcess } doReturn MutableStateFlow<SetupStakingProcess>(SetupStakingProcess.Initial(StakingType.PARACHAIN))
    }
    private val para = mock<StakingParachainScenarioInteractor> {
        on { stakingStateFlow() } doReturn states
    }
    private val repository = mock<StakingRepository> {
        onBlocking { getTotalIssuance(any()) } doAnswer {
            if (it.getArgument<String>(0) == "chain-a") BigInteger("1000") else BigInteger("2000")
        }
    }
    private val calls = mutableListOf<String>()
    private val api = mock<StakingApi> {
        onBlocking { getCollatorsApy(any(), any<SubsquidCollatorsApyRequest>()) } doAnswer {
            val url = it.getArgument<String>(0); calls += url
            SubsquidResponse(SubsquidCollatorsApyResponse(listOf(
                SubsquidCollatorsApyResponse.CollatorApyElement("01", if (url.endsWith("chain-a")) BigDecimal("13") else BigDecimal("29"))
            )))
        }
    }
    private val factory = RewardCalculatorFactory(mock(), repository, mock(), para, api)
    private val updateSystem = mock<UpdateSystem> { on { start() } doReturn emptyFlow() }
    private lateinit var vm: StakingViewModel

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        // Real suspension before returning Result avoids Mockito's immediate value-class boxing trap.
        runTest(dispatcher) {
            whenever(para.total(any())).doSuspendableAnswer {
                coroutineScope {
                    val response = CompletableDeferred<Result<BigInteger>>()
                    launch { yield(); response.complete(Result.success(BigInteger("500"))) }
                    response.await()
                }
            }
        }
    }
    @After fun teardown() {
        if (::vm.isInitialized) runTest(dispatcher) { vm.viewModelScope.coroutineContext[Job]!!.cancelAndJoin() }
        Dispatchers.resetMain()
    }

    @Test fun `coherent B actual calculator publishes only B quote`() = runTest(dispatcher) {
        createRoot("chain-b")
        seedAverage("chain-b")
        performReward()
        assertEquals(listOf(url("chain-b")), calls)
        val payload = requireNotNull(vm.showRewardEstimationEvent.value?.peekContent())
        assertEquals(BigDecimal("29").formatAsPercentage(), payload.apr)
        assertEquals(BigDecimal("10").formatAsPercentage(), payload.apy)
    }

    @Test fun `fresh B intent must not query replayed A parachain state`() = runTest(dispatcher) {
        createRoot("chain-a")
        seedAverage("chain-b")
        performReward()
        assertFalse("Captured B may never query A", calls.contains(url("chain-a")))
        vm.showRewardEstimationEvent.value?.peekContent()?.let {
            assertEquals(BigDecimal("29").formatAsPercentage(), it.apr)
        }
    }

    @Test fun `coherent B quote cannot publish average cached from A`() = runTest(dispatcher) {
        createRoot("chain-b")
        seedAverage("chain-a")
        performReward()
        assertEquals(listOf(url("chain-b")), calls)
        // Withholding an unproven average is safe; publishing A as B is not.
        vm.showRewardEstimationEvent.value?.peekContent()?.let {
            assertEquals(BigDecimal("10").formatAsPercentage(), it.apy)
        }
    }

    private suspend fun seedAverage(chain: String) {
        factory.createSubquery().calculateReturns(BigDecimal.ONE, 30, false, chain)
    }
    private fun chain(id: String, config: ChainAsset) = mock<Chain> {
        on { this.id } doReturn id
        on { ecosystem } doReturn Ecosystem.Substrate
        on { addressPrefix } doReturn 42
        on { assets } doReturn listOf(config)
        on { externalApi } doReturn Chain.ExternalApi(
            Chain.ExternalApi.Section(Chain.ExternalApi.Section.Type.SUBSQUID, url(id)), null, null
        )
    }
    private suspend fun TestScope.createRoot(observedStateChain: String) {
        val config = mock<ChainAsset> {
            on { chainId } doReturn "chain-b"
            on { id } doReturn "native"
            on { symbol } doReturn "LOCAL"
            on { precision } doReturn 10
            on { staking } doReturn ChainAsset.StakingType.PARACHAIN
        }
        val selectedChain = chain("chain-b", config)
        check(metadata.tryEmit(SingleAssetSharedState.AssetWithChain(selectedChain, config)))
        check(assets.tryEmit(Asset.createEmpty(config, 7, account, minSupportedVersion = null)))
        check(states.tryEmit(StakingState.Parachain.None(chain(observedStateChain, config), account)))
        revision.changed(StakingAssetSelection.ParachainStaking("chain-b", "native"))
        vm = StakingViewModel(interactor, mock(), mock(), router, resources, ValidationExecutor(resources), updateSystem,
            selected, mock(), mock(), factory, setupState, mock(), StakingPoolSharedStateProvider(), mock(), mock(), mock(), mock(), mock())
        val scenario = StakingParachainScenarioViewModel(interactor, para, resources, vm, mock(), mock(), factory, setupState, router)
        StakingViewModel::class.java.getDeclaredField("scenarioViewModelFlow").apply { isAccessible = true }
            .set(vm, MutableStateFlow<StakingScenarioViewModel>(scenario))
        runCurrent()
    }
    private suspend fun TestScope.performReward() {
        val parent = vm.viewModelScope.coroutineContext[Job]!!
        val before = parent.children.toSet()
        vm.onEstimatedEarningsInfoClick { true }
        val actions = parent.children.filter { it !in before }.toList()
        runCurrent()
        withContext(Dispatchers.Default) { withTimeout(5_000) { actions.joinAll() } }
        runCurrent()
        verify(setupState, never()).set(any())
        verify(router, never()).openSetupStaking()
    }
    private fun url(chain: String) = "https://local.invalid/$chain"
}
