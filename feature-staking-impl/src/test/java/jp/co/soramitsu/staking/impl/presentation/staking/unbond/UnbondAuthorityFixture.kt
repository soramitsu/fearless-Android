package jp.co.soramitsu.staking.impl.presentation.staking.unbond

import android.graphics.drawable.PictureDrawable
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import java.math.BigDecimal
import java.math.BigInteger
import java.util.Optional
import java.util.concurrent.CopyOnWriteArrayList
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.presentation.actions.ExternalAccountActions
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.mixin.api.RetryPayload
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.validation.*
import jp.co.soramitsu.core.extrinsic.MutationExecutionGuard
import jp.co.soramitsu.staking.api.data.StakingSelectionRevision
import jp.co.soramitsu.core.extrinsic.ExtrinsicService
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.staking.api.domain.model.StakingAccount
import jp.co.soramitsu.staking.api.domain.model.StakingState
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.staking.impl.domain.model.NetworkInfo
import jp.co.soramitsu.staking.impl.domain.staking.unbond.UnbondInteractor
import jp.co.soramitsu.staking.impl.domain.validations.unbond.*
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.staking.unbond.select.*
import jp.co.soramitsu.staking.impl.presentation.staking.unbond.confirm.*
import jp.co.soramitsu.staking.impl.scenarios.StakingScenarioInteractor
import jp.co.soramitsu.wallet.api.presentation.mixin.fee.*
import jp.co.soramitsu.wallet.api.presentation.model.FeeModel
import jp.co.soramitsu.wallet.impl.domain.interfaces.QuickInputsUseCase
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.AssetAmountState
import jp.co.soramitsu.wallet.impl.domain.model.Token
import jp.co.soramitsu.wallet.impl.domain.model.FiatValuationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.TestDispatcher
import org.mockito.kotlin.*
import jp.co.soramitsu.core.models.Asset as ChainAsset

/** Actual ViewModel + ValidationExecutor + UnbondInteractor; mocked network service only executes captured builders. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class UnbondAuthorityFixture(
    val dispatcher: TestDispatcher,
    val confirmScreen: Boolean = false,
    val parachain: Boolean = false,
    bonded: AssetAmountState<BigInteger> = AssetAmountState.Known(BigInteger.valueOf(1_000)),
    val holdValidation: Boolean = false,
    val holdBuilder: Boolean = false,
    trustedFiat: Boolean = false
) {
    val account = ByteArray(32) { 1 }
    val collator = "0x" + "02".repeat(32)
    val config = configuration()
    val chain = mock<Chain> {
        on { id } doReturn "fixture"
        on { ecosystem } doReturn Ecosystem.Substrate
        on { externalApi } doReturn null
        on { addressPrefix } doReturn 42
    }
    val initial = Asset.createEmpty(config, 7, account, minSupportedVersion = null).copy(
        token = if (trustedFiat) Token(config, AssetAmountState.Known(BigDecimal.TEN), AssetAmountState.Missing,
            FiatValuationContext("usd"), "usd", "fixture-price") else Token(config, null, null, null),
        freeInPlanksState = AssetAmountState.Known(BigInteger.valueOf(2_000)), bondedInPlanksState = bonded)
    val assets = MutableStateFlow(initial)
    val initialState: StakingState = if (parachain) StakingState.Parachain.None(chain, account)
        else StakingState.Stash.None(chain, account, account, account)
    val states = MutableStateFlow(initialState)
    val resources = mock<ResourceManager> {
        on { getString(any()) } doReturn "Unavailable"
        on { getString(any(), anyVararg()) } doReturn "Amount"
        on { getQuantityString(any(), any(), anyVararg()) } doReturn "24 hours"
    }
    val validated = CopyOnWriteArrayList<UnbondValidationPayload>()
    val validationEntered = CompletableDeferred<Unit>()
    val validationRelease = CompletableDeferred<Unit>().apply { if (!holdValidation) complete(Unit) }
    val builderEntered = CompletableDeferred<Unit>()
    val builderRelease = CompletableDeferred<Unit>().apply { if (!holdBuilder) complete(Unit) }
    val calls = CopyOnWriteArrayList<Call>()
    val submitted = CopyOnWriteArrayList<String>()
    val estimates = CopyOnWriteArrayList<BigInteger>()
    val routed = CopyOnWriteArrayList<ConfirmUnbondPayload>()
    data class Call(val amount: BigInteger, val state: StakingState, val bonded: BigInteger?, val candidate: String?)
    val validator = object : Validation<UnbondValidationPayload, UnbondValidationFailure> {
        override suspend fun validate(value: UnbondValidationPayload): ValidationStatus<UnbondValidationFailure> {
            validated += value; validationEntered.complete(Unit); validationRelease.await()
            return ValidationStatus.Valid()
        }
    }
    val system = ValidationSystem(validator)
    var quoteRead: suspend (Asset) -> BigDecimal? = { if (parachain) BigDecimal.TEN else it.bonded }
    var scenarioBuild: suspend () -> Unit = {}
    var estimateFailure: Throwable? = null
    var submissionFailure: Throwable? = null
    val actionCompletions = CopyOnWriteArrayList<Throwable>()
    private val gates = mutableListOf<CompletableDeferred<Unit>>()
    fun gate() = CompletableDeferred<Unit>().also { gates += it }
    val scenario = mock<StakingScenarioInteractor> {
        on { stakingStateFlow() } doReturn states
        on { getUnbondValidationSystem() } doReturn system
        onBlocking { overrideUnbondHint() } doReturn null
        on { overrideUnbondAvailableLabel() } doReturn null
        onBlocking { observeNetworkInfoState() } doReturn flowOf(NetworkInfo.Parachain(24, BigInteger.ZERO))
        on { getSelectedAccountAddress() } doReturn flowOf(Optional.empty())
        on { getCollatorAddress(anyOrNull()) } doReturn flowOf(Optional.empty())
        onBlocking { getUnstakeAvailableAmount(any(), anyOrNull()) } doSuspendableAnswer {
            quoteRead(it.getArgument(0))
        }
        onBlocking { stakeLess(any(), any(), any(), anyOrNull(), anyOrNull(), any()) } doSuspendableAnswer {
            scenarioBuild()
            calls += Call(it.getArgument(1), it.getArgument(2), it.getArgument(3), it.getArgument(4))
            Unit
        }
    }
    val selection = StakingSelectionRevision()
    val wallet = mock<MetaAccount> {
        on { id } doReturn 7L
        on { substrateAccountId } doReturn account
        on { chainAccounts } doReturn emptyMap()
    }
    val interactor = mock<StakingInteractor> {
        on { selectionRevision } doReturn selection
        onBlocking { getCurrentMetaAccount() } doReturn wallet
        on { currentAssetFlow() } doReturn assets
        onBlocking { getProjectedAccount(any()) } doAnswer { StakingAccount(it.getArgument(0), "Fixture", false) }
    }
    val builder = mock<ExtrinsicBuilder>()
    val service = mock<ExtrinsicService> {
        onBlocking { estimateFeeForWallet(any(), any<ByteArray>(), eq(7L), any(), anyOrNull(), anyOrNull(), any()) } doSuspendableAnswer {
            estimateFailure?.let { failure -> throw failure }
            it.getArgument<suspend ExtrinsicBuilder.() -> Unit>(6).invoke(builder)
            estimates += BigInteger.ONE
            BigInteger.ONE
        }
        onBlocking { submitAuthorizedExtrinsicForWallet(any(), any(), eq(7L), any(), any(), anyOrNull(), anyOrNull(), any()) } doSuspendableAnswer {
            currentCoroutineContext().job.invokeOnCompletion { cause -> cause?.let { actionCompletions += it } }
            yield() // Preserve Kotlin's suspend Result ABI at the mocked external service boundary.
            builderEntered.complete(Unit); builderRelease.await()
            runCatching {
                submissionFailure?.let { failure -> throw failure }
                it.getArgument<suspend ExtrinsicBuilder.() -> Unit>(7).invoke(builder)
                val intent = "ab".repeat(32)
                it.getArgument<(String) -> MutationExecutionGuard>(3)(intent)
                    .runSubmissionIfAuthorized(intent) { submitted += "fixture-hash" }
                "fixture-hash"
            }
        }
    }
    val unbond = UnbondInteractor(service)
    val router = mock<StakingRouter> {
        on { openConfirmUnbond(any()) } doAnswer { routed += it.getArgument<ConfirmUnbondPayload>(0); Unit }
    }
    val icon = mock<PictureDrawable>()
    val icons = mock<AddressIconGenerator> {
        onBlocking { createAddressIcon(any(), any(), any()) } doReturn icon
    }
    val external = mock<ExternalAccountActions.Presentation> {
        on { openBrowserEvent } doReturn MutableLiveData()
        on { showExternalActionsEvent } doReturn MutableLiveData()
    }
    val quick = mock<QuickInputsUseCase> {
        onBlocking { calculateStakingQuickInputs(any(), any(), any(), any()) } doReturn emptyMap()
    }
    val feeRequests = CopyOnWriteArrayList<suspend (Token) -> BigInteger>()
    private val feeInitializers = CopyOnWriteArrayList<suspend () -> Unit>()
    val fees = object : FeeLoaderMixin.Presentation {
        override val feeLiveData = MutableLiveData<FeeStatus>(FeeStatus.Loaded(FeeModel(BigDecimal.ONE, initial.token)))
        override val retryEvent = MutableLiveData<Event<RetryPayload>>()
        override fun invalidate() { feeLiveData.value = FeeStatus.Loading }
        override fun loadFee(coroutineScope: CoroutineScope, feeConstructor: suspend (Token) -> BigInteger,
            onRetryCancelled: () -> Unit, onComplete: ((FeeStatus?) -> Unit)?) {
            feeRequests += feeConstructor
            feeInitializers += {
                val status = runCatching { feeConstructor(initial.token) }.fold(
                    { FeeStatus.Loaded(FeeModel(BigDecimal.ONE, initial.token)) }, { FeeStatus.Error })
                feeLiveData.value = status
                onComplete?.invoke(status)
            }
        }
        override fun requireFee(block: (BigDecimal) -> Unit, onError: (String, String) -> Unit) { block(BigDecimal.ONE) }
    }
    private val confirmationOwner = SupervisorJob()
    private val confirmationAuthority = UnbondRequestAuthority(confirmationOwner, selection).apply {
        assetChanged(initial); stateChanged(initialState)
    }
    private val confirmation = confirmationAuthority.capture(if (parachain) collator else null)?.takeIf { it.admitWallet(wallet) }?.confirmation(
        UnbondValidationPayload(initialState, BigDecimal.ONE, BigDecimal.ONE, initial, if (parachain) collator else null))
    init {
        if (confirmScreen) {
            val sourceScope = CoroutineScope(confirmationOwner + Dispatchers.Main)
            assets.onEach { confirmationAuthority.assetChanged(it) }.launchIn(sourceScope)
            states.onEach { confirmationAuthority.stateChanged(it) }.launchIn(sourceScope)
        }
    }
    private val extraVms = mutableListOf<ConfirmUnbondViewModel>()
    fun confirm(payload: ConfirmUnbondPayload) = ConfirmUnbondViewModel(router, interactor, scenario, unbond,
        resources, ValidationExecutor(resources), icons, mock(), external,
        SavedStateHandle(mapOf(PAYLOAD_KEY to payload))).also { extraVms += it }
    val vm: BaseViewModel = if (confirmScreen) ConfirmUnbondViewModel(router, interactor, scenario, unbond,
        resources, ValidationExecutor(resources), icons, mock(), external,
        SavedStateHandle(mapOf(PAYLOAD_KEY to ConfirmUnbondPayload(BigDecimal.ONE, BigDecimal.ONE, if (parachain) collator else null, confirmation))))
    else SelectUnbondViewModel(router, interactor, scenario, unbond, resources, ValidationExecutor(resources), fees,
        quick, SavedStateHandle(mapOf(SelectUnbondFragment.PAYLOAD_KEY to SelectUnbondPayload(if (parachain) collator else null, true))))
    private var initialJobs: Set<Job> = emptySet()

    suspend fun start() {
        dispatcher.scheduler.runCurrent()
        // Force the initial public projection to complete before assertions or fixture teardown.
        await { if (vm is ConfirmUnbondViewModel) confirmation == null || vm.originAddressModelLiveData.value != null else (vm as SelectUnbondViewModel).assetModelFlow.value != null }
        if (vm is SelectUnbondViewModel) {
            dispatcher.scheduler.advanceTimeBy(501); dispatcher.scheduler.runCurrent()
            // Establish an actual current constructor/completion pair before caller tests; retain manual feeRequests.
            completeFees()
            calls.clear(); estimates.clear()
        }
        initialJobs = vm.viewModelScope.coroutineContext.job.children.toSet()
    }
    suspend fun completeFees() {
        val pending = feeInitializers.toList(); feeInitializers.clear()
        pending.forEach { it() }
    }
    fun click(submit: Boolean = true) {
        when (vm) {
            is ConfirmUnbondViewModel -> vm.confirmClicked()
            is SelectUnbondViewModel -> if (submit) vm.confirmClicked() else vm.nextClicked()
        }
        dispatcher.scheduler.runCurrent()
    }
    suspend fun finishActions() { await { vm.viewModelScope.coroutineContext.job.children.none { it !in initialJobs && it.isActive } } }
    suspend fun await(condition: () -> Boolean) = withTimeout(5_000) {
        while (true) { dispatcher.scheduler.runCurrent(); if (condition()) break; delay(5) }
    }
    fun releaseAndCancel() {
        validationRelease.complete(Unit); builderRelease.complete(Unit)
        gates.forEach { it.complete(Unit) }
        extraVms.forEach { it.viewModelScope.cancel() }
        vm.viewModelScope.cancel()
        confirmationOwner.cancel()
    }
    suspend fun close() {
        releaseAndCancel()
        await { vm.viewModelScope.coroutineContext.job.isCompleted && confirmationOwner.isCompleted && extraVms.all { it.viewModelScope.coroutineContext.job.isCompleted } }
    }
    fun configuration(chainId: String = "fixture", id: String = "native", precision: Int = 2): ChainAsset = mock {
        on { this.chainId } doReturn chainId
        on { this.id } doReturn id
        on { this.precision } doReturn precision
        on { symbol } doReturn "UNIT"
        on { chainName } doReturn "Fixture"
        on { iconUrl } doReturn "public://fixture"
        on { priceId } doReturn "fixture-price"
        on { staking } doReturn if (parachain) ChainAsset.StakingType.PARACHAIN else ChainAsset.StakingType.RELAYCHAIN
    }
}
