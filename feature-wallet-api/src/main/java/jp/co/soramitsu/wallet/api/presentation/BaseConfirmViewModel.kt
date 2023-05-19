package jp.co.soramitsu.wallet.api.presentation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.AlertViewState
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.base.errors.TitledException
import jp.co.soramitsu.common.base.errors.ValidationException
import jp.co.soramitsu.common.compose.component.ConfirmScreenViewState
import jp.co.soramitsu.common.compose.component.GradientIconState
import jp.co.soramitsu.common.compose.component.TitleValueViewState
import jp.co.soramitsu.common.compose.component.ToolbarViewState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.applyFiatRate
import jp.co.soramitsu.common.utils.flowOf
import jp.co.soramitsu.common.utils.formatCryptoDetail
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.common.validation.FeeInsufficientBalanceException
import jp.co.soramitsu.common.validation.WaitForFeeCalculationException
import jp.co.soramitsu.feature_wallet_api.R
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.api.domain.ExistentialDepositUseCase
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks
import jp.co.soramitsu.wallet.impl.domain.model.planksFromAmount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigInteger

abstract class BaseConfirmViewModel(
    private val existentialDepositUseCase: ExistentialDepositUseCase,
    private val resourceManager: ResourceManager,
    protected val asset: Asset,
    protected val chain: Chain,
    protected val address: String,
    protected val amountInPlanks: BigInteger? = null,
    @StringRes protected val titleRes: Int,
    @StringRes protected val additionalMessageRes: Int? = null,
    @DrawableRes protected val customIcon: Int? = null,
    private val feeEstimator: suspend (BigInteger?) -> BigInteger,
    private val executeOperation: suspend (String, BigInteger?) -> Result<String>,
    private val accountNameProvider: suspend (String) -> String?,
    private val onOperationSuccess: (String) -> Unit,
    private val errorAlertPresenter: (AlertViewState) -> Unit
) : BaseViewModel() {

    private val amount = amountInPlanks?.let { asset.token.amountFromPlanks(it) }
    private val amountFormatted = amount?.formatCryptoDetail(asset.token.configuration.symbolToShow)
    private val amountFiat = amount?.applyFiatRate(asset.token.fiatRate)?.formatFiat(asset.token.fiatSymbol)

    private val toolbarViewState = ToolbarViewState(
        resourceManager.getString(R.string.common_confirm),
        R.drawable.ic_arrow_back_24dp
    )

    protected val defaultAddressViewState = TitleValueViewState(
        resourceManager.getString(R.string.transaction_details_from),
        address
    )

    protected val addressViewStateFlow = flowOf {
        val name = accountNameProvider(address)
        if (name.isNullOrEmpty()) {
            defaultAddressViewState.copy(value = address, additionalValue = null)
        } else {
            defaultAddressViewState.copy(value = name, additionalValue = address)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, defaultAddressViewState)

    protected val amountViewState = TitleValueViewState(
        resourceManager.getString(R.string.common_amount),
        amountFormatted,
        amountFiat
    )

    protected val feeInPlanksFlow = flowOf {
        val amountInPlanks = amount?.let { asset.token.planksFromAmount(it) }
        feeEstimator(amountInPlanks)
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        null
    )

    val feeViewStateFlow = feeInPlanksFlow.filterNotNull().map { feeInPlanks ->
        val fee = asset.token.amountFromPlanks(feeInPlanks)
        val feeFormatted = fee.formatCryptoDetail(asset.token.configuration.symbolToShow)
        val feeFiat = fee.formatFiat(asset.token.fiatSymbol)
        TitleValueViewState(
            resourceManager.getString(R.string.network_fee),
            feeFormatted,
            feeFiat
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        defaultFeeState
    )

    open val tableItemsFlow: StateFlow<List<TitleValueViewState>> = combine(feeViewStateFlow, addressViewStateFlow) { feeViewState, addressViewState ->
        listOf(addressViewState, amountViewState, feeViewState)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val isLoadingStateFlow = MutableStateFlow(false)

    val viewState by lazy {
        combine(tableItemsFlow, isLoadingStateFlow) { tableItems, isLoading ->
            val icon = if (customIcon != null) {
                GradientIconState.Local(customIcon)
            } else {
                GradientIconState.Remote(asset.token.configuration.iconUrl, asset.token.configuration.color)
            }

            ConfirmScreenViewState(
                toolbarViewState,
                amount = amountViewState.value,
                tableItems = tableItems,
                icon,
                titleRes,
                additionalMessageRes,
                isLoading
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, defaultScreenState)
    }

    fun onConfirm() {
        launch {
            isLoadingStateFlow.value = true
            isValid().fold({ validationPassed() }, ::showError)
            isLoadingStateFlow.value = false
        }
    }

    protected open suspend fun isValid(): Result<Any> {
        val fee = feeInPlanksFlow.value ?: return Result.failure(WaitForFeeCalculationException(resourceManager))

        val chargesAmount = amountInPlanks.orZero() + fee
        val existentialDeposit = existentialDepositUseCase(asset.token.configuration)

        val resultBalance = asset.transferableInPlanks - chargesAmount
        if (resultBalance < existentialDeposit || resultBalance <= BigInteger.ZERO) {
            return Result.failure(FeeInsufficientBalanceException(resourceManager))
        }
        return Result.success(Unit)
    }

    private suspend fun validationPassed() {
        executeOperation(address, amountInPlanks).fold({
            onOperationSuccess(it)
        }, ::showError)
    }

    override fun showError(throwable: Throwable) {
        val message =
            throwable.localizedMessage ?: throwable.message ?: resourceManager.getString(R.string.common_undefined_error_message)
        val errorAlertViewState = when (throwable) {
            is ValidationException -> {
                val (title, message) = throwable
                AlertViewState(
                    title = title,
                    message = message,
                    buttonText = resourceManager.getString(R.string.common_got_it),
                    iconRes = R.drawable.ic_status_warning_16
                )
            }

            is TitledException -> {
                AlertViewState(
                    title = throwable.title,
                    message = message,
                    buttonText = resourceManager.getString(R.string.common_got_it),
                    iconRes = R.drawable.ic_status_warning_16
                )
            }

            else -> {
                AlertViewState(
                    title = resourceManager.getString(R.string.common_error_general_title),
                    message = message,
                    buttonText = resourceManager.getString(R.string.common_got_it),
                    iconRes = R.drawable.ic_status_warning_16
                )
            }
        }
        errorAlertPresenter(errorAlertViewState)
    }

    private val defaultFeeState
        get() = TitleValueViewState(
            resourceManager.getString(R.string.network_fee),
            null,
            null
        )

    private val defaultScreenState
        get() = ConfirmScreenViewState(
            toolbarViewState,
            amountViewState.value.orEmpty(),
            listOf(
                defaultAddressViewState,
                amountViewState,
                defaultFeeState
            ),
            GradientIconState.Remote(asset.token.configuration.iconUrl, asset.token.configuration.color),
            titleRes,
            additionalMessageRes,
            isLoading = false
        )
}
