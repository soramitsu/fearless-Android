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
import jp.co.soramitsu.common.utils.formatAsCurrency
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.wallet.api.presentation.formatters.formatTokenAmount
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

open class BaseEnterAmountViewModel(
    @StringRes private val nextButtonTextRes: Int = R.string.common_continue,
    @StringRes private val toolbarTextRes: Int = R.string.staking_bond_more_v1_9_0,
    @StringRes private val balanceHintRes: Int,
    initialAmount: BigDecimal = BigDecimal.ZERO,
    isInputActive: Boolean = true,
    protected val asset: Asset,
    private val resourceManager: ResourceManager,
    private val feeEstimator: suspend (BigInteger) -> BigInteger,
    private val onNextStep: suspend (BigInteger) -> Unit,
    private vararg val validations: Validation,
    private val buttonValidation: (BigInteger) -> Boolean = { it != BigInteger.ZERO },
    private val availableAmountForOperation: suspend (Asset) -> BigDecimal = { it.transferable },
    private val errorAlertPresenter: (AlertViewState) -> Unit
) : BaseViewModel() {

    private val defaultAmountInputState = AmountInputViewState(
        tokenName = "...",
        tokenImage = "",
        totalBalance = resourceManager.getString(balanceHintRes, "..."),
        fiatAmount = "",
        tokenAmount = initialAmount,
        initial = null
    )

    private val defaultButtonState = ButtonViewState(
        resourceManager.getString(nextButtonTextRes),
        true
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

    private val enteredAmountFlow = MutableStateFlow(initialAmount)

    private val amountInputViewState: Flow<AmountInputViewState> = enteredAmountFlow.map { amount ->
        val tokenBalance = availableAmountForOperation(asset).formatTokenAmount(asset.token.configuration)
        val fiatAmount = amount.applyFiatRate(asset.token.fiatRate)?.formatAsCurrency(asset.token.fiatSymbol)

        AmountInputViewState(
            tokenName = asset.token.configuration.symbolToShow,
            tokenImage = asset.token.configuration.iconUrl,
            totalBalance = resourceManager.getString(balanceHintRes, tokenBalance),
            fiatAmount = fiatAmount,
            tokenAmount = amount,
            isActive = isInputActive,
            precision = asset.token.configuration.precision,
            initial = amount
        )
    }.stateIn(this, SharingStarted.Eagerly, defaultAmountInputState)

    private val feeInfoViewStateFlow: Flow<FeeInfoViewState> = enteredAmountFlow.map { amount ->
        val inPlanks = asset.token.planksFromAmount(amount)
        val feeInPlanks = feeEstimator(inPlanks)
        val fee = asset.token.amountFromPlanks(feeInPlanks)
        val feeFormatted = fee.formatTokenAmount(asset.token.configuration)
        val feeFiat = fee.applyFiatRate(asset.token.fiatRate)?.formatAsCurrency(asset.token.fiatSymbol)

        FeeInfoViewState(feeAmount = feeFormatted, feeAmountFiat = feeFiat)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, FeeInfoViewState.default)

    private val buttonStateFlow = enteredAmountFlow.map { amount ->
        val amountInPlanks = asset.token.planksFromAmount(amount)
        ButtonViewState(
            resourceManager.getString(nextButtonTextRes),
            buttonValidation(amountInPlanks)
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

    fun onAmountInput(amount: BigDecimal?) {
        enteredAmountFlow.value = amount.orZero()
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
        val amountInPlanks = asset.token.planksFromAmount(amount)
        val allValidations = validations.toList()
        val firstError = allValidations.firstNotNullOfOrNull {
            if (it.condition(amountInPlanks)) null else it.error
        }
        return firstError?.let { Result.failure(it) } ?: Result.success(Unit)
    }
}

class Validation(
    val condition: suspend (amount: BigInteger) -> Boolean,
    val error: ValidationException
)
