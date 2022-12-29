package jp.co.soramitsu.polkaswap.impl.presentation.transaction_settings

import androidx.compose.ui.focus.FocusState
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.NumberInputState
import jp.co.soramitsu.common.compose.component.SelectorState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_polkaswap_impl.R
import jp.co.soramitsu.polkaswap.api.presentation.PolkaswapRouter
import jp.co.soramitsu.polkaswap.impl.domain.models.Market
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.text.NumberFormat
import javax.inject.Inject

@HiltViewModel
class TransactionSettingsViewModel @Inject constructor(
    private val polkaswapRouter: PolkaswapRouter,
    private val resourceManager: ResourceManager
) : BaseViewModel(), TransactionSettingsCallbacks {

    private val selectedMarket = MutableStateFlow(Market.SMART)
    private val slippageInputFocused = MutableStateFlow(false)
    private val slippageToleranceStringValue = MutableStateFlow(DefaultSlippageTolerance)
    private val slippageWarningText = slippageToleranceStringValue
        .map { slippageToleranceString ->
            val number = slippageToleranceString.toDouble()
            when {
                number <= MinWarningSlippageThreshold -> resourceManager.getString(R.string.polkaswap_transaction_may_fail)
                number >= MaxWarningSlippageThreshold -> resourceManager.getString(R.string.polkaswap_transaction_may_frontrun)
                else -> null
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val formatter = NumberFormat.getNumberInstance().apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 1
    }

    val state = combine(selectedMarket, slippageToleranceStringValue, slippageInputFocused, slippageWarningText) {
            selectedMarket, slippageToleranceValue, isSlippageInputFocused, slippageWarningText ->
        TransactionSettingsViewState(
            marketState = getMarketState(selectedMarket),
            slippageInputState = getSlippageTolerance(slippageToleranceValue, isSlippageInputFocused, slippageWarningText),
            slippageWarningText = slippageWarningText
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, TransactionSettingsViewState.default(resourceManager))

    override fun onMarketClick() {
        slippageInputFocused.value = false
        polkaswapRouter.openSelectMarketDialog()
    }

    override fun onCloseClick() {
        polkaswapRouter.back()
    }

    override fun onResetToDefaultClick() {
        selectedMarket.value = Market.SMART
        slippageToleranceStringValue.value = DefaultSlippageTolerance
        slippageInputFocused.value = false
    }

    override fun onSaveClick() {
        // TODO: onSaveClick
        slippageInputFocused.value = false
    }

    override fun onQuickSlippageInput(value: Double) {
        slippageToleranceStringValue.value = format(value)
        slippageInputFocused.value = false
    }

    override fun onSlippageValueChange(value: String) {
        val slippageValue = value.toFloatOrNull() ?: return
        if (slippageValue in SlippageRange) {
            slippageToleranceStringValue.value = value
        }
    }

    override fun onSlippageValueChange(value: Float) {
        slippageToleranceStringValue.value = format(value)
        slippageInputFocused.value = false
    }

    override fun onAmountFocusChanged(focusState: FocusState) {
        slippageInputFocused.value = focusState.isFocused
    }

    private fun getMarketState(market: Market): SelectorState {
        return SelectorState(
            title = resourceManager.getString(R.string.polkaswap_market_title),
            subTitle = market.marketName,
            iconUrl = null
        )
    }

    private fun getSlippageTolerance(
        value: String,
        hasFocus: Boolean,
        slippageWarningText: String?
    ): NumberInputState {
        return NumberInputState(
            title = resourceManager.getString(R.string.polkaswap_slippage_tolerance),
            value = value,
            suffix = "%",
            isFocused = hasFocus,
            warning = slippageWarningText != null
        )
    }

    private fun format(value: Number): String {
        return formatter.format(value)
            .replace(",", ".")
    }

    companion object {
        val SlippageRange = 0f..10f
        private const val DefaultSlippageTolerance = "0.5"

        private const val MinWarningSlippageThreshold = 0.1
        private const val MaxWarningSlippageThreshold = 5.0
    }
}
