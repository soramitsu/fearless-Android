package jp.co.soramitsu.staking.impl.presentation.staking.unbond.select

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.mixin.api.Validatable
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.formatCrypto
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.common.utils.requireException
import jp.co.soramitsu.common.validation.ValidationExecutor
import jp.co.soramitsu.common.validation.progressConsumer
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.shared_utils.extensions.fromHex
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
import jp.co.soramitsu.wallet.impl.domain.model.planksFromAmount
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Named
import kotlin.time.DurationUnit
import kotlin.time.toDuration

private const val DEFAULT_AMOUNT = 1
private const val DEBOUNCE_DURATION_MILLIS = 500

@HiltViewModel
class SelectUnbondViewModel @Inject constructor(
    private val router: StakingRouter,
    interactor: StakingInteractor,
    private val stakingScenarioInteractor: StakingScenarioInteractor,
    private val unbondInteractor: UnbondInteractor,
    private val resourceManager: ResourceManager,
    private val validationExecutor: ValidationExecutor,
    @Named("StakingFeeLoader") private val feeLoaderMixin: FeeLoaderMixin.Presentation,
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

    private val accountStakingFlow = stakingScenarioInteractor.stakingStateFlow
        .share()

    private val assetFlow = interactor.currentAssetFlow()
        .share()

    val assetModelFlow = assetFlow
        .map {
            val patternId = stakingScenarioInteractor.overrideUnbondAvailableLabel() ?: R.string.common_available_format
            val retrieveAmount = stakingScenarioInteractor.getUnstakeAvailableAmount(it, payload.collatorAddress?.fromHex())
            mapAssetToAssetModel(
                asset = it,
                resourceManager = resourceManager,
                retrieveAmount = { retrieveAmount },
                patternId = patternId
            )
        }
        .inBackground()
        .asLiveData()

    val enteredAmountFlow = MutableStateFlow(DEFAULT_AMOUNT.toString())

    private val parsedAmountFlow = enteredAmountFlow.mapNotNull { it.toBigDecimalOrNull() }

    val enteredFiatAmountFlow = assetFlow.combine(parsedAmountFlow) { asset, amount ->
        asset.token.fiatAmount(amount)?.formatFiat(asset.token.fiatSymbol)
    }
        .inBackground()
        .asLiveData()

    val lockupPeriodLiveData = MutableLiveData<String>().apply {
        launch {
            val networkInfo = stakingScenarioInteractor.observeNetworkInfoState().first()
            val lockupPeriod = if (networkInfo.lockupPeriodInHours > HOURS_IN_DAY) {
                val inDays = networkInfo.lockupPeriodInHours / HOURS_IN_DAY
                resourceManager.getQuantityString(R.plurals.staking_main_lockup_period_value, inDays, inDays)
            } else {
                resourceManager.getQuantityString(R.plurals.common_hours_format, networkInfo.lockupPeriodInHours, networkInfo.lockupPeriodInHours)
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

    init {
        listenFee()
    }

    fun nextClicked() {
        maybeGoToNext(::openConfirm)
    }

    fun confirmClicked() {
        maybeGoToNext(::sendTransaction)
    }

    fun backClicked() {
        router.back()
    }

    @OptIn(FlowPreview::class)
    private fun listenFee() {
        parsedAmountFlow
            .debounce(DEBOUNCE_DURATION_MILLIS.toDuration(DurationUnit.MILLISECONDS))
            .onEach { loadFee(it) }
            .launchIn(viewModelScope)
    }

    private fun loadFee(amount: BigDecimal) {
        feeLoaderMixin.loadFee(
            coroutineScope = viewModelScope,
            feeConstructor = { token ->
                val amountInPlanks = token.planksFromAmount(amount)
                val asset = assetFlow.first()

                val stashState = accountStakingFlow.first()
                unbondInteractor.estimateFee(stashState) {
                    stakingScenarioInteractor.stakeLess(this, amountInPlanks, stashState, asset.bondedInPlanks.orZero(), payload.collatorAddress)
                }
            },
            onRetryCancelled = ::backClicked
        )
    }

    private fun requireFee(block: (BigDecimal) -> Unit) = feeLoaderMixin.requireFee(
        block,
        onError = { title, message -> showError(title, message) }
    )

    private fun maybeGoToNext(action: (payload: UnbondValidationPayload) -> Unit) = requireFee { fee ->
        launch {
            val asset = assetFlow.first()

            val payload = UnbondValidationPayload(
                stash = accountStakingFlow.first(),
                asset = asset,
                fee = fee,
                amount = parsedAmountFlow.first(),
                collatorAddress = payload.collatorAddress
            )

            validationExecutor.requireValid(
                validationSystem = stakingScenarioInteractor.getUnbondValidationSystem(),
                payload = payload,
                validationFailureTransformer = { unbondValidationFailure(it, resourceManager) },
                autoFixPayload = ::unbondPayloadAutoFix,
                progressConsumer = _showNextProgress.progressConsumer()
            ) { correctPayload ->
                _showNextProgress.value = false

                action.invoke(correctPayload)
            }
        }
    }

    private fun openConfirm(validationPayload: UnbondValidationPayload) {
        val confirmUnbondPayload = ConfirmUnbondPayload(
            amount = validationPayload.amount,
            fee = validationPayload.fee,
            collatorAddress = validationPayload.collatorAddress
        )

        router.openConfirmUnbond(confirmUnbondPayload)
    }

    private fun sendTransaction(validPayload: UnbondValidationPayload) = launch {
        val amountInPlanks = validPayload.asset.token.configuration.planksFromAmount(validPayload.amount)

        val result = unbondInteractor.unbond(validPayload.stash/*, validPayload.asset.bondedInPlanks.orZero(), amountInPlanks*/) {
            stakingScenarioInteractor.stakeLess(
                this,
                amountInPlanks,
                validPayload.stash,
                validPayload.asset.bondedInPlanks.orZero(),
                payload.collatorAddress
            )
        }

        _showNextProgress.value = false

        if (result.isSuccess) {
            showMessage(resourceManager.getString(R.string.common_transaction_submitted))

            router.returnToStakingBalance()
        } else {
            showError(result.requireException())
        }
    }

    fun onAmountInputFocusChanged(hasFocus: Boolean) {
        launch {
            isInputFocused.emit(hasFocus)
        }
    }

    fun onQuickAmountInput(input: Double) {
        launch {
            val asset = assetFlow.first()
            val retrieveAmount = stakingScenarioInteractor.getUnstakeAvailableAmount(asset, payload.collatorAddress?.fromHex())

            val value = (retrieveAmount * input.toBigDecimal()).formatCrypto()
            enteredAmountFlow.emit(value)
        }
    }
}
