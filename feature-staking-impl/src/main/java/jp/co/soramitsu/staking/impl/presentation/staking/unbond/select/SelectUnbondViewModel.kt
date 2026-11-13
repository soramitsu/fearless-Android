package jp.co.soramitsu.staking.impl.presentation.staking.unbond.select

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.mixin.api.Validatable
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.formatCryptoDetail
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.common.utils.requireException
import jp.co.soramitsu.common.validation.ValidationExecutor
import jp.co.soramitsu.common.validation.progressConsumer
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.staking.impl.domain.staking.unbond.UnbondInteractor
import jp.co.soramitsu.staking.impl.domain.validations.unbond.UnbondValidationPayload
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.staking.unbond.confirm.ConfirmUnbondPayload
import jp.co.soramitsu.staking.impl.presentation.staking.unbond.unbondPayloadAutoFix
import jp.co.soramitsu.staking.impl.presentation.staking.unbond.unbondValidationFailure
import jp.co.soramitsu.staking.impl.scenarios.StakingScenarioInteractor
import jp.co.soramitsu.staking.impl.scenarios.relaychain.HOURS_IN_DAY
import jp.co.soramitsu.wallet.api.data.mappers.mapAssetToAssetModel
import jp.co.soramitsu.wallet.api.presentation.mixin.fee.FeeLoaderMixin
import jp.co.soramitsu.wallet.impl.domain.interfaces.QuickInputsUseCase
import jp.co.soramitsu.wallet.impl.domain.model.planksFromAmount
import jp.co.soramitsu.common.data.storage.WalletSelectionEpoch
import jp.co.soramitsu.staking.impl.presentation.common.stakingBalanceUnavailable
import jp.co.soramitsu.staking.impl.presentation.staking.unbond.UnbondRequestAuthority
import jp.co.soramitsu.wallet.api.presentation.mixin.fee.FeeStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.SharingStarted
import jp.co.soramitsu.wallet.api.presentation.model.AssetModel
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.BigInteger
import javax.inject.Inject
import javax.inject.Named
import kotlin.time.DurationUnit
import kotlin.time.toDuration

private const val DEBOUNCE_DURATION_MILLIS = 500

@HiltViewModel
class SelectUnbondViewModel @Inject constructor(
    private val router: StakingRouter,
    private val interactor: StakingInteractor,
    private val stakingScenarioInteractor: StakingScenarioInteractor,
    private val unbondInteractor: UnbondInteractor,
    private val resourceManager: ResourceManager,
    private val validationExecutor: ValidationExecutor,
    @Named("StakingFeeLoader") private val feeLoaderMixin: FeeLoaderMixin.Presentation,
    private val quickInputsUseCase: QuickInputsUseCase,
    private val savedStateHandle: SavedStateHandle
) : BaseViewModel(),
    Validatable by validationExecutor,
    FeeLoaderMixin by feeLoaderMixin {

    private val payload = savedStateHandle.get<SelectUnbondPayload>(SelectUnbondFragment.PAYLOAD_KEY)!!

    val oneScreenConfirmation = payload.oneScreenConfirmation

    val unbondHint: LiveData<String> = MutableLiveData<String>().apply {
        launch {
            stakingScenarioInteractor.overrideUnbondHint()?.let { postValue(it) }
        }
    }

    val isInputFocused = MutableStateFlow(false)

    private val _showNextProgress = MutableLiveData(false)
    val showNextProgress: LiveData<Boolean> = _showNextProgress

    private val authority = UnbondRequestAuthority(viewModelScope.coroutineContext.job, interactor.selectionRevision)
    private val presentationScope = CoroutineScope(viewModelScope.coroutineContext + Dispatchers.Main)
    private var latestAsset: Asset? = null
    private var completedFee: Pair<UnbondRequestAuthority.Context, BigDecimal>? = null

    private val accountStakingFlow = stakingScenarioInteractor.stakingStateFlow()
        .onEach { authority.stateChanged(it) }
        .onCompletion { authority.stateChanged(null) }
        .catch { if (it is CancellationException) throw it else showError(it) }
        .shareIn(presentationScope, SharingStarted.Eagerly, replay = 1)

    private val assetFlow = interactor.currentAssetFlow()
        .onEach { latestAsset = it; authority.assetChanged(it) }
        .onCompletion { authority.assetChanged(null) }
        .catch { if (it is CancellationException) throw it else showError(it) }
        .shareIn(presentationScope, SharingStarted.Eagerly, replay = 1)

    private val mutableAssetModel = MutableLiveData<AssetModel>()
    val assetModelFlow: LiveData<AssetModel> = mutableAssetModel
    private val quotedAsset = MutableStateFlow<Asset?>(null)
    private val reviewSignals = merge(authority.changes.map { Unit }, WalletSelectionEpoch.changes.map { Unit },
        interactor.selectionRevision.changes.map { Unit }).shareIn(presentationScope, SharingStarted.Eagerly, replay = 1)

    val enteredAmountFlow = authority.amountInput

    val enteredFiatAmountFlow = quotedAsset.combine(enteredAmountFlow) { asset, input ->
        asset?.let { current ->
            UnbondRequestAuthority.parseAmount(input, current.token.configuration.precision)
                ?.let { current.token.fiatAmount(it)?.formatFiat(current.token.fiatSymbol) }
        }
    }
        .inBackground()
        .asLiveData()

    val lockupPeriodLiveData = MutableLiveData<String>().apply {
        launch {
            // Read on its own: it never waits on, or fails with, the network's minimum stake. A period that cannot be
            // read is left out rather than closing the app.
            val read = runCatching { stakingScenarioInteractor.unbondingPeriodInHours() }
            currentCoroutineContext().ensureActive()
            val hours = read.getOrNull() ?: return@launch
            val lockupPeriod = if (hours > HOURS_IN_DAY) {
                val inDays = hours / HOURS_IN_DAY
                resourceManager.getQuantityString(R.plurals.common_days_format, inDays, inDays)
            } else {
                resourceManager.getQuantityString(R.plurals.common_hours_format, hours, hours)
            }
            postValue(lockupPeriod)
        }
    }

    val accountLiveData = stakingScenarioInteractor.getSelectedAccountAddress()
        .inBackground()
        .asLiveData()

    val collatorLiveData = stakingScenarioInteractor.getCollatorAddress(payload.collatorAddress)
        .inBackground()
        .asLiveData()

    private val quickInputsStateFlow = MutableStateFlow<Map<Double, BigDecimal>>(emptyMap())
    private var quickInputContext: UnbondRequestAuthority.Context? = null

    init {
        listenFee()
        listenQuote()
    }

    private fun listenQuote() {
        // Clear before collectLatest waits for cancelled, possibly non-cancellable, quote cleanup.
        reviewSignals.onEach {
            quotedAsset.value = null
            quickInputsStateFlow.value = emptyMap()
            quickInputContext = null
            latestAsset?.let { mutableAssetModel.value = mapAssetToAssetModel(it, resourceManager, { null }, null) }
        }.onEach { _showNextProgress.value = false }.let { signals ->
            presentationScope.launch {
                signals.collectLatest {
                    val context = authority.capture(payload.collatorAddress) ?: return@collectLatest
                    try {
                        if (!context.admitWallet(interactor.getCurrentMetaAccount())) return@collectLatest
                        val available = stakingScenarioInteractor.getUnstakeAvailableAmount(context.asset, context.collator?.fromHex())
                        context.requireCurrent()
                        requireNotNull(available) { "Unbond quote unavailable" }
                        require(available.signum() >= 0) { "Unbond quote is negative" }
                        val pattern = stakingScenarioInteractor.overrideUnbondAvailableLabel() ?: R.string.common_available_format
                        mutableAssetModel.value = mapAssetToAssetModel(context.asset, resourceManager, { available }, pattern)
                        quotedAsset.value = context.asset
                        val quick = quickInputsUseCase.calculateStakingQuickInputs(context.asset.chainId,
                            context.asset.token.configuration.id, calculateAvailableAmount = {
                                context.requireCurrent(); available
                            }, calculateFee = { context.requireCurrent(); BigInteger.ZERO })
                        context.requireCurrent()
                        quickInputContext = context
                        quickInputsStateFlow.value = quick
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Throwable) { if (context.isCurrent()) showError(error) }
                }
            }
        }
    }

    fun nextClicked() = maybeGoToNext(submit = false)

    fun confirmClicked() = maybeGoToNext(submit = true)

    fun backClicked() {
        authority.revoke()
        feeLoaderMixin.invalidate()
        router.back()
    }

    @OptIn(FlowPreview::class)
    private fun listenFee() {
        // Regular Main avoids taking our request lock inline under wallet/selection publisher monitors.
        viewModelScope.launch(Dispatchers.Main) {
            merge(WalletSelectionEpoch.changes.map { Unit }, interactor.selectionRevision.changes.map { Unit })
                .collect { completedFee = null; feeLoaderMixin.invalidate(); _showNextProgress.value = false }
        }
        authority.changes
            .onEach { completedFee = null; feeLoaderMixin.invalidate(); _showNextProgress.value = false }
            .debounce(DEBOUNCE_DURATION_MILLIS.toDuration(DurationUnit.MILLISECONDS))
            .onEach { authority.capture(payload.collatorAddress)?.let(::loadFee) }
            .launchIn(CoroutineScope(viewModelScope.coroutineContext + Dispatchers.Main))
    }

    private fun loadFee(context: UnbondRequestAuthority.Context) {
        feeLoaderMixin.loadFee(
            coroutineScope = viewModelScope,
            feeConstructor = { token ->
                check(context.admitWallet(interactor.getCurrentMetaAccount())) { "Unbond wallet changed" }
                context.requireCurrent()
                require(token.configuration == context.asset.token.configuration) { "Unbond fee token changed" }
                val amountInPlanks = context.asset.token.planksFromAmount(context.amount)
                unbondInteractor.estimateFee(context.stash, context.walletId, context::isCurrent) {
                    context.requireCurrent()
                    stakingScenarioInteractor.stakeLess(this, amountInPlanks, context.stash,
                        context.asset.bondedInPlanks, context.collator, context::isCurrent)
                    context.requireCurrent()
                }
            },
            onRetryCancelled = { if (context.isCurrent()) backClicked() },
            onComplete = { status ->
                if (context.isCurrent() && status is FeeStatus.Loaded &&
                    status.feeModel.token.configuration == context.asset.token.configuration) {
                    completedFee = context to status.feeModel.fee
                }
            }
        )
    }

    private fun maybeGoToNext(submit: Boolean) {
        val (context, fee) = completedFee?.takeIf { it.first.isCurrent() } ?: run {
            showError(stakingBalanceUnavailable(resourceManager)); return
        }
        if (fee.signum() < 0) { showError(stakingBalanceUnavailable(resourceManager)); return }
        val attempt = context.claim(viewModelScope.coroutineContext.job) ?: return
        val validationPayload = UnbondValidationPayload(context.stash, fee, context.amount, context.asset, context.collator)
        launch {
            try {
                validationExecutor.requireValid(
                    validationSystem = stakingScenarioInteractor.getUnbondValidationSystem(),
                    payload = validationPayload,
                    errorDisplayer = ::showError,
                    validationFailureTransformer = { unbondValidationFailure(it, resourceManager) },
                    autoFixPayload = ::unbondPayloadAutoFix,
                    progressConsumer = _showNextProgress.progressConsumer(),
                    isCurrent = attempt::isCurrent
                ) { valid ->
                    if (attempt.isCurrent()) {
                        _showNextProgress.value = false
                        if (valid.amount.compareTo(context.amount) != 0) {
                            val changed = attempt.requestAmountReview(valid.amount)
                            completedFee = null
                            feeLoaderMixin.invalidate()
                            attempt.release()
                            if (!changed) showError(stakingBalanceUnavailable(resourceManager))
                        } else if (submit) sendTransaction(attempt, valid) else openConfirm(attempt, valid)
                    }
                }
            } catch (cancelled: CancellationException) {
                attempt.release(); throw cancelled
            } catch (error: Throwable) {
                if (attempt.isCurrent()) showError(error)
                attempt.release()
            }
        }
    }

    private fun openConfirm(attempt: UnbondRequestAuthority.Attempt, valid: UnbondValidationPayload) {
        attempt.requireCurrent()
        val review = attempt.context.confirmation(valid)
        router.openConfirmUnbond(ConfirmUnbondPayload(valid.amount, valid.fee, valid.collatorAddress, review))
        attempt.release()
    }

    private fun sendTransaction(attempt: UnbondRequestAuthority.Attempt, valid: UnbondValidationPayload) = launch {
        try {
            attempt.requireCurrent()
            val amountInPlanks = valid.asset.token.planksFromAmount(valid.amount)
            val execution = currentCoroutineContext().job
            val result = unbondInteractor.unbond(valid.stash, valid.asset.metaId, attempt::isCurrent,
                { intent -> attempt.authorize(intent, execution) }) {
                attempt.requireCurrent()
                stakingScenarioInteractor.stakeLess(this, amountInPlanks, valid.stash,
                    valid.asset.bondedInPlanks, valid.collatorAddress, attempt::isCurrent)
                attempt.requireCurrent()
            }
            (result.exceptionOrNull() as? CancellationException)?.let { throw it }
            if (attempt.isCurrent()) {
                _showNextProgress.value = false
                if (result.isSuccess) {
                    showMessage(resourceManager.getString(R.string.common_transaction_submitted))
                    router.returnToStakingBalance()
                } else showError(result.requireException())
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Throwable) { if (attempt.isCurrent()) showError(error) }
        finally { if (attempt.isCurrent()) _showNextProgress.value = false; attempt.release() }
    }

    override fun onCleared() {
        authority.revoke()
        feeLoaderMixin.invalidate()
        super.onCleared()
    }

    fun onAmountInputFocusChanged(hasFocus: Boolean) {
        launch {
            isInputFocused.emit(hasFocus)
        }
    }

    fun onQuickAmountInput(input: Double) {
        val context = quickInputContext?.takeIf { it.isCurrent() } ?: return
        val amount = quickInputsStateFlow.value[input] ?: return
        if (context.isCurrent()) enteredAmountFlow.value = amount.formatCryptoDetail()
    }
}
