package jp.co.soramitsu.wallet.api.presentation

import androidx.annotation.StringRes
import androidx.lifecycle.viewModelScope
import java.math.BigDecimal
import java.math.BigInteger
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

open class BaseEnterAmountViewModel(
    @StringRes private val nextButtonTextRes: Int = R.string.common_continue,
    @StringRes private val toolbarTextRes: Int = R.string.staking_bond_more_v1_9_0,
    initialAmount: String = "0",
    isInputActive: Boolean = true,
    protected val asset: Asset,
    private val resourceManager: ResourceManager,
    private val feeEstimator: suspend (BigInteger) -> BigInteger,
    private val onNextStep: (BigInteger) -> Unit,
    private vararg val validations: Validation,
    private val buttonValidation: (BigInteger) -> Boolean = { it != BigInteger.ZERO }
) : BaseViewModel() {

    private val defaultAmountInputState = AmountInputViewState(
        tokenName = "...",
        tokenImage = "",
        totalBalance = resourceManager.getString(R.string.common_balance_format, "..."),
        fiatAmount = "",
        tokenAmount = initialAmount
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

    private val amountInputViewState: Flow<AmountInputViewState> = enteredAmountFlow.map { enteredAmount ->
        val tokenBalance = asset.transferable.formatTokenAmount(asset.token.configuration)
        val amount = enteredAmount.toBigDecimalOrNull().orZero()
        val fiatAmount = amount.applyFiatRate(asset.token.fiatRate)?.formatAsCurrency(asset.token.fiatSymbol)

        AmountInputViewState(
            tokenName = asset.token.configuration.symbolToShow,
            tokenImage = asset.token.configuration.iconUrl,
            totalBalance = resourceManager.getString(R.string.common_balance_format, tokenBalance),
            fiatAmount = fiatAmount,
            tokenAmount = enteredAmount,
            isActive = isInputActive
        )
    }.stateIn(this, SharingStarted.Eagerly, defaultAmountInputState)

    private val feeInfoViewStateFlow: Flow<FeeInfoViewState> = enteredAmountFlow.map { enteredAmount ->
        val amount = enteredAmount.toBigDecimalOrNull().orZero()
        val inPlanks = asset.token.planksFromAmount(amount)
        val feeInPlanks = feeEstimator(inPlanks)
        val fee = asset.token.amountFromPlanks(feeInPlanks)
        val feeFormatted = fee.formatTokenAmount(asset.token.configuration)
        val feeFiat = fee.applyFiatRate(asset.token.fiatRate)?.formatAsCurrency(asset.token.fiatSymbol)

        FeeInfoViewState(feeAmount = feeFormatted, feeAmountFiat = feeFiat)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, FeeInfoViewState.default)

    private val buttonStateFlow = enteredAmountFlow.map { enteredAmount ->
        val amount = enteredAmount.toBigDecimalOrNull().orZero()
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

    fun onAmountInput(amount: String) {
        enteredAmountFlow.value = amount.replace(',', '.')
    }

    fun onNextClick() {
        val amount = enteredAmountFlow.value.toBigDecimalOrNull().orZero()
        val inPlanks = asset.token.planksFromAmount(amount)
        isValid(amount).fold({
            onNextStep(inPlanks)
        }, {
            showError(it)
        })
    }

    private fun isValid(amount: BigDecimal): Result<Any> {
        val amountInPlanks = asset.token.planksFromAmount(amount)
        val allValidations = validations.toList()
        val firstError = allValidations.mapNotNull {
            if (it.condition(amountInPlanks)) null else it.error
        }.firstOrNull()
        return firstError?.let { Result.failure(it) } ?: Result.success(Unit)
    }
}

class Validation(
    val condition: (amount: BigInteger) -> Boolean,
    val error: ValidationException
)
