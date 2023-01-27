package jp.co.soramitsu.polkaswap.impl.presentation.swap_tokens

import android.util.Log
import androidx.compose.ui.focus.FocusState
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.math.BigInteger
import javax.inject.Inject
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.base.errors.ValidationException
import jp.co.soramitsu.common.compose.component.AmountInputViewState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.applyFiatRate
import jp.co.soramitsu.common.utils.combine
import jp.co.soramitsu.common.utils.flowOf
import jp.co.soramitsu.common.utils.format
import jp.co.soramitsu.common.utils.formatAsCurrency
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.feature_polkaswap_impl.R
import jp.co.soramitsu.polkaswap.api.domain.PathUnavailableException
import jp.co.soramitsu.polkaswap.api.domain.PolkaswapInteractor
import jp.co.soramitsu.polkaswap.api.models.Market
import jp.co.soramitsu.polkaswap.api.models.WithDesired
import jp.co.soramitsu.polkaswap.api.presentation.PolkaswapRouter
import jp.co.soramitsu.polkaswap.api.presentation.models.SwapDetailsViewState
import jp.co.soramitsu.polkaswap.api.presentation.models.TransactionSettingsModel
import jp.co.soramitsu.polkaswap.impl.presentation.transaction_settings.TransactionSettingsFragment
import jp.co.soramitsu.wallet.api.presentation.WalletRouter
import jp.co.soramitsu.wallet.api.presentation.formatters.formatTokenAmount
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapConcat
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
    private var slippageTolerance = MutableStateFlow(0.5)

    private val fromAsset = MutableStateFlow<Asset?>(null)
    private val toAsset = MutableStateFlow<Asset?>(null)

    private var desired: WithDesired? = null

    private val dexes = flowOf { polkaswapInteractor.getAvailableDexes() }.stateIn(viewModelScope, SharingStarted.Eagerly, listOf())

    private val poolReservesFlow = combine(fromAsset, toAsset, selectedMarket) { fromAsset, toAsset, selectedMarket ->
        if (fromAsset == null || toAsset == null) return@combine null

        val tokenFromId = requireNotNull(fromAsset.token.configuration.currencyId)
        val tokenToId = requireNotNull(toAsset.token.configuration.currencyId)

        (tokenFromId to tokenToId) to selectedMarket
    }.flatMapConcat {
        it ?: return@flatMapConcat kotlinx.coroutines.flow.flowOf(Unit)
        val (assets, market) = it
        val (fromAsset, toAsset) = assets
        polkaswapInteractor.observePoolReserves(fromAsset, toAsset, market)
    }

    private val swapDetails = combine(
        fromAmountInputViewState.debounce(200),
        toAmountInputViewState.debounce(200),
        fromAsset,
        toAsset,
        selectedMarket,
        slippageTolerance,
        dexes,
        poolReservesFlow,
        transform = ::getSwapDetails
    ).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private var assetResultJob: Job? = null

    private var transactionSettingsJob: Job? = null

    val state = combine(
        fromAmountInputViewState,
        toAmountInputViewState,
        selectedMarket,
        swapDetails
    ) { fromAmountInput, toAmountInput, selectedMarket, swapDetails ->
        SwapTokensContentViewState(
            fromAmountInputViewState = fromAmountInput,
            toAmountInputViewState = toAmountInput,
            selectedMarket = selectedMarket,
            swapDetailsViewState = swapDetails
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SwapTokensContentViewState.default(resourceManager))

    init {
        initFromAsset()
        subscribeFromAmountInputViewState()
        subscribeToAmountInputViewState()

        transactionSettingsJob?.cancel()
        transactionSettingsJob = polkaswapRouter.observeResult<TransactionSettingsModel>(TransactionSettingsFragment.SETTINGS_MODEL_KEY)
            .onEach {
                selectedMarket.value = it.market
                slippageTolerance.value = it.slippageTolerance
            }
            .launchIn(viewModelScope)
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

    private suspend fun getSwapDetails(
        fromAmountInputViewState: AmountInputViewState,
        toAmountInputViewState: AmountInputViewState,
        fromAsset: Asset?,
        toAsset: Asset?,
        selectedMarket: Market,
        slippageTolerance: Double,
        dexes: List<BigInteger>,
        reserves: Any
    ): SwapDetailsViewState? {
        Log.d("&&&", "getSwapDetails from: ${fromAmountInputViewState.tokenAmount}")
        Log.d("&&&", "getSwapDetails to: ${toAmountInputViewState.tokenAmount}")
        fromAsset ?: return null
        toAsset ?: return null
        desired ?: return null
        if (dexes.isEmpty()) return null

        polkaswapInteractor.fetchAvailableSources(fromAsset, toAsset, dexes)

        val amount = try {
            when (desired) {
                WithDesired.INPUT -> fromAmountInputViewState.tokenAmount
                WithDesired.OUTPUT -> toAmountInputViewState.tokenAmount
                null -> return null
            }.toBigDecimal()
        } catch (e: java.lang.NumberFormatException) {
            return null
        }
        if (amount == BigDecimal.ZERO) return null

        val detailsCalcResult = polkaswapInteractor.calcDetails(dexes, fromAsset, toAsset, amount, desired!!, slippageTolerance, selectedMarket)
        return detailsCalcResult.fold(
            onSuccess = { details ->
                if (details == null) {
                    showError(
                        ValidationException(
                            resourceManager.getString(R.string.common_error_general_title),
                            resourceManager.getString(R.string.polkaswap_insufficient_liqudity)
                        )
                    )
                    return null
                }

                var fromAmount = ""
                var toAmount = ""
                when {
                    desired == WithDesired.INPUT -> {
                        enteredToAmountFlow.value = details.amount.format()
                        fromAmount = amount.formatTokenAmount(fromAsset.token.configuration)
                        toAmount = details.amount.formatTokenAmount(toAsset.token.configuration)
                    }
                    desired == WithDesired.OUTPUT -> {
                        enteredFromAmountFlow.value = details.amount.format()
                        fromAmount = details.amount.formatTokenAmount(fromAsset.token.configuration)
                        toAmount = amount.formatTokenAmount(toAsset.token.configuration)
                    }
                }
                val (minMaxTitle, minMax) = when (desired) {
                    WithDesired.INPUT -> resourceManager.getString(R.string.common_min_received) to
                        (details.minMax.formatTokenAmount(toAsset.token.configuration) to
                            toAsset.token.fiatAmount(details.minMax)?.formatAsCurrency(toAsset.token.fiatSymbol))

                    WithDesired.OUTPUT ->
                        resourceManager.getString(R.string.polkaswap_maximum_sold) to
                            (details.minMax.formatTokenAmount(fromAsset.token.configuration) to
                                fromAsset.token.fiatAmount(details.minMax)?.formatAsCurrency(fromAsset.token.fiatSymbol))

                    null -> null to null
                }

                val tokenFromId = requireNotNull(fromAsset.token.configuration.currencyId)
                val tokenToId = requireNotNull(toAsset.token.configuration.currencyId)

                SwapDetailsViewState(
                    fromTokenId = tokenFromId,
                    toTokenId = tokenToId,
                    fromTokenName = fromAsset.token.configuration.symbolToShow.uppercase(),
                    toTokenName = toAsset.token.configuration.symbolToShow.uppercase(),
                    fromTokenImage = fromAsset.token.configuration.iconUrl,
                    toTokenImage = toAsset.token.configuration.iconUrl,
                    fromTokenAmount = fromAmount,
                    toTokenAmount = toAmount,
                    minmaxTitle = minMaxTitle.orEmpty(),
                    toTokenMinReceived = minMax?.first.orEmpty(),
                    toFiatMinReceived = minMax?.second.orEmpty(),
                    fromTokenOnToToken = details.fromTokenOnToToken.format(),
                    toTokenOnFromToken = details.toTokenOnFromToken.format(),
                    networkFee = SwapDetailsViewState.NetworkFee(
                        details.feeAsset.token.configuration.symbolToShow.uppercase(),
                        details.networkFee.formatTokenAmount(details.feeAsset.token.configuration),
                        details.feeAsset.token.fiatAmount(details.networkFee)?.formatAsCurrency(details.feeAsset.token.fiatSymbol)
                    ),
                    liquidityProviderFee = SwapDetailsViewState.NetworkFee(
                        details.feeAsset.token.configuration.symbolToShow.uppercase(),
                        details.liquidityProviderFee.formatTokenAmount(details.feeAsset.token.configuration),
                        details.feeAsset.token.fiatAmount(details.liquidityProviderFee)?.formatAsCurrency(details.feeAsset.token.fiatSymbol)
                    )
                )
            },
            onFailure = {
                val error = when (it) {
                    is PathUnavailableException -> ValidationException(
                        resourceManager.getString(R.string.common_error_general_title),
                        resourceManager.getString(R.string.polkaswap_path_unavailable_message)
                    )
                    else -> it
                }
                showError(error)
                null
            })
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
        val toAssetModel = toAsset.value
        toAsset.value = null
        fromAsset.value = toAssetModel
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
        val initialSettings = TransactionSettingsModel(selectedMarket.value, slippageTolerance.value)
        polkaswapRouter.openTransactionSettingsDialog(initialSettings)
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
