package jp.co.soramitsu.polkaswap.impl.presentation.swap_tokens

import androidx.compose.ui.focus.FocusState
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import javax.inject.Inject
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.AmountInputViewState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.applyFiatRate
import jp.co.soramitsu.common.utils.formatAsCurrency
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.feature_polkaswap_impl.R
import jp.co.soramitsu.polkaswap.api.domain.PolkaswapInteractor
import jp.co.soramitsu.polkaswap.api.models.Market
import jp.co.soramitsu.polkaswap.api.models.WithDesired
import jp.co.soramitsu.polkaswap.api.presentation.PolkaswapRouter
import jp.co.soramitsu.polkaswap.api.presentation.models.SwapDetails
import jp.co.soramitsu.wallet.api.presentation.WalletRouter
import jp.co.soramitsu.wallet.api.presentation.formatters.formatTokenAmount
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SwapTokensViewModel @Inject constructor(
    private val resourceManager: ResourceManager,
    private val polkaswapInteractor: PolkaswapInteractor,
    private val polkaswapRouter: PolkaswapRouter,
    savedStateHandle: SavedStateHandle
) : BaseViewModel(), SwapTokensCallbacks {

    private val enteredFromAmountFlow = MutableStateFlow("0")
    private val enteredToAmountFlow = MutableStateFlow("0")

    private val isFromAmountFocused = MutableStateFlow(false)
    private val isToAmountFocused = MutableStateFlow(false)

    private val initFromAssetId = savedStateHandle.get<String>(SwapTokensFragment.KEY_SELECTED_ASSET_ID)

    private val fromAmountInputViewState = MutableStateFlow(AmountInputViewState.default(resourceManager))
    private val toAmountInputViewState = MutableStateFlow(AmountInputViewState.default(resourceManager))

    private var selectedMarket = MutableStateFlow(Market.SMART)

    private val fromAsset = MutableStateFlow<Asset?>(null)
    private val toAsset = MutableStateFlow<Asset?>(null)

    private var desired: WithDesired? = null

    private val swapDetails = combine(
        fromAmountInputViewState,
        toAmountInputViewState,
        fromAsset,
        toAsset,
        transform = ::getSwapDetails
    )
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private var assetResultJob: Job? = null

    val state = combine(
        fromAmountInputViewState,
        toAmountInputViewState,
        selectedMarket,
        swapDetails
    ) { (fromAmountInput, toAmountInput, selectedMarket, swapDetails) ->
        SwapTokensContentViewState(
            fromAmountInputViewState = fromAmountInput as AmountInputViewState,
            toAmountInputViewState = toAmountInput as AmountInputViewState,
            selectedMarket = selectedMarket as Market,
            swapDetails = swapDetails as SwapDetails?
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SwapTokensContentViewState.default(resourceManager))

    init {
        initFromAsset()
        subscribeFromAmountInputViewState()
        subscribeToAmountInputViewState()
    }

    private fun observeResultFor(assetFlow: MutableStateFlow<Asset?>) {
        assetResultJob?.cancel()
        assetResultJob = polkaswapRouter.observeResult<String>(WalletRouter.KEY_ASSET_ID)
            .map(polkaswapInteractor::getAsset)
            .onEach { assetFlow.value = it }
            .onEach { assetResultJob?.cancel() }
            .catch {
                // TODO: Logging
            }
            .launchIn(viewModelScope)
    }

    private fun getSwapDetails(
        fromAmountInputViewState: AmountInputViewState,
        toAmountInputViewState: AmountInputViewState,
        fromAsset: Asset?,
        toAsset: Asset?
    ): SwapDetails? {
        val fromAssetId = fromAsset?.token?.configuration?.id ?: return null
        val toAssetId = toAsset?.token?.configuration?.id ?: return null

        val fromTokenName = fromAmountInputViewState.tokenName ?: return null
        val toTokenName = toAmountInputViewState.tokenName ?: return null

        val fromTokenAmount = fromAmountInputViewState.tokenAmount.toBigDecimal()
            .takeIf { it != BigDecimal.ZERO } ?: return null
        val toTokenAmount = toAmountInputViewState.tokenAmount.toBigDecimal()
            .takeIf { it != BigDecimal.ZERO } ?: return null

        val fromTokenImage = fromAmountInputViewState.tokenImage
        val toTokenImage = toAmountInputViewState.tokenImage

        val toFiatAmount = toAmountInputViewState.fiatAmount.orEmpty()

        return SwapDetails(
            fromTokenId = fromAssetId,
            toTokenId = toAssetId,
            fromTokenName = fromTokenName.uppercase(),
            toTokenName = toTokenName.uppercase(),
            fromTokenImage = fromTokenImage,
            toTokenImage = toTokenImage,
            toTokenMinReceived = toTokenAmount,
            toFiatMinReceived = toFiatAmount,
            fromTokenAmount = fromTokenAmount,
            toTokenAmount = toTokenAmount,
            networkFee = SwapDetails.NetworkFee(
                tokenName = "",
                tokenAmount = BigDecimal.ONE,
                fiatAmount = ""
            ),
            liquidityProviderFee = SwapDetails.NetworkFee(
                tokenName = "",
                tokenAmount = BigDecimal.ONE,
                fiatAmount = ""
            ),
            fromTokenOnToToken = BigDecimal.ZERO,
            toTokenOnFromToken = BigDecimal.ZERO
        )
    }

    private fun getAmountInputViewState(
        title: String,
        enteredAmount: String,
        asset: Asset?,
        isFocused: Boolean
    ): AmountInputViewState {
        if (asset == null) {
            return AmountInputViewState(
                tokenName = null,
                tokenImage = null,
                totalBalance = resourceManager.getString(R.string.common_balance_format, "0"),
                fiatAmount = null,
                tokenAmount = enteredAmount,
                title = title,
                allowAssetChoose = true,
                isFocused = isFocused
            )
        }

        val tokenBalance = asset.transferable.formatTokenAmount(asset.token.configuration)
        val amount = enteredAmount.toBigDecimalOrNull().orZero()
        val fiatAmount = amount.applyFiatRate(asset.token.fiatRate)?.formatAsCurrency(asset.token.fiatSymbol)

        return AmountInputViewState(
            tokenName = asset.token.configuration.symbolToShow,
            tokenImage = asset.token.configuration.iconUrl,
            totalBalance = resourceManager.getString(R.string.common_balance_format, tokenBalance),
            fiatAmount = fiatAmount,
            tokenAmount = enteredAmount,
            title = title,
            allowAssetChoose = true,
            isFocused = isFocused
        )
    }

    private fun initFromAsset() {
        viewModelScope.launch {
            fromAsset.value = initFromAssetId?.let {
                polkaswapInteractor.getAsset(it)
            }
        }
    }

    private fun subscribeFromAmountInputViewState() {
        combine(enteredFromAmountFlow, fromAsset, isFromAmountFocused) { enteredAmount, asset, isFromAmountFocused ->
            fromAmountInputViewState.value = getAmountInputViewState(
                title = resourceManager.getString(R.string.polkaswap_from),
                enteredAmount = enteredAmount,
                asset = asset,
                isFocused = isFromAmountFocused
            )
            if (isFromAmountFocused) {
                desired = WithDesired.INPUT
            }
        }
            .launchIn(viewModelScope)
    }

    private fun subscribeToAmountInputViewState() {
        combine(enteredToAmountFlow, toAsset, isToAmountFocused) { enteredAmount, asset, isToAmountFocused ->
            toAmountInputViewState.value = getAmountInputViewState(
                title = resourceManager.getString(R.string.polkaswap_to),
                enteredAmount = enteredAmount,
                asset = asset,
                isFocused = isToAmountFocused
            )
            if (isToAmountFocused) {
                desired = WithDesired.OUTPUT
            }
        }
            .launchIn(viewModelScope)
    }

    override fun onChangeTokensClick() {
        val fromAssetModel = fromAsset.value
        fromAsset.value = toAsset.value
        toAsset.value = fromAssetModel

        val enteredFromAmountModel = enteredFromAmountFlow.value
        enteredFromAmountFlow.value = enteredToAmountFlow.value
        enteredToAmountFlow.value = enteredFromAmountModel
    }

    override fun onPreviewClick() {
        val swapDetailsValue = swapDetails.value ?: return

        polkaswapRouter.openSwapPreviewDialog(swapDetailsValue)
    }

    override fun onBackClick() {
        polkaswapRouter.back()
    }

    override fun onFromAmountChange(amount: String) {
        enteredFromAmountFlow.value = amount.replace(',', '.')
    }

    override fun onToAmountChange(amount: String) {
        enteredToAmountFlow.value = amount.replace(',', '.')
    }

    override fun onMarketSettingsClick() {
        polkaswapRouter.openTransactionSettingsDialog()
    }

    override fun onFromTokenSelect() {
        openSelectAsset(
            selectedAssetFlow = fromAsset,
            excludeAssetFlow = toAsset
        )
    }

    override fun onToTokenSelect() {
        openSelectAsset(
            selectedAssetFlow = toAsset,
            excludeAssetFlow = fromAsset
        )
    }

    override fun onFromAmountFocusChange(focusState: FocusState) {
        isFromAmountFocused.value = focusState.hasFocus
    }

    override fun onToAmountFocusChange(focusState: FocusState) {
        isToAmountFocused.value = focusState.hasFocus
    }

    private fun openSelectAsset(
        selectedAssetFlow: MutableStateFlow<Asset?>,
        excludeAssetFlow: MutableStateFlow<Asset?>
    ) {
        observeResultFor(selectedAssetFlow)
        val selectedAssetId = selectedAssetFlow.value?.token?.configuration?.id
        val excludeAssetId = excludeAssetFlow.value?.token?.configuration?.id
        polkaswapRouter.openSelectAsset(
            chainId = polkaswapInteractor.polkaswapChainId,
            selectedAssetId = selectedAssetId,
            excludeAssetId = excludeAssetId
        )
    }
}
