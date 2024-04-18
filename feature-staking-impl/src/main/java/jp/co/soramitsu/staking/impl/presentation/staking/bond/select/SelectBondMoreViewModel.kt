package jp.co.soramitsu.staking.impl.presentation.staking.bond.select

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Named
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.mixin.api.Validatable
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.applyFiatRate
import jp.co.soramitsu.common.utils.flowOf
import jp.co.soramitsu.common.utils.formatCrypto
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.common.utils.requireException
import jp.co.soramitsu.common.validation.ValidationExecutor
import jp.co.soramitsu.common.validation.progressConsumer
import jp.co.soramitsu.core.utils.amountFromPlanks
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.staking.impl.domain.staking.bond.BondMoreInteractor
import jp.co.soramitsu.staking.impl.domain.validations.bond.BondMoreValidationPayload
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.staking.bond.bondMoreValidationFailure
import jp.co.soramitsu.staking.impl.presentation.staking.bond.confirm.ConfirmBondMorePayload
import jp.co.soramitsu.staking.impl.scenarios.StakingScenarioInteractor
import jp.co.soramitsu.wallet.api.data.mappers.mapAssetToAssetModel
import jp.co.soramitsu.wallet.api.presentation.mixin.fee.FeeLoaderMixin
import jp.co.soramitsu.wallet.impl.domain.model.planksFromAmount
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

private const val DEFAULT_AMOUNT = 1
private const val DEBOUNCE_DURATION_MILLIS = 500

@HiltViewModel
class SelectBondMoreViewModel @Inject constructor(
    private val router: StakingRouter,
    interactor: StakingInteractor,
    private val stakingScenarioInteractor: StakingScenarioInteractor,
    private val bondMoreInteractor: BondMoreInteractor,
    private val resourceManager: ResourceManager,
    private val validationExecutor: ValidationExecutor,
    @Named("StakingFeeLoader") private val feeLoaderMixin: FeeLoaderMixin.Presentation,
    private val savedStateHandle: SavedStateHandle
) : BaseViewModel(),
    Validatable by validationExecutor,
    FeeLoaderMixin by feeLoaderMixin {

    private val payload = savedStateHandle.get<SelectBondMorePayload>(PAYLOAD_KEY)!!

    val oneScreenConfirmation = payload.oneScreenConfirmation

    val isInputFocused = MutableStateFlow(false)

    private val _showNextProgress = MutableLiveData(false)
    val showNextProgress: LiveData<Boolean> = _showNextProgress

    private val accountStakingFlow = stakingScenarioInteractor.selectedAccountStakingStateFlow()
        .share()

    private val assetFlow = interactor.currentAssetFlow()
        .share()

    val stakeCreatorBalanceFlow = flowOf {
        val balance = stakingScenarioInteractor.getAvailableForBondMoreBalance()
        val asset = assetFlow.first()
        val balanceAmount = balance.formatCrypto(asset.token.configuration.symbol)
        val balanceFiatAmount = balance.applyFiatRate(asset.token.fiatRate)?.formatFiat(asset.token.fiatSymbol)
        val balanceWithFiat = balanceAmount + balanceFiatAmount?.let { " ($it)" }
        resourceManager.getString(R.string.common_available_format, balanceWithFiat)
    }.inBackground().share()

    val assetModelFlow = assetFlow
        .map { mapAssetToAssetModel(it, resourceManager) }
        .inBackground()
        .asLiveData()

    val enteredAmountFlow = MutableStateFlow(DEFAULT_AMOUNT.toString())

    private val parsedAmountFlow = enteredAmountFlow.mapNotNull { it.toBigDecimalOrNull() }

    val accountLiveData = stakingScenarioInteractor.getSelectedAccountAddress()
        .inBackground()
        .asLiveData()

    val collatorLiveData = stakingScenarioInteractor.getCollatorAddress(payload.collatorAddress)
        .inBackground()
        .asLiveData()

    val enteredFiatAmountFlow = assetFlow.combine(parsedAmountFlow) { asset, amount ->
        asset.token.fiatAmount(amount)?.formatFiat(asset.token.fiatSymbol)
    }
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

                bondMoreInteractor.estimateFee {
                    stakingScenarioInteractor.stakeMore(this, amountInPlanks, payload.collatorAddress)
                }
            },
            onRetryCancelled = ::backClicked
        )
    }

    private fun requireFee(block: (BigDecimal) -> Unit) = feeLoaderMixin.requireFee(
        block,
        onError = { title, message -> showError(title, message) }
    )

    private fun maybeGoToNext(action: (payload: BondMoreValidationPayload) -> Unit) = requireFee { fee ->
        launch {
            val payload = BondMoreValidationPayload(
                stashAddress = stashAddress(),
                fee = fee,
                amount = parsedAmountFlow.first(),
                chainAsset = assetFlow.first().token.configuration
            )

            validationExecutor.requireValid(
                validationSystem = stakingScenarioInteractor.provideBondMoreValidationSystem(),
                payload = payload,
                validationFailureTransformer = { bondMoreValidationFailure(it, resourceManager) },
                progressConsumer = _showNextProgress.progressConsumer()
            ) {
                _showNextProgress.value = false

                action.invoke(payload)
            }
        }
    }

    private fun openConfirm(validationPayload: BondMoreValidationPayload) {
        val confirmPayload = ConfirmBondMorePayload(
            amount = validationPayload.amount,
            fee = validationPayload.fee,
            stashAddress = validationPayload.stashAddress,
            overrideFinishAction = payload.overrideFinishAction,
            collatorAddress = payload.collatorAddress
        )

        router.openConfirmBondMore(confirmPayload)
    }

    private fun sendTransaction(payload: BondMoreValidationPayload) = launch {
        val token = assetFlow.first().token
        val amountInPlanks = token.planksFromAmount(payload.amount)

        val result = bondMoreInteractor.bondMore(payload.stashAddress) {
            stakingScenarioInteractor.stakeMore(this, amountInPlanks, this@SelectBondMoreViewModel.payload.collatorAddress)
        }

        _showNextProgress.value = false

        if (result.isSuccess) {
            showMessage(resourceManager.getString(R.string.common_transaction_submitted))

            finishFlow()
        } else {
            showError(result.requireException())
        }
    }

    private suspend fun stashAddress() = accountStakingFlow.first().rewardsAddress

    private fun finishFlow() = when {
        payload.overrideFinishAction != null -> payload.overrideFinishAction.invoke(router)
        else -> router.returnToStakingBalance()
    }

    fun onAmountInputFocusChanged(hasFocus: Boolean) {
        launch {
            isInputFocused.emit(hasFocus)
        }
    }

    fun onQuickAmountInput(input: Double) {
        launch {
            val availableAmount = stakingScenarioInteractor.getAvailableForBondMoreBalance()
            val asset = assetFlow.firstOrNull() ?: return@launch

            val amountInPlanks = asset.token.planksFromAmount(availableAmount)
            val fee = bondMoreInteractor.estimateFee {
                stakingScenarioInteractor.stakeMore(this, amountInPlanks, payload.collatorAddress)
            }
            val utilityFeeReserve = asset.token.configuration.amountFromPlanks(fee)

            val value = when {
                availableAmount < utilityFeeReserve -> {
                    BigDecimal.ZERO
                }

                availableAmount * input.toBigDecimal() < availableAmount - utilityFeeReserve -> {
                    availableAmount * input.toBigDecimal()
                }

                else -> {
                    availableAmount - utilityFeeReserve
                }
            }

            enteredAmountFlow.emit(value.formatCrypto())
        }
    }
}
