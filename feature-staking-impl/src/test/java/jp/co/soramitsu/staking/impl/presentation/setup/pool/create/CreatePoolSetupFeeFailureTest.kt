package jp.co.soramitsu.staking.impl.presentation.setup.pool.create

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.common.navigation.payload.WalletSelectorPayload
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAddress
import kotlinx.coroutines.CompletableDeferred
import java.io.IOException
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.CallMovedToAssetHubException
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolCreateFlowState
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolSharedStateProvider
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolState
import jp.co.soramitsu.staking.impl.scenarios.StakingPoolInteractor
import jp.co.soramitsu.wallet.impl.domain.interfaces.QuickInputsUseCase
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Answers
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.anyVararg
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.mockingDetails
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Creating a pool on Polkadot or Kusama estimates NominationPools.create, which fearless-utils refuses since pools
 * moved to Asset Hub. The estimate runs eagerly in viewModelScope; before this fix the refusal was uncaught there and
 * crashed the app as soon as the screen opened.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CreatePoolSetupFeeFailureTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val resourceManager = mock<ResourceManager> {
        on { getString(any()) } doAnswer { "text-${it.getArgument<Int>(0)}" }
        on { getString(any(), anyVararg()) } doAnswer { "text-${it.getArgument<Int>(0)}" }
    }
    private val asset = mock<Asset>(defaultAnswer = Answers.RETURNS_DEEP_STUBS).also { asset ->
        whenever(asset.transferable).thenReturn(BigDecimal.TEN)
        whenever(asset.token.configuration.symbol).thenReturn("DOT")
        whenever(asset.token.configuration.precision).thenReturn(10)
        whenever(asset.token.configuration.chainId).thenReturn(POLKADOT_ID)
        whenever(asset.token.fiatRate).thenReturn(null)
    }
    private var feeFailure: Throwable = CallMovedToAssetHubException("NominationPools", "create")
    private val poolInteractor = mock<StakingPoolInteractor> {
        onBlocking { getLastPoolId(any()) } doReturn BigInteger.TEN
        onBlocking { getAccountName(any()) } doReturn null
        onBlocking { estimateCreateFee(any(), any(), any(), any(), any(), any(), any(), any()) } doAnswer { throw feeFailure }
        onBlocking { getMinToCreate(any()) } doReturn BigInteger.ONE
    }
    private val stakingInteractor = mock<StakingInteractor> {
        onBlocking { existentialDeposit(any()) } doReturn BigInteger.ZERO
    }
    private val selections = MutableStateFlow<WalletSelectorPayload?>(null)
    private val router = mock<StakingRouter> {
        on { walletSelectorPayloadFlow } doReturn selections
    }
    private val quickInputsUseCase = mock<QuickInputsUseCase> {
        onBlocking { calculateStakingQuickInputs(any(), any(), any(), any()) } doReturn emptyMap()
    }
    private val sharedState = StakingPoolSharedStateProvider().apply {
        val chain = mock<Chain> {
            on { id } doReturn POLKADOT_ID
            on { ecosystem } doReturn Ecosystem.Substrate
        }
        mainState.set(StakingPoolState(chain = chain, asset = asset, address = ADDRESS, amount = BigDecimal.ONE))
        createFlowState.set(StakingPoolCreateFlowState(context = mainState.capture()))
    }

    private val owned = mutableListOf<CreatePoolSetupViewModel>()
    private val uncaught = mutableListOf<Throwable>()
    private val previousHandler = Thread.getDefaultUncaughtExceptionHandler()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        // viewModelScope reports uncaught failures here; on a device the same failure kills the app.
        Thread.setDefaultUncaughtExceptionHandler { _, error -> synchronized(uncaught) { uncaught += error } }
    }

    @After
    fun tearDown() {
        runBlocking { withTimeout(5_000) { owned.forEach { it.viewModelScope.coroutineContext[Job]?.cancelAndJoin() } } }
        Thread.setDefaultUncaughtExceptionHandler(previousHandler)
        Dispatchers.resetMain()
        assertEquals(emptyList<Throwable>(), synchronized(uncaught) { uncaught.toList() })
    }

    @Test
    fun `a pool creation refused for Asset Hub is explained on create instead of crashing`() {
        val viewModel = createViewModel()
        awaitEstimates(1)

        // Without a fee the button stays enabled, so that tapping it says why.
        assertTrue(viewModel.viewState.value.createButtonViewState.enabled)

        viewModel.onCreateClick()
        viewModel.onCreateClick()

        assertEquals(emptyList<Throwable>(), synchronized(uncaught) { uncaught.toList() })
        assertEquals(movedTitleAndMessage, viewModel.errorWithTitleLiveData.value?.peekContent())
        // It would be refused again, so asking does not estimate it again.
        assertEquals(estimates(), estimatesAfterOpening)
        verify(router, never()).openCreatePoolConfirm()
    }

    @Test
    fun `another failed fee is reported on create and estimated again`() {
        feeFailure = IOException("offline")
        val viewModel = createViewModel()
        awaitEstimates(1)

        viewModel.onCreateClick()

        assertEquals(emptyList<Throwable>(), synchronized(uncaught) { uncaught.toList() })
        assertEquals(
            "text-${R.string.choose_amount_network_error}" to "text-${R.string.choose_amount_error_fee}",
            viewModel.errorWithTitleLiveData.value?.peekContent()
        )
        awaitEstimates(estimatesAfterOpening + 1)
        verify(router, never()).openCreatePoolConfirm()
    }

    @Test fun `consumed create draft cannot be revived by retained setup`() {
        healthyFees()
        val vm = createViewModel()
        awaitEstimates(1)
        sharedState.createFlowState.complete()
        vm.onCreateClick()
        assertEquals(emptyList<Throwable>(), synchronized(uncaught) { uncaught.toList() })
        assertEquals(null, sharedState.createFlowState.get())
        verify(router, never()).openCreatePoolConfirm()
    }

    @Test fun `replacement create draft cannot be edited by retained setup`() {
        healthyFees()
        val vm = createViewModel()
        awaitEstimates(1)
        val replacement = StakingPoolCreateFlowState(poolName = "Other draft", context = sharedState.mainState.capture())
        sharedState.createFlowState.set(replacement)
        vm.onCreateClick()
        assertEquals(replacement, sharedState.createFlowState.get())
        verify(router, never()).openCreatePoolConfirm()
    }

    @Test fun `pending confirmation prevents retained setup from changing draft`() {
        healthyFees()
        val vm = createViewModel()
        awaitEstimates(1)
        val draft = sharedState.createFlowState.capture()
        val claim = requireNotNull(sharedState.createFlowState.tryClaim(draft))
        vm.onCreateClick()
        assertTrue(sharedState.createFlowState.owns(claim))
        assertEquals(draft.value, sharedState.createFlowState.get())
        verify(router, never()).openCreatePoolConfirm()
    }

    @Test fun `healthy current create setup publishes review`() {
        healthyFees()
        val vm = createViewModel()
        awaitEstimates(1)
        vm.onPoolNameInput("Healthy pool")
        vm.onCreateClick()
        assertEquals("Healthy pool", sharedState.requireCreateState.poolName)
        verify(router).openCreatePoolConfirm()
    }

    @Test fun `main context replacement prevents retained setup publication`() {
        healthyFees()
        val vm = createViewModel()
        awaitEstimates(1)
        val original = sharedState.requireCreateState
        sharedState.mainState.set(sharedState.requireMainState.copy())
        vm.onCreateClick()
        assertEquals(original, sharedState.requireCreateState)
        verify(router, never()).openCreatePoolConfirm()
    }

    @Test fun `wallet selection change prevents retained setup publication`() {
        healthyFees()
        val vm = createViewModel()
        awaitEstimates(1)
        val original = sharedState.requireCreateState
        jp.co.soramitsu.common.data.storage.WalletSelectionEpoch.beginChange()
        jp.co.soramitsu.common.data.storage.WalletSelectionEpoch.endChange()
        vm.onCreateClick()
        assertEquals(original, sharedState.requireCreateState)
        verify(router, never()).openCreatePoolConfirm()
    }

    @Test fun `main change while setup validation is held prevents publication`() {
        healthyFees()
        val entered = kotlinx.coroutines.CompletableDeferred<Unit>()
        val resume = kotlinx.coroutines.CompletableDeferred<BigInteger>()
        kotlinx.coroutines.runBlocking {
            whenever(stakingInteractor.existentialDeposit(any())).doSuspendableAnswer {
                entered.complete(Unit)
                resume.await()
            }
        }
        val vm = createViewModel()
        awaitEstimates(1)
        val draft = sharedState.requireCreateState
        vm.onCreateClick()
        assertTrue(entered.isCompleted)
        sharedState.mainState.set(sharedState.requireMainState.copy())
        resume.complete(BigInteger.ZERO)
        assertEquals(draft, sharedState.requireCreateState)
        verify(router, never()).openCreatePoolConfirm()
    }

    @Test fun `retained setup after Back cannot publish another confirmation`() {
        healthyFees()
        val vm = createViewModel()
        awaitEstimates(1)
        val original = sharedState.requireCreateState
        vm.onNavigationClick()
        vm.onCreateClick()
        assertEquals(original, sharedState.requireCreateState)
        verify(router, never()).openCreatePoolConfirm()
    }

    @Test fun `Back during setup validation prevents confirmation`() {
        healthyFees()
        val (entered, resume) = holdMinimum()
        val vm = createViewModel()
        awaitEstimates(1)
        val original = sharedState.requireCreateState
        vm.onCreateClick()
        assertTrue(entered.isCompleted)
        vm.onNavigationClick()
        resume.complete(BigInteger.ZERO)
        assertEquals(original, sharedState.requireCreateState)
        verify(router, never()).openCreatePoolConfirm()
    }

    @Test fun `late insufficient balance does not alert over a changed main context`() {
        healthyFees()
        val (entered, resume) = holdMinimum()
        val vm = createViewModel()
        awaitEstimates(1)
        vm.onCreateClick()
        assertTrue(entered.isCompleted)
        sharedState.mainState.set(sharedState.requireMainState.copy())
        resume.complete(BigInteger.TEN.pow(30))
        assertEquals(null, vm.errorWithTitleLiveData.value)
        assertEquals(null, vm.errorLiveData.value)
        verify(router, never()).openCreatePoolConfirm()
    }

    @Test fun `ordinary minimum lookup failure is contained and explained`() {
        healthyFees()
        runBlocking { whenever(stakingInteractor.existentialDeposit(any())).doSuspendableAnswer { throw IOException("local minimum unavailable") } }
        val vm = createViewModel()
        awaitEstimates(1)
        vm.onCreateClick()
        assertEquals(emptyList<Throwable>(), synchronized(uncaught) { uncaught.toList() })
        assertEquals("local minimum unavailable", vm.errorLiveData.value?.peekContent())
        verify(router, never()).openCreatePoolConfirm()
    }

    @Test fun `editing form during validation cannot mix old amount and new name`() {
        healthyFees()
        val (entered, resume) = holdMinimum()
        val vm = createViewModel()
        awaitEstimates(1)
        val original = sharedState.requireCreateState
        vm.onPoolNameInput("Old name")
        vm.onCreateClick()
        assertTrue(entered.isCompleted)
        vm.onPoolNameInput("New name")
        vm.onTokenAmountInput(BigDecimal.TEN)
        resume.complete(BigInteger.ZERO)
        assertEquals(original, sharedState.requireCreateState)
        verify(router, never()).openCreatePoolConfirm()
    }

    @Test fun `dismissed setup cannot finish its held validation`() {
        healthyFees()
        val (entered, resume) = holdMinimum()
        val vm = createViewModel()
        awaitEstimates(1)
        val original = sharedState.requireCreateState
        vm.onCreateClick()
        assertTrue(entered.isCompleted)
        vm.onDismissed()
        resume.complete(BigInteger.ZERO)
        assertEquals(original, sharedState.requireCreateState)
        verify(router, never()).openCreatePoolConfirm()
        verify(router, never()).back()
    }

    @Test fun `clearing setup view model cancels held validation`() {
        healthyFees()
        val (entered, resume) = holdMinimum()
        val vm = createViewModel()
        awaitEstimates(1)
        val original = sharedState.requireCreateState
        vm.onCreateClick()
        assertTrue(entered.isCompleted)
        androidx.lifecycle.ViewModelStore().also { it.put("setup", vm); it.clear() }
        resume.complete(BigInteger.ZERO)
        assertEquals(original, sharedState.requireCreateState)
        verify(router, never()).openCreatePoolConfirm()
    }

    @Test fun `setup Back is idempotent`() {
        healthyFees()
        val vm = createViewModel()
        vm.onNavigationClick()
        vm.onNavigationClick()
        verify(router).back()
    }

    @Test fun `returning form values to their originals cannot revive held validation`() {
        healthyFees()
        val (entered, resume) = holdMinimum()
        val vm = createViewModel()
        awaitEstimates(1)
        val original = sharedState.requireCreateState
        vm.onPoolNameInput("Original")
        vm.onCreateClick()
        assertTrue(entered.isCompleted)
        vm.onPoolNameInput("Other")
        vm.onPoolNameInput("Original")
        resume.complete(BigInteger.ZERO)
        assertEquals(original, sharedState.requireCreateState)
        verify(router, never()).openCreatePoolConfirm()
    }

    @Test fun `late ordinary lookup failure after dismissal remains silent`() {
        healthyFees()
        val (entered, resume) = holdMinimum()
        val vm = createViewModel()
        awaitEstimates(1)
        vm.onCreateClick()
        assertTrue(entered.isCompleted)
        vm.onDismissed()
        resume.completeExceptionally(IOException("local lookup ended late"))
        assertEquals(null, vm.errorLiveData.value)
        assertEquals(null, vm.errorWithTitleLiveData.value)
        verify(router, never()).openCreatePoolConfirm()
    }

    @Test fun `older nominator resolution cannot overwrite the latest selection`() {
        assertLatestSelection("nominator")
    }

    @Test fun `older toggler resolution cannot overwrite the latest selection`() {
        assertLatestSelection("state_toggler")
    }

    private fun assertLatestSelection(tag: String) {
        healthyFees()
        val (first, second) = holdSelections()
        val vm = createViewModel()
        awaitEstimates(1)
        selections.value = WalletSelectorPayload(tag, 1)
        runBlocking { verify(stakingInteractor).getMetaAccount(1) }
        selections.value = WalletSelectorPayload(tag, 2)
        runBlocking { verify(stakingInteractor).getMetaAccount(2) }
        second.complete(publicAccount(2))
        first.complete(publicAccount(1))
        vm.onCreateClick()
        val current = sharedState.requireCreateState
        val selected = if (tag == "nominator") current.nominatorAddress else current.stateTogglerAddress
        assertEquals(ByteArray(32) { 2 }.toAddress(0), selected)
        verify(router).openCreatePoolConfirm()
    }

    @Test fun `ordinary wallet selection failure is contained and explained`() {
        healthyFees()
        val (first, _) = holdSelections()
        val vm = createViewModel()
        awaitEstimates(1)
        selections.value = WalletSelectorPayload("nominator", 1)
        first.completeExceptionally(IOException("local account unavailable"))
        assertEquals(emptyList<Throwable>(), synchronized(uncaught) { uncaught.toList() })
        assertEquals("local account unavailable", vm.errorLiveData.value?.peekContent())
    }

    @Test fun `older wallet selection failure cannot alert over a newer successful choice`() {
        healthyFees()
        val (first, second) = holdSelections()
        val vm = createViewModel()
        awaitEstimates(1)
        selections.value = WalletSelectorPayload("nominator", 1)
        selections.value = WalletSelectorPayload("nominator", 2)
        second.complete(publicAccount(2))
        first.completeExceptionally(IOException("old local lookup failure"))
        assertEquals(emptyList<Throwable>(), synchronized(uncaught) { uncaught.toList() })
        assertEquals(null, vm.errorLiveData.value)
        assertEquals(null, vm.errorWithTitleLiveData.value)
    }

    @Test fun `unresolved selection prevents confirmation with the previous address`() {
        healthyFees()
        val (first, _) = holdSelections()
        val vm = createViewModel()
        awaitEstimates(1)
        vm.onPoolNameInput("Public pool")
        runBlocking { withTimeout(5_000) { vm.viewState.first { it.createButtonViewState.enabled } } }
        val original = sharedState.requireCreateState
        selections.value = WalletSelectorPayload("nominator", 1)
        runBlocking { withTimeout(5_000) { vm.viewState.first { !it.createButtonViewState.enabled } } }
        vm.onCreateClick()
        assertEquals(original, sharedState.requireCreateState)
        verify(router, never()).openCreatePoolConfirm()
        first.complete(publicAccount(1))
        runBlocking { withTimeout(5_000) { vm.viewState.first { it.createButtonViewState.enabled } } }
        vm.onCreateClick()
        assertEquals(ByteArray(32) { 1 }.toAddress(0), sharedState.requireCreateState.nominatorAddress)
        verify(router).openCreatePoolConfirm()
    }

    @Test fun `independent selector resolutions preserve both latest choices`() {
        healthyFees()
        val (first, second) = holdSelections()
        val vm = createViewModel()
        awaitEstimates(1)
        val original = sharedState.requireCreateState
        selections.value = WalletSelectorPayload("nominator", 1)
        selections.value = WalletSelectorPayload("state_toggler", 2)
        second.complete(publicAccount(2))
        vm.onCreateClick()
        assertEquals(original, sharedState.requireCreateState)
        verify(router, never()).openCreatePoolConfirm()
        first.complete(publicAccount(1))
        vm.onCreateClick()
        assertEquals(ByteArray(32) { 1 }.toAddress(0), sharedState.requireCreateState.nominatorAddress)
        assertEquals(ByteArray(32) { 2 }.toAddress(0), sharedState.requireCreateState.stateTogglerAddress)
        verify(router).openCreatePoolConfirm()
    }

    @Test fun `unknown transferable disables and rejects pool creation with healthy network lookups`() {
        healthyFees()
        whenever(asset.transferable).thenReturn(null)
        val vm = createViewModel()
        awaitEstimates(1)
        org.junit.Assert.assertFalse(vm.viewState.value.createButtonViewState.enabled)
        vm.onCreateClick()
        verify(router, never()).openCreatePoolConfirm()
        assertEquals(
            "text-${jp.co.soramitsu.common.R.string.common_error_general_title}" to
                "text-${jp.co.soramitsu.common.R.string.portfolio_balance_unavailable}",
            vm.errorWithTitleLiveData.value?.peekContent()
        )
    }

    private fun holdSelections(): Pair<CompletableDeferred<MetaAccount>, CompletableDeferred<MetaAccount>> {
        val first = CompletableDeferred<MetaAccount>()
        val second = CompletableDeferred<MetaAccount>()
        runBlocking {
            whenever(stakingInteractor.getMetaAccount(any())).doSuspendableAnswer {
                if (it.getArgument<Long>(0) == 1L) first.await() else second.await()
            }
        }
        return first to second
    }

    private fun publicAccount(byte: Int): MetaAccount = mock {
        on { chainAccounts } doReturn emptyMap()
        on { substrateAccountId } doReturn ByteArray(32) { byte.toByte() }
    }

    private fun holdMinimum(): Pair<kotlinx.coroutines.CompletableDeferred<Unit>, kotlinx.coroutines.CompletableDeferred<BigInteger>> {
        val entered = kotlinx.coroutines.CompletableDeferred<Unit>()
        val resume = kotlinx.coroutines.CompletableDeferred<BigInteger>()
        runBlocking {
            whenever(stakingInteractor.existentialDeposit(any())).doSuspendableAnswer {
                entered.complete(Unit)
                resume.await()
            }
        }
        return entered to resume
    }

    private fun healthyFees() = kotlinx.coroutines.runBlocking {
        whenever(poolInteractor.estimateCreateFee(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(BigInteger.ONE)
    }

    private val movedTitleAndMessage =
        "text-${R.string.relay_moved_to_asset_hub_title}" to "text-${R.string.relay_moved_to_asset_hub_message}"

    private var estimatesAfterOpening = 0

    private fun createViewModel() = CreatePoolSetupViewModel(
        resourceManager = resourceManager,
        stakingPoolSharedStateProvider = sharedState,
        poolInteractor = poolInteractor,
        stakingInteractor = stakingInteractor,
        router = router,
        quickInputsUseCase = quickInputsUseCase
    ).also { owned += it }

    private fun estimates() = mockingDetails(poolInteractor).invocations.count { it.method.name == "estimateCreateFee" }

    private fun awaitEstimates(count: Int) {
        val deadline = System.currentTimeMillis() + 5_000
        while (estimates() < count && System.currentTimeMillis() < deadline) Thread.sleep(10)
        assertTrue("estimateCreateFee called ${estimates()} times", estimates() >= count)
        // Let the screen's other lookups, which run on Dispatchers.Default, settle before it is read.
        Thread.sleep(200)
        estimatesAfterOpening = estimates()
    }

    private companion object {
        const val ADDRESS = "15oF4uVJwmo4TdGW7VfQxNLavjCXviqxT9S1MgbjMNHr6Sp5"
        const val POLKADOT_ID = "91b171bb158e2d3848fa23a9f1c25182fb8e20313b2c1eb49219da7a70ce90c3"
    }
}
