package jp.co.soramitsu.polkaswap.impl.presentation.swap_tokens

import androidx.annotation.StringRes
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.base.errors.ValidationException
import jp.co.soramitsu.common.compose.component.AmountInputViewState
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.presentation.dataOrNull
import jp.co.soramitsu.common.presentation.map
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.applyFiatRate
import jp.co.soramitsu.common.utils.combine
import jp.co.soramitsu.common.utils.flowOf
import jp.co.soramitsu.common.utils.formatCryptoDetail
import jp.co.soramitsu.common.utils.formatCrypto
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.common.validation.NotEnoughResultedAmountToPayFeeException
import jp.co.soramitsu.common.validation.SpendInsufficientBalanceException
import jp.co.soramitsu.common.validation.UnableToPayFeeException
import jp.co.soramitsu.common.validation.WaitForFeeCalculationException
import jp.co.soramitsu.feature_polkaswap_impl.R
import jp.co.soramitsu.polkaswap.api.domain.InsufficientLiquidityException
import jp.co.soramitsu.polkaswap.api.domain.PathUnavailableException
import jp.co.soramitsu.polkaswap.api.domain.PolkaswapInteractor
import jp.co.soramitsu.polkaswap.api.domain.models.SwapDetails
import jp.co.soramitsu.polkaswap.api.models.Market
import jp.co.soramitsu.polkaswap.api.models.WithDesired
import jp.co.soramitsu.polkaswap.api.presentation.PolkaswapRouter
import jp.co.soramitsu.polkaswap.api.presentation.models.SwapDetailsParcelModel
import jp.co.soramitsu.polkaswap.api.presentation.models.SwapDetailsViewState
import jp.co.soramitsu.polkaswap.api.presentation.models.TransactionSettingsModel
import jp.co.soramitsu.polkaswap.api.presentation.models.detailsToViewState
import jp.co.soramitsu.polkaswap.impl.presentation.transaction_settings.TransactionSettingsFragment
import jp.co.soramitsu.wallet.api.presentation.WalletRouter
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks
import jp.co.soramitsu.wallet.impl.domain.model.planksFromAmount
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.BigInteger
import javax.inject.Inject

typealias TooltipEvent = Event<Pair<String, String>>

@HiltViewModel
class SwapTokensViewModel @Inject constructor(
    private val resourceManager: ResourceManager,
    private val polkaswapInteractor: PolkaswapInteractor,
    private val polkaswapRouter: PolkaswapRouter,
    savedStateHandle: SavedStateHandle
) : BaseViewModel(), SwapTokensCallbacks {

    private val _showMarketsWarningEvent = MutableLiveData<Event<Unit>>()
    val showMarketsWarningEvent: LiveData<Event<Unit>> = _showMarketsWarningEvent

    private val _showTooltipEvent = MutableLiveData<TooltipEvent>()
    val showTooltipEvent: LiveData<TooltipEvent> = _showTooltipEvent

    private val enteredFromAmountFlow = MutableStateFlow(BigDecimal.ZERO)
    private val enteredToAmountFlow = MutableStateFlow(BigDecimal.ZERO)

    private val initFromAmountFlow = MutableStateFlow<BigDecimal?>(null)
    private val initToAmountFlow = MutableStateFlow<BigDecimal?>(null)

    private val isFromAmountFocused = MutableStateFlow(false)
    private val isToAmountFocused = MutableStateFlow(false)

    private val initFromAssetId = savedStateHandle.get<String>(SwapTokensFragment.KEY_SELECTED_ASSET_FROM_ID)
    private val initToAssetId = savedStateHandle.get<String>(SwapTokensFragment.KEY_SELECTED_ASSET_TO_ID)
    private val initFromChainId = savedStateHandle.get<String>(SwapTokensFragment.KEY_SELECTED_CHAIN_ID)

    private val fromAmountInputViewState = MutableStateFlow(AmountInputViewState.default(resourceManager, R.string.common_available_format))
    private val toAmountInputViewState = MutableStateFlow(AmountInputViewState.default(resourceManager))

    private var selectedMarket = MutableStateFlow(Market.SMART)
    private var slippageTolerance = MutableStateFlow(0.5)

    private val fromAsset = MutableStateFlow<Asset?>(null)
    private val toAsset = MutableStateFlow<Asset?>(null)

    private var desired: WithDesired? = null

    private val dexes = flowOf { polkaswapInteractor.getAvailableDexes() }.stateIn(viewModelScope, SharingStarted.Eagerly, listOf())

    private val isLoading = MutableStateFlow(false)
    private var initialFee = BigDecimal.ZERO
    private val availableDexPathsFlow: MutableStateFlow<List<Int>?> = MutableStateFlow(null)

    @OptIn(FlowPreview::class)
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

    private val amountInput = combine(enteredFromAmountFlow, enteredToAmountFlow) { fromInput, toInput ->
        when (desired) {
            WithDesired.INPUT -> fromInput
            WithDesired.OUTPUT -> toInput
            null -> BigDecimal.ZERO
        }
    }
        .catch { emit(BigDecimal.ZERO) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, BigDecimal.ZERO)

    private val swapDetails = combine(
        amountInput,
        selectedMarket,
        slippageTolerance,
        availableDexPathsFlow,
        poolReservesFlow,
        transform = ::getSwapDetails
    ).catch {
        emit(Result.failure(it))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, Result.success(null))

    private val networkFeeFlow = swapDetails.transform {
        emit(LoadingState.Loading())
        val details = it.getOrNull() ?: return@transform emit(LoadingState.Loaded(null))
        val fromAsset = fromAsset.value ?: return@transform emit(LoadingState.Loaded(null))
        val toAsset = toAsset.value ?: return@transform emit(LoadingState.Loaded(null))

        val fromCurrencyId = fromAsset.token.configuration.currencyId ?: return@transform emit(LoadingState.Loaded(null))
        val toCurrencyId = toAsset.token.configuration.currencyId ?: return@transform emit(LoadingState.Loaded(null))

        val market = selectedMarket.value

        val desiredAsset = when (desired) {
            WithDesired.INPUT -> fromAsset
            WithDesired.OUTPUT -> toAsset
            null -> return@transform emit(LoadingState.Loaded(null))
        }
        val feeInPlanks = polkaswapInteractor.estimateSwapFee(
            details.bestDexId,
            fromCurrencyId,
            toCurrencyId,
            desiredAsset.token.planksFromAmount(details.amount),
            market,
            requireNotNull(desired)
        )
        val fee = desiredAsset.token.amountFromPlanks(feeInPlanks)
        emit(LoadingState.Loaded(fee))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LoadingState.Loaded(null))

    private val networkFeeViewStateFlow = networkFeeFlow.map { amountLoading ->
        amountLoading.map {
            it?.let { feeAmount ->
                val feeAsset = swapDetails.value.getOrNull()?.feeAsset ?: return@let null
                SwapDetailsViewState.NetworkFee(
                    feeAsset.token.configuration.symbolToShow.uppercase(),
                    feeAmount.formatCryptoDetail(feeAsset.token.configuration.symbolToShow),
                    feeAsset.token.fiatAmount(feeAmount)?.formatFiat(feeAsset.token.fiatSymbol)
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LoadingState.Loaded(null))

    private val swapDetailsViewState = swapDetails.map {
        it.fold(
            onSuccess = { details ->
                details ?: return@map null
                val fromAsset = fromAsset.value ?: return@map null
                val toAsset = toAsset.value ?: return@map null
                fillSecondInputField(details.amount)
                detailsToViewState(resourceManager, amountInput.value, fromAsset, toAsset, details, desired ?: return@map null)
            },
            onFailure = { throwable ->
                val error = when (throwable) {
                    is PathUnavailableException -> ValidationException(
                        resourceManager.getString(R.string.common_error_general_title),
                        resourceManager.getString(R.string.polkaswap_path_unavailable_message)
                    )
                    is InsufficientLiquidityException -> ValidationException(
                        resourceManager.getString(R.string.common_error_general_title),
                        resourceManager.getString(R.string.polkaswap_insufficient_liqudity)
                    )
                    else -> throwable
                }
                showError(error)
                return@map null
            }
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private var assetResultJob: Job? = null

    private var transactionSettingsJob: Job? = null

    val state = combine(
        fromAmountInputViewState,
        toAmountInputViewState,
        selectedMarket,
        swapDetailsViewState,
        networkFeeViewStateFlow,
        isLoading,
        polkaswapInteractor.observeHasReadDisclaimer()
    ) { fromAmountInput, toAmountInput, selectedMarket, swapDetails, networkFeeState, isLoading, hasReadDisclaimer ->
        SwapTokensContentViewState(
            fromAmountInputViewState = fromAmountInput,
            toAmountInputViewState = toAmountInput,
            selectedMarket = selectedMarket,
            swapDetailsViewState = swapDetails,
            networkFeeViewState = networkFeeState,
            isLoading = isLoading,
            hasReadDisclaimer = hasReadDisclaimer
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SwapTokensContentViewState.default(resourceManager))

    init {
        initAssets()
        subscribeFromAmountInputViewState()
        subscribeToAmountInputViewState()

        transactionSettingsJob?.cancel()
        transactionSettingsJob = polkaswapRouter.observeResult<TransactionSettingsModel>(TransactionSettingsFragment.SETTINGS_MODEL_KEY)
            .onEach {
                selectedMarket.value = it.market
                slippageTolerance.value = it.slippageTolerance
            }
            .launchIn(viewModelScope)

        viewModelScope.launch {
            observeAvailableSources()
            initialFee = polkaswapInteractor.calcFakeFee()
        }
    }

    private fun initAssets() {
        viewModelScope.launch {
            polkaswapInteractor.setChainId(initFromChainId)
            fromAsset.value = initFromAssetId?.let { polkaswapInteractor.getAsset(it) }
            toAsset.value = initToAssetId?.let { polkaswapInteractor.getAsset(it) }
        }
    }

    private fun subscribeFromAmountInputViewState() {
        combine(initFromAmountFlow, enteredFromAmountFlow, fromAsset, isFromAmountFocused) { initAmount, enteredAmount, asset, isFromAmountFocused ->
            if (isFromAmountFocused) {
                desired = WithDesired.INPUT
            }
            fromAmountInputViewState.value = getAmountInputViewState(
                title = resourceManager.getString(R.string.polkaswap_from),
                amount = enteredAmount,
                asset = asset,
                isFocused = isFromAmountFocused,
                totalFormatRes = R.string.common_available_format,
                initialAmount = initAmount
            )
        }
            .launchIn(viewModelScope)
    }

    private fun subscribeToAmountInputViewState() {
        combine(initToAmountFlow, enteredToAmountFlow, toAsset, isToAmountFocused) { initialAmount, enteredAmount, asset, isToAmountFocused ->
            toAmountInputViewState.value = getAmountInputViewState(
                title = resourceManager.getString(R.string.polkaswap_to),
                amount = enteredAmount,
                asset = asset,
                isFocused = isToAmountFocused,
                initialAmount = initialAmount
            )
            if (isToAmountFocused) {
                desired = WithDesired.OUTPUT
            }
        }
            .launchIn(viewModelScope)
    }

    private fun observeAvailableSources() {
        combine(fromAsset.filterNotNull(), toAsset.filterNotNull(), dexes) { fromAsset, toAsset, dexes ->
            availableDexPathsFlow.value = null
            val fromCurrencyId = fromAsset.token.configuration.currencyId ?: return@combine
            val toCurrencyId = toAsset.token.configuration.currencyId ?: return@combine
            if (fromCurrencyId == toCurrencyId) return@combine

            val availableDexPaths = polkaswapInteractor.getAvailableDexesForPair(fromCurrencyId, toCurrencyId, dexes)
            if (availableDexPaths.isEmpty()) {
                showError(PathUnavailableException())
                return@combine
            }

            availableDexPathsFlow.value = availableDexPaths

            val availableMarkets = polkaswapInteractor.fetchAvailableSources(fromAsset, toAsset, availableDexPaths)
            if (selectedMarket.value !in availableMarkets) {
                selectedMarket.value = Market.SMART
            }
        }.launchIn(viewModelScope)
    }

    @Suppress("UNUSED_PARAMETER")
    private suspend fun getSwapDetails(
        amount: BigDecimal,
        selectedMarket: Market,
        slippageTolerance: Double,
        availableDexPaths: List<Int>?,
        reserves: Any
    ): Result<SwapDetails?> {
        val emptyResult = Result.success(null)
        fromAsset.value ?: return emptyResult
        toAsset.value ?: return emptyResult
        desired ?: return emptyResult
        if (availableDexPaths == null) return emptyResult
        if (availableDexPaths.isEmpty()) return emptyResult
        if (amount.compareTo(BigDecimal.ZERO) == 0) return emptyResult
        if (selectedMarket !in polkaswapInteractor.availableMarkets.values.flatten().toSet()) return emptyResult

        return polkaswapInteractor.calcDetails(availableDexPaths, fromAsset.value!!, toAsset.value!!, amount, desired!!, slippageTolerance, selectedMarket)
    }

    private fun fillSecondInputField(amount: BigDecimal) {
        when (desired) {
            WithDesired.INPUT -> {
                enteredToAmountFlow.value = amount
                initToAmountFlow.value = amount
            }
            WithDesired.OUTPUT -> {
                enteredFromAmountFlow.value = amount
                initFromAmountFlow.value = amount
            }
            else -> Unit
        }
    }

    private fun getAmountInputViewState(
        title: String,
        amount: BigDecimal,
        asset: Asset?,
        isFocused: Boolean,
        @StringRes totalFormatRes: Int = R.string.common_balance_format,
        initialAmount: BigDecimal?
    ): AmountInputViewState {
        if (asset == null) {
            return AmountInputViewState(
                tokenName = null,
                tokenImage = null,
                totalBalance = resourceManager.getString(totalFormatRes, "0"),
                fiatAmount = null,
                tokenAmount = amount,
                title = title,
                isFocused = isFocused,
                allowAssetChoose = true,
                initial = initialAmount
            )
        }

        val tokenBalance = asset.transferable.formatCrypto(asset.token.configuration.symbolToShow)
        val fiatAmount = amount.applyFiatRate(asset.token.fiatRate)?.formatFiat(asset.token.fiatSymbol)

        return AmountInputViewState(
            tokenName = asset.token.configuration.symbolToShow,
            tokenImage = asset.token.configuration.iconUrl,
            totalBalance = resourceManager.getString(totalFormatRes, tokenBalance),
            fiatAmount = fiatAmount,
            tokenAmount = amount,
            title = title,
            isFocused = isFocused,
            allowAssetChoose = true,
            precision = asset.token.configuration.precision,
            initial = initialAmount
        )
    }

    override fun onChangeTokensClick() {
        val newDesired = if (desired == WithDesired.INPUT) WithDesired.OUTPUT else WithDesired.INPUT
        desired = newDesired

        val fromAssetModel = fromAsset.value
        val toAssetModel = toAsset.value
        toAsset.value = null
        fromAsset.value = null
        fromAsset.value = toAssetModel
        toAsset.value = fromAssetModel

        val enteredRawFromAmountModel = enteredFromAmountFlow.value
        val enteredRawToAmountModel = enteredToAmountFlow.value

        enteredFromAmountFlow.value = enteredRawToAmountModel
        initFromAmountFlow.value = enteredRawToAmountModel

        enteredToAmountFlow.value = enteredRawFromAmountModel
        initToAmountFlow.value = enteredRawFromAmountModel
    }

    override fun onPreviewClick() {
        viewModelScope.launch {
            isLoading.value = true
            val bestDexId = (polkaswapInteractor.bestDexIdFlow.value as? LoadingState.Loaded)?.data

            val swapDetailsViewStateReady = swapDetailsViewState.value != null
            val swapDetailsReady = swapDetails.value.getOrNull() != null
            val bestDexIdReady = bestDexId != null
            val networkFeeReady = networkFeeViewStateFlow.value.dataOrNull() != null
            val isAllDataReady = swapDetailsViewStateReady && swapDetailsReady && bestDexIdReady && networkFeeReady

            if (!isAllDataReady) {
                isLoading.value = false
                showError(WaitForFeeCalculationException(resourceManager))
                return@launch
            }

            val swapDetails = requireNotNull(swapDetails.value.getOrNull())

            validate(swapDetails)?.let {
                isLoading.value = false
                showError(it)
                return@launch
            }

            val amountInPlanks: BigInteger
            val minMaxInPlanks: BigInteger?

            when (desired) {
                WithDesired.INPUT -> {
                    amountInPlanks = fromAsset.value?.token?.planksFromAmount(enteredFromAmountFlow.value).orZero()
                    minMaxInPlanks = toAsset.value?.token?.planksFromAmount(swapDetails.minMax.orZero())
                }
                WithDesired.OUTPUT -> {
                    amountInPlanks = toAsset.value?.token?.planksFromAmount(enteredToAmountFlow.value).orZero()
                    minMaxInPlanks = fromAsset.value?.token?.planksFromAmount(swapDetails.minMax.orZero())
                }
                else -> return@launch
            }

            val detailsParcelModel = SwapDetailsParcelModel(
                amountInPlanks,
                selectedMarket.value,
                requireNotNull(desired),
                requireNotNull(bestDexId),
                minMaxInPlanks,
                requireNotNull(networkFeeViewStateFlow.value.dataOrNull())
            )
            isLoading.value = false
            polkaswapRouter.openSwapPreviewDialog(requireNotNull(swapDetailsViewState.value), detailsParcelModel)
        }
    }

    private suspend fun validate(swapDetails: SwapDetails): Throwable? {
        val feeAsset = requireNotNull(polkaswapInteractor.getFeeAsset())
        val amountToSwap = enteredFromAmountFlow.value
        val toTokenAmount = enteredToAmountFlow.value
        val available = requireNotNull(fromAsset.value?.transferable)
        val networkFee = requireNotNull(networkFeeFlow.value.dataOrNull())
        val fee = networkFee + swapDetails.liquidityProviderFee
        val isFromFeeAsset = fromAsset.value?.token?.configuration?.id == feeAsset.token.configuration.id
        val isToFeeAsset = toAsset.value?.token?.configuration?.id == feeAsset.token.configuration.id

        return when {
            amountToSwap >= available -> {
                SpendInsufficientBalanceException(resourceManager)
            }
            isToFeeAsset.not() && feeAsset.transferable <= fee -> {
                UnableToPayFeeException(resourceManager)
            }
            isToFeeAsset && feeAsset.transferable <= fee && (toTokenAmount - fee) <= feeAsset.transferable -> {
                NotEnoughResultedAmountToPayFeeException(resourceManager)
            }
            isFromFeeAsset && available <= amountToSwap + fee -> {
                SpendInsufficientBalanceException(resourceManager)
            }
            else -> null
        }
    }

    override fun onBackClick() {
        polkaswapRouter.back()
    }

    override fun onFromAmountChange(amount: BigDecimal?) {
        enteredFromAmountFlow.value = amount.orZero()
    }

    override fun onToAmountChange(amount: BigDecimal?) {
        enteredToAmountFlow.value = amount.orZero()
    }

    override fun onMarketSettingsClick() {
        if (fromAsset.value == null || toAsset.value == null) {
            _showMarketsWarningEvent.value = Event(Unit)
            return
        }
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

    override fun onFromAmountFocusChange(isFocused: Boolean) {
        isFromAmountFocused.value = isFocused
    }

    override fun onToAmountFocusChange(isFocused: Boolean) {
        isToAmountFocused.value = isFocused
    }

    override fun minMaxToolTopClick() {
        val tooltip = when (desired) {
            WithDesired.INPUT -> R.string.polkaswap_minimum_received_title to R.string.polkaswap_minimum_received_info
            WithDesired.OUTPUT -> R.string.polkaswap_maximum_sold_title to R.string.polkaswap_maximum_sold_info
            null -> return
        }

        _showTooltipEvent.value = Event(resourceManager.getString(tooltip.first) to resourceManager.getString(tooltip.second))
    }

    override fun liquidityProviderTooltipClick() {
        _showTooltipEvent.value = Event(
            resourceManager.getString(R.string.polkaswap_liqudity_fee_title) to
                resourceManager.getString(R.string.polkaswap_liqudity_fee_info)
        )
    }

    override fun networkFeeTooltipClick() {
        _showTooltipEvent.value = Event(
            resourceManager.getString(R.string.network_fee) to
                resourceManager.getString(R.string.polkaswap_network_fee_info)
        )
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

    private fun observeResultFor(assetFlow: MutableStateFlow<Asset?>) {
        assetResultJob?.cancel()
        assetResultJob = polkaswapRouter.observeResult<String>(WalletRouter.KEY_ASSET_ID)
            .map(polkaswapInteractor::getAsset)
            .onEach {
                selectedMarket.value = Market.SMART
                assetFlow.value = it
            }
            .onEach { assetResultJob?.cancel() }
            .catch {
                showError(it)
            }
            .launchIn(viewModelScope)
    }

    fun marketAlertConfirmed() {
        when {
            fromAsset.value == null -> {
                onFromTokenSelect()
            }
            toAsset.value == null -> {
                onToTokenSelect()
            }
        }
    }

    var awaitNewFeeJob: Job? = null

    override fun onQuickAmountInput(value: Double) {
        viewModelScope.launch {
            desired ?: return@launch

            val transferable = fromAsset.value?.transferable.orZero()
            val details = swapDetails.value.getOrNull()

            val isFeeAsset = fromAsset.value?.token?.configuration?.id == polkaswapInteractor.getFeeAsset()?.token?.configuration?.id
            val amount = transferable.multiply(value.toBigDecimal())
            val networkFee = networkFeeFlow.value.dataOrNull() ?: initialFee
            val fee = if (details == null) {
                val liquidityProviderFee = amount.multiply(BigDecimal.valueOf(0.003))
                liquidityProviderFee + initialFee
            } else {
                networkFee + details.liquidityProviderFee
            }
            val result = if (isFeeAsset) amount.minus(fee) else amount
            val amountFrom = result.takeIf { it >= BigDecimal.ZERO }.orZero()

            enteredFromAmountFlow.value = amountFrom
            initFromAmountFlow.value = amountFrom

            if (isFeeAsset.not()) return@launch

            awaitNewFeeJob?.cancel()
            awaitNewFeeJob = viewModelScope.launch {
                combine(swapDetails, networkFeeFlow) { details, networkFee -> details to networkFee }.collectLatest { (detailsResult, networkFeeLoadingState) ->
                    val newDetails = detailsResult.getOrNull() ?: return@collectLatest
                    val newNetworkFee = networkFeeLoadingState.dataOrNull() ?: return@collectLatest

                    val newResult = amount.minus(newNetworkFee).minus(newDetails.liquidityProviderFee).takeIf { it >= BigDecimal.ZERO }.orZero()
                    enteredFromAmountFlow.value = newResult
                    awaitNewFeeJob?.cancel()
                }
            }
            awaitNewFeeJob?.start()
        }
    }

    override fun onDisclaimerClick() {
        polkaswapRouter.openPolkaswapDisclaimer()
    }
}
