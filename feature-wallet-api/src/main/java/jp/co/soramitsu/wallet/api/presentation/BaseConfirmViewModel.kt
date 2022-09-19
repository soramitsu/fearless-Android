package jp.co.soramitsu.wallet.api.presentation

import androidx.annotation.StringRes
import androidx.lifecycle.viewModelScope
import java.math.BigInteger
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.ConfirmScreenViewState
import jp.co.soramitsu.common.compose.component.TitleValueViewState
import jp.co.soramitsu.common.compose.component.ToolbarViewState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.applyFiatRate
import jp.co.soramitsu.common.utils.flowOf
import jp.co.soramitsu.common.utils.formatAsCurrency
import jp.co.soramitsu.feature_wallet_api.R
import jp.co.soramitsu.wallet.api.presentation.formatters.formatTokenAmount
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks
import jp.co.soramitsu.wallet.impl.domain.model.planksFromAmount
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

open class BaseConfirmViewModel(
    private val resourceManager: ResourceManager,
    protected val asset: Asset,
    protected val address: String,
    protected val amountInPlanks: BigInteger,
    @StringRes protected val titleRes: Int,
    @StringRes protected val additionalMessageRes: Int? = null,
    private val feeEstimator: suspend (BigInteger) -> BigInteger,
    private val executeOperation: suspend (String, BigInteger) -> Result<Any>,
    private val accountNameProvider: suspend (String) -> String?,
    private val onOperationSuccess: () -> Unit
) : BaseViewModel() {

    private val amount = asset.token.amountFromPlanks(amountInPlanks)
    private val amountFormatted = amount.formatTokenAmount(asset.token.configuration)
    private val amountFiat = amount.applyFiatRate(asset.token.fiatRate)?.formatAsCurrency(asset.token.fiatSymbol)

    private val toolbarViewState = ToolbarViewState(
        resourceManager.getString(R.string.common_confirm),
        R.drawable.ic_arrow_back_24dp
    )

    private val defaultAddressViewState = TitleValueViewState(
        resourceManager.getString(R.string.transaction_details_from),
        address
    )

    private val addressViewStateFlow = flowOf {
        val name = accountNameProvider(address)
        if (name.isNullOrEmpty()) {
            defaultAddressViewState.copy(value = address, additionalValue = null)
        } else {
            defaultAddressViewState.copy(value = name, additionalValue = address)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, defaultAddressViewState)

    private val amountViewState = TitleValueViewState(
        resourceManager.getString(R.string.common_amount),
        amountFormatted,
        amountFiat
    )

    private val feeViewStateFlow = flowOf {
        val amountInPlanks = asset.token.planksFromAmount(amount)
        val feeInPlanks = feeEstimator(amountInPlanks)
        val fee = asset.token.amountFromPlanks(feeInPlanks)
        val feeFormatted = fee.formatTokenAmount(asset.token.configuration)
        val feeFiat = fee.formatAsCurrency(asset.token.fiatSymbol)
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

    val viewState = combine(feeViewStateFlow, addressViewStateFlow) { feeViewState, addressViewState ->
        ConfirmScreenViewState(
            toolbarViewState,
            addressViewState,
            amountViewState,
            feeViewState,
            asset.token.configuration.iconUrl,
            titleRes,
            additionalMessageRes
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, defaultScreenState)

    fun onConfirm() {
        launch {
            executeOperation(address, amountInPlanks).fold({
                onOperationSuccess()
            }, {
                showError(it)
            })
        }
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
            defaultAddressViewState,
            amountViewState,
            defaultFeeState,
            asset.token.configuration.iconUrl,
            titleRes,
            additionalMessageRes
        )
}
