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

    private val formatter = NumberFormat.getNumberInstance().apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 1
    }

    val state = combine(selectedMarket, slippageToleranceStringValue, slippageInputFocused) {
            selectedMarket, slippageToleranceValue, isSlippageInputFocused ->
        TransactionSettingsViewState(
            marketState = getMarketState(selectedMarket),
            slippageInputState = getSlippageTolerance(slippageToleranceValue, isSlippageInputFocused)
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, TransactionSettingsViewState.default(resourceManager))

    override fun onMarketClick() {
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
        slippageToleranceStringValue.value = formatter.format(value)
        slippageInputFocused.value = false
    }

    override fun onSlippageValueChange(value: String) {
        val slippageValue = value.toFloatOrNull() ?: return
        if (slippageValue in SlippageRange) {
            slippageToleranceStringValue.value = value
        }
    }

    override fun onSlippageValueChange(value: Float) {
        slippageToleranceStringValue.value = formatter.format(value)
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

    private fun getSlippageTolerance(value: String, hasFocus: Boolean): NumberInputState {
        return NumberInputState(
            title = resourceManager.getString(R.string.polkaswap_slippage_tolerance),
            value = value,
            suffix = "%",
            isFocused = hasFocus
        )
    }

    companion object {
        val SlippageRange = 0f..10f
        private const val DefaultSlippageTolerance = "0.5"
    }
}
