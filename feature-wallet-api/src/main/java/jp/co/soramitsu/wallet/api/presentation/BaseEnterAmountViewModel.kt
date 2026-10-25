package jp.co.soramitsu.wallet.api.presentation

import androidx.annotation.StringRes
import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.AlertViewState
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.base.errors.ValidationException
import jp.co.soramitsu.common.compose.component.AmountInputViewState
import jp.co.soramitsu.common.compose.component.ButtonViewState
import jp.co.soramitsu.common.compose.component.EnterAmountViewState
import jp.co.soramitsu.common.compose.component.FeeInfoViewState
import jp.co.soramitsu.common.compose.component.ToolbarViewState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.applyFiatRate
import jp.co.soramitsu.common.utils.formatCrypto
import jp.co.soramitsu.common.utils.formatCryptoDetail
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.wallet.api.presentation.mixin.fee.FeeEstimation
import jp.co.soramitsu.wallet.impl.domain.interfaces.QuickInputsUseCase
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks
import jp.co.soramitsu.wallet.impl.domain.model.planksFromAmount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

open class BaseEnterAmountViewModel(
    @StringRes private val nextButtonTextRes: Int = R.string.common_continue,
    @StringRes private val toolbarTextRes: Int = R.string.staking_bond_more_v1_9_0,
    @StringRes private val balanceHintRes: Int,
    initialAmount: BigDecimal? = null,
    isInputActive: Boolean = true,
    protected val asset: Asset,
    private val resourceManager: ResourceManager,
    private val quickInputsUseCase: QuickInputsUseCase? = null,
    private val feeEstimator: suspend (BigInteger) -> BigInteger,
    private val onNextStep: suspend (BigInteger) -> Unit,
    private vararg val validations: Validation,
    private val buttonValidation: (BigInteger) -> Boolean = { it != BigInteger.ZERO },
    private val availableAmountForOperation: suspend (Asset) -> BigDecimal? = { it.transferable },
    private val errorAlertPresenter: (AlertViewState) -> Unit,
    private val quickInputsCalculator: suspend () -> Map<Double, BigDecimal> = {
        availableAmountForOperation(asset)?.let { available ->
            quickInputsUseCase?.calculateStakingQuickInputs(
                asset.token.configuration.chainId,
                asset.token.configuration.id,
                calculateAvailableAmount = { available },
                calculateFee = { feeEstimator(it) })
        } ?: emptyMap()
    },
    private val onAmountChanged: () -> Unit = {}
) : BaseViewModel() {

    private val defaultAmountInputState = AmountInputViewState(
        tokenName = "...",
        tokenImage = "",
        totalBalance = resourceManager.getString(balanceHintRes, "..."),
        fiatAmount = "",
        tokenAmount = initialAmount.orZero()
    )

    private val defaultButtonState = ButtonViewState(
        resourceManager.getString(nextButtonTextRes),
        false
    )

    private val toolbarViewState = ToolbarViewState(
        resourceManager.getString(toolbarTextRes),
        R.drawable.ic_arrow_back_24dp
    )

    private val defaultState = EnterAmountViewState(
        toolbarViewState,
        defaultAmountInputState,
        FeeInfoViewState.default,
        defaultButtonState
    )

    private val enteredAmountFlow = MutableStateFlow(initialAmount.orZero())

    private val amountInputViewState: Flow<AmountInputViewState> = enteredAmountFlow.map { amount ->
        val available = availableAmountForOperation(asset)
        val tokenBalance = available?.formatCrypto(asset.token.configuration.symbol)
            ?: resourceManager.getString(R.string.portfolio_balance_unavailable)
        val tokenBalanceFiat = available?.let(asset.token::fiatAmount)?.formatFiat(asset.token.fiatSymbol)
        val balanceWithFiat = if (tokenBalanceFiat == null) {
            tokenBalance
        } else {
            "$tokenBalance ($tokenBalanceFiat)"
        }
        val fiatAmount = amount.applyFiatRate(asset.token.fiatRate)?.formatFiat(asset.token.fiatSymbol)

        AmountInputViewState(
            tokenName = asset.token.configuration.symbol,
            tokenImage = asset.token.configuration.iconUrl,
            totalBalance = resourceManager.getString(balanceHintRes, balanceWithFiat),
            fiatAmount = fiatAmount,
            tokenAmount = amount,
            isActive = isInputActive,
            precision = asset.token.configuration.precision
        )
    }.stateIn(this, SharingStarted.Eagerly, defaultAmountInputState)

    private val feeEstimation = FeeEstimation(resourceManager)

    // Runs eagerly in viewModelScope for every amount: a failed estimation must neither crash the app nor stop later ones.
    private val feeInfoViewStateFlow: Flow<FeeInfoViewState> = combine(enteredAmountFlow, feeEstimation.retryFlow) { amount, _ ->
        val inPlanks = asset.token.planksFromAmount(amount)
        val feeInPlanks = feeEstimation.estimate { feeEstimator(inPlanks) } ?: return@combine FeeInfoViewState.default
        val fee = asset.token.amountFromPlanks(feeInPlanks)
        val feeFormatted = fee.formatCryptoDetail(asset.token.configuration.symbol)
        val feeFiat = fee.applyFiatRate(asset.token.fiatRate)?.formatFiat(asset.token.fiatSymbol)

        FeeInfoViewState(feeAmount = feeFormatted, feeAmountFiat = feeFiat)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, FeeInfoViewState.default)

    private val buttonStateFlow = enteredAmountFlow.map { amount ->
        val amountInPlanks = asset.token.planksFromAmount(amount)
        ButtonViewState(
            resourceManager.getString(nextButtonTextRes),
            availableAmountForOperation(asset) != null && buttonValidation(amountInPlanks)
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, defaultButtonState)

    val state = combine(
        amountInputViewState,
        feeInfoViewStateFlow,
        buttonStateFlow
    ) { amountInputState, feeInfoState, buttonState ->
        EnterAmountViewState(
            toolbarState = toolbarViewState,
            amountInputState,
            feeInfoState,
            buttonState
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, defaultState)

    protected var quickInputs: Map<Double, BigDecimal> = mapOf<Double, BigDecimal>()
    init {
        viewModelScope.launch  {
            quickInputs = if (availableAmountForOperation(asset) == null) emptyMap() else quickInputsCalculator()
        }
    }

    fun onAmountInput(amount: BigDecimal?) {
        val updated = amount.orZero()
        if (enteredAmountFlow.value.compareTo(updated) != 0) onAmountChanged()
        enteredAmountFlow.value = updated
    }

    fun onNextClick() {
        viewModelScope.launch {
            val amount = enteredAmountFlow.value
            val inPlanks = asset.token.planksFromAmount(amount)
            isValid(amount).fold({
                onNextStep(inPlanks)
            }, { throwable ->
                val errorAlertViewState = (throwable as? ValidationException)?.let { (title, message) ->
                    AlertViewState(
                        title = title,
                        message = message,
                        buttonText = resourceManager.getString(R.string.common_got_it),
                        iconRes = R.drawable.ic_status_warning_16
                    )
                } ?: AlertViewState(
                    title = resourceManager.getString(R.string.common_error_general_title),
                    message = throwable.localizedMessage ?: throwable.message ?: resourceManager.getString(R.string.common_undefined_error_message),
                    buttonText = resourceManager.getString(R.string.common_got_it),
                    iconRes = R.drawable.ic_status_warning_16
                )
                errorAlertPresenter(errorAlertViewState)
            })
        }
    }

    private suspend fun isValid(amount: BigDecimal): Result<Any> {
        if (availableAmountForOperation(asset) == null) return Result.failure(
            ValidationException(resourceManager.getString(R.string.common_error_general_title),
                resourceManager.getString(R.string.portfolio_balance_unavailable))
        )
        if (feeEstimation.hasFailed) return Result.failure(feeEstimation.missingFeeError())

        val amountInPlanks = asset.token.planksFromAmount(amount)
        val allValidations = validations.toList()
        val firstError = allValidations.firstNotNullOfOrNull {
            if (it.condition(amountInPlanks)) null else it.error
        }
        return firstError?.let { Result.failure(it) } ?: Result.success(Unit)
    }

    fun onQuickAmountInput(value: Double) {
        viewModelScope.launch {
            val amount = quickInputs[value] ?: return@launch
            onAmountInput(amount.setScale(asset.token.configuration.precision, RoundingMode.HALF_DOWN))
        }
    }
}

class Validation(
    val condition: suspend (amount: BigInteger) -> Boolean,
    val error: ValidationException
)
