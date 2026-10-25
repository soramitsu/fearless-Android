package jp.co.soramitsu.staking.impl.presentation.staking.main.scenarios

import jp.co.soramitsu.wallet.impl.domain.model.AssetAmountState
import java.math.BigInteger
import java.util.concurrent.atomic.AtomicInteger
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.staking.api.data.StakingSharedState
import jp.co.soramitsu.staking.api.domain.model.StakingState
import jp.co.soramitsu.staking.impl.data.repository.StakingConstantsRepository
import jp.co.soramitsu.staking.impl.data.repository.StakingRewardsRepository
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.staking.impl.scenarios.relaychain.StakingRelayChainScenarioInteractor
import jp.co.soramitsu.staking.impl.scenarios.relaychain.StakingRelayChainScenarioRepository
import jp.co.soramitsu.staking.impl.presentation.staking.main.StakingViewState
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever
import jp.co.soramitsu.core.models.Asset as ChainAsset

/** Real lower summary and real relay body, with public independent asset observations.
 * Only state selection is supplied; summary conversion, suspension and body mapping execute production code.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RelayStakingSummaryContextTest {
    @Test fun `coherent actual lower summary renders current body`() = runTest {
        val h = Harness(this)
        try {
            h.start(); h.awaitSummary()
            assertTrue(h.body()!!.stakeInfoViewState.staked.value!!.contains("2"))
            assertTrue(h.body()!!.stakeInfoViewState.staked.value!!.contains("BBB"))
            assertTrue(h.errors.isEmpty())
        } finally { h.close() }
    }

    @Test fun `actual lower summary from wrong chain cannot be relabelled as current body`() = runTest {
        val h = Harness(this)
        try {
            h.lowerAssets.value = h.asset(h.a, bonded = 9900)
            h.start(); h.awaitSummary()
            assertNull(h.values.last())
            assertTrue(h.values.none { it is StakingViewState.RelayChain.Stake })
            h.recoverCurrent(); assertTrue(h.errors.isEmpty())
        } finally { h.close() }
    }

    @Test fun `actual lower summary from wrong account cannot be relabelled as current body`() = runTest {
        val h = Harness(this)
        try {
            h.lowerAssets.value = h.asset(h.b, account = ByteArray(32) { 2 }, bonded = 9900)
            h.start(); h.awaitSummary()
            assertNull(h.values.last())
            assertTrue(h.values.none { it is StakingViewState.RelayChain.Stake })
            h.recoverCurrent(); assertTrue(h.errors.isEmpty())
        } finally { h.close() }
    }

    @Test fun `actual lower non relay asset cannot supply current relay summary`() = runTest {
        val h = Harness(this)
        try {
            h.lowerAssets.value = h.asset(h.b, type = ChainAsset.StakingType.PARACHAIN, bonded = 9900)
            h.start(); h.awaitSummary()
            assertNull(h.values.last())
            assertTrue(h.values.none { it is StakingViewState.RelayChain.Stake })
            h.recoverCurrent(); assertTrue(h.errors.isEmpty())
        } finally { h.close() }
    }

    @Test fun `actual lower other wallet summary cannot supply same account current wallet body`() = runTest {
        val h = Harness(this)
        try {
            h.lowerAssets.value = h.asset(h.b, metaId = 99, bonded = 9900)
            h.start(); h.awaitSummary()
            assertNull(h.values.last())
            assertTrue(h.values.none { it is StakingViewState.RelayChain.Stake })
            h.recoverCurrent(); assertTrue(h.errors.isEmpty())
        } finally { h.close() }
    }

    @Test fun `held actual summary is cancelled when its input becomes incompatible`() = runTest {
        val h = Harness(this)
        try {
            h.start(); h.awaitSummary()
            val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
            whenever(h.repository.getLegacyActiveElectedValidatorsExposures(any())).doSuspendableAnswer {
                entered.complete(Unit); release.await(); emptyMap()
            }
            h.lowerAssets.value = h.asset(h.b, bonded = 300)
            h.until { entered.isCompleted }
            val start = h.values.size
            h.lowerAssets.value = h.asset(h.a, bonded = 9900)
            h.until { h.lowerObserved.get() >= 3 }
            assertNull(h.values.last())
            release.complete(Unit)
            h.awaitSummary(2)
            assertTrue(h.values.drop(start).all { it == null })
            h.recoverCurrent(); assertTrue(h.errors.isEmpty())
        } finally { h.close() }
    }

    @Test fun `matching actual summary update preserves amount and era freshness`() = runTest {
        val h = Harness(this)
        try {
            h.start(); h.awaitSummary()
            h.lowerAssets.value = h.asset(h.b, bonded = 700)
            h.viewAssets.value = h.lowerAssets.value
            h.awaitSummary(2)
            assertTrue(h.body()!!.stakeInfoViewState.staked.value!!.contains("7"))
            assertTrue(h.body()!!.stakeInfoViewState.staked.value!!.contains("BBB"))
            assertTrue(h.errors.isEmpty())
        } finally { h.close() }
    }

    @Test fun `compatible input hides prior actual body while recalculation is held and recovers fresh value`() = runTest {
        val h = Harness(this)
        try {
            h.start(); h.awaitSummary()
            val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
            whenever(h.repository.getLegacyActiveElectedValidatorsExposures(any())).doSuspendableAnswer {
                entered.complete(Unit); release.await(); emptyMap()
            }
            val fresh = h.asset(h.b, bonded = 9900)
            h.viewAssets.value = fresh
            h.lowerAssets.value = fresh
            h.until { entered.isCompleted }
            val heldBody = h.values.last()
            release.complete(Unit)
            h.until { h.body()?.stakeInfoViewState?.staked?.value?.contains("99") == true }
            assertTrue(h.body()!!.stakeInfoViewState.staked.value!!.contains("BBB"))
            assertNull("Old compatible body must not remain while the actual recalculation is held", heldBody)
            assertTrue(h.errors.isEmpty())
        } finally { h.close() }
    }

    @Test fun `fresh body asset cannot reuse old compatible lower summary before lower observation catches up`() = runTest {
        val h = Harness(this)
        try {
            h.start(); h.awaitSummary()
            val start = h.values.size
            val fresh = h.asset(h.b, bonded = 9900)
            h.viewAssets.value = fresh
            h.until { h.viewObserved.get() >= 2 && h.values.size > start }
            val earlyValues = h.values.drop(start).toList()
            h.lowerAssets.value = fresh
            h.until { h.body()?.stakeInfoViewState?.staked?.value?.contains("99") == true }
            assertTrue(h.body()!!.stakeInfoViewState.staked.value!!.contains("BBB"))
            assertTrue("Fresh asset must not reuse a summary calculated from prior bonded input", earlyValues.all { it == null })
            assertTrue(h.errors.isEmpty())
        } finally { h.close() }
    }

    @Test fun `reward recalculation hides prior actual body until fresh reward completes`() = runTest {
        val h = Harness(this)
        try {
            h.start(); h.awaitSummary()
            val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
            whenever(h.repository.getLegacyActiveElectedValidatorsExposures(any())).doSuspendableAnswer {
                entered.complete(Unit); release.await(); emptyMap()
            }
            h.rewardInput.value = BigInteger.valueOf(900)
            h.until { entered.isCompleted }
            val heldBody = h.values.last()
            release.complete(Unit)
            h.until { h.body()?.stakeInfoViewState?.rewarded?.value?.contains("9") == true }
            assertNull("Prior body must be unavailable while reward recalculation waits", heldBody)
            assertTrue(h.body()!!.stakeInfoViewState.staked.value!!.contains("2"))
            assertTrue(h.errors.isEmpty())
        } finally { h.close() }
    }

    @Test fun `era recalculation hides prior actual body until fresh era completes`() = runTest {
        val h = Harness(this)
        try {
            h.start(); h.awaitSummary()
            val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
            whenever(h.repository.getLegacyActiveElectedValidatorsExposures(any())).doSuspendableAnswer {
                entered.complete(Unit); release.await(); emptyMap()
            }
            h.eraInput.value = BigInteger.valueOf(25)
            h.until { entered.isCompleted }
            val heldBody = h.values.last()
            release.complete(Unit)
            h.until { h.lastEra.get() == 25 && h.body() != null }
            assertNull("Prior body must be unavailable while era recalculation waits", heldBody)
            assertTrue(h.body()!!.stakeInfoViewState.staked.value!!.contains("2"))
            assertTrue(h.errors.isEmpty())
        } finally { h.close() }
    }

    @Test fun `equivalent independent input copies remain usable`() = runTest {
        val h = Harness(this)
        try {
            h.start(); h.awaitSummary()
            val calls = h.calculationCalls.get()
            h.viewAssets.value = h.asset(h.b)
            h.lowerAssets.value = h.asset(h.b)
            h.until { h.viewObserved.get() >= 2 && h.calculationCalls.get() > calls && h.body() != null }
            assertTrue(h.body()!!.stakeInfoViewState.staked.value!!.contains("2"))
            assertTrue(h.body()!!.stakeInfoViewState.staked.value!!.contains("BBB"))
            assertTrue(h.errors.isEmpty())
        } finally { h.close() }
    }

    private class Harness(private val test: TestScope) {
        val account = ByteArray(32) { 1 }
        val a = chain("local-summary-a")
        val b = chain("local-summary-b")
        val current = asset(b)
        val lowerAssets = MutableStateFlow(current)
        val viewAssets = MutableStateFlow(current)
        val lowerObserved = AtomicInteger()
        val viewObserved = AtomicInteger()
        val calculationCalls = AtomicInteger()
        val lastEra = AtomicInteger(-1)
        val rewardInput = MutableStateFlow(BigInteger.ZERO)
        val eraInput = MutableStateFlow(BigInteger.valueOf(12))
        private val summaryObserved = AtomicInteger()
        val values = mutableListOf<StakingViewState?>()
        val errors = mutableListOf<Throwable>()
        private val job = SupervisorJob(test.backgroundScope.coroutineContext[Job])
        private val scope = CoroutineScope(test.backgroundScope.coroutineContext + job + CoroutineExceptionHandler { _, e -> errors += e })
        private val lowerShared = mock<StakingSharedState> {
            on { currentAssetFlow() } doReturn lowerAssets.onEach { lowerObserved.incrementAndGet() }.shareIn(scope, SharingStarted.Eagerly, 1)
        }
        private val viewShared = mock<StakingSharedState> {
            on { currentAssetFlow() } doReturn viewAssets.onEach { viewObserved.incrementAndGet() }.shareIn(scope, SharingStarted.Eagerly, 1)
        }
        private val rewards = mock<StakingRewardsRepository> {
            on { totalRewardFlow(any()) } doReturn rewardInput
        }
        private val interactor = StakingInteractor(mock(), mock(), rewards, viewShared, mock(), mock(), mock(), mock())
        val repository = mock<StakingRelayChainScenarioRepository> {
            on { observeActiveEraIndex(any()) } doReturn eraInput
            onBlocking { getLegacyActiveElectedValidatorsExposures(any()) } doAnswer {
                calculationCalls.incrementAndGet()
                emptyMap()
            }
        }
        private val constants = mock<StakingConstantsRepository> {
            onBlocking { maxRewardedNominatorPerValidator(any()) } doReturn 256
        }
        private val real = StakingRelayChainScenarioInteractor(
            interactor, mock(), mock(), constants, repository, rewards, mock(), lowerShared,
            mock(), mock(), mock(), mock(), mock(), mock()
        )
        private val state = MutableStateFlow<StakingState>(StakingState.Stash.None(b, account, account, account))
        private val scenario = spy(real).also {
            doReturn(state).whenever(it).stakingStateFlow()
            doAnswer { call ->
                var lastCompleted: Any? = Any()
                real.observeStashSummary(call.getArgument(0)).onEach { summary ->
                    // Count completed outcomes, not the new pending null. Every event still reaches the actual VM.
                    val input = lowerAssets.value
                    val unavailableInput = input.chainId != b.id || !input.accountId.contentEquals(account) ||
                        input.token.configuration.staking != ChainAsset.StakingType.RELAYCHAIN
                    if ((summary != null || unavailableInput) && summary != lastCompleted) {
                        lastCompleted = summary
                        summary?.let { lastEra.set(it.currentEra) }
                        summaryObserved.incrementAndGet()
                    }
                }
            }.whenever(it).observeStashSummary(any())
        }
        private val resource = mock<ResourceManager> {
            on { getString(any()) } doReturn "Public label"
            on { getString(any(), any<Int>()) } doReturn "Public era"
            on { getString(any(), any<String>()) } doReturn "Public balance"
        }
        private val base = mock<BaseStakingViewModel> {
            on { stakingStateScope } doReturn scope
            on { enteredAmountEvent } doReturn emptyFlow()
        }
        suspend fun start() {
            val vm = StakingRelaychainScenarioViewModel(
                interactor, scenario, resource, base, mock(), mock(), mock(), viewShared,
                mock(), mock(), mock(), mock(), mock()
            )
            scope.launch { vm.getStakingViewStateFlow().collect { values += it } }
            test.runCurrent()
        }
        suspend fun until(condition: () -> Boolean) {
            val deadline = System.nanoTime() + 5_000_000_000L
            while (!condition()) {
                test.runCurrent()
                check(System.nanoTime() < deadline) { "Actual summary deadline; observations=${summaryObserved.get()}, inputs=${lowerObserved.get()}, errors=$errors" }
                withContext(Dispatchers.Default) { delay(5) }
            }
            test.runCurrent()
        }
        suspend fun awaitSummary(count: Int = 1) = until { summaryObserved.get() >= count }
        fun body() = values.lastOrNull() as? StakingViewState.RelayChain.Stake
        suspend fun recoverCurrent() {
            val next = summaryObserved.get() + 1
            lowerAssets.value = current
            awaitSummary(next)
            assertTrue(body()!!.stakeInfoViewState.staked.value!!.contains("2"))
            assertTrue(body()!!.stakeInfoViewState.staked.value!!.contains("BBB"))
        }
        suspend fun close() {
            job.cancelAndJoin(); test.runCurrent()
            assertTrue("No uncaught fixture failure: $errors", errors.isEmpty())
        }
        fun asset(chain: Chain, account: ByteArray = this.account, metaId: Long = 42,
                  type: ChainAsset.StakingType = ChainAsset.StakingType.RELAYCHAIN, bonded: Long = 200): Asset {
            val id = chain.id
            val assetSymbol = if (id == b.id) "BBB" else "AAA"
            val config = mock<ChainAsset> {
                on { chainId } doReturn id
                on { this.id } doReturn "native"
                on { symbol } doReturn assetSymbol
                on { staking } doReturn type
                on { precision } doReturn 2
                on { iconUrl } doReturn ""
            }
            return Asset.createEmpty(config, metaId, account, minSupportedVersion = null).copy(bondedInPlanksState = AssetAmountState.Known(BigInteger.valueOf(bonded)))
        }
        private fun chain(id: String) = mock<Chain> {
            on { this.id } doReturn id
            on { addressPrefix } doReturn 42
            on { ecosystem } doReturn Ecosystem.Substrate
        }
    }
}
