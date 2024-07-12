package jp.co.soramitsu.polkaswap.impl.presentation.swap_tokens

import android.app.Activity
import androidx.annotation.StringRes
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import javax.inject.Inject
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.base.errors.ValidationException
import jp.co.soramitsu.common.compose.component.AmountInputViewState
import jp.co.soramitsu.common.data.network.runtime.binding.cast
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.presentation.dataOrNull
import jp.co.soramitsu.common.presentation.map
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.MAX_DECIMALS_8
import jp.co.soramitsu.common.utils.applyFiatRate
import jp.co.soramitsu.common.utils.combine
import jp.co.soramitsu.common.utils.flowOf
import jp.co.soramitsu.common.utils.formatCrypto
import jp.co.soramitsu.common.utils.formatCryptoDetail
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.common.utils.isZero
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.common.validation.NotEnoughResultedAmountToPayFeeException
import jp.co.soramitsu.common.validation.SpendInsufficientBalanceException
import jp.co.soramitsu.common.validation.UnableToPayFeeException
import jp.co.soramitsu.common.validation.WaitForFeeCalculationException
import jp.co.soramitsu.core.models.ChainId
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
import jp.co.soramitsu.wallet.impl.domain.interfaces.QuickInputsUseCase
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks
import jp.co.soramitsu.wallet.impl.domain.model.planksFromAmount
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

typealias TooltipEvent = Event<Pair<String, String>>

@HiltViewModel
class SwapTokensViewModel @Inject constructor(
    private val resourceManager: ResourceManager,
    private val polkaswapInteractor: PolkaswapInteractor,
    private val polkaswapRouter: PolkaswapRouter,
    private val quickInputsUseCase: QuickInputsUseCase,
    savedStateHandle: SavedStateHandle
) : BaseViewModel(), SwapTokensCallbacks {

    private val _showMarketsWarningEvent = MutableLiveData<Event<Unit>>()
    val showMarketsWarningEvent: LiveData<Event<Unit>> = _showMarketsWarningEvent

    private val _showTooltipEvent = MutableLiveData<TooltipEvent>()
    val showTooltipEvent: LiveData<TooltipEvent> = _showTooltipEvent

    private val enteredFromAmountFlow = MutableStateFlow(BigDecimal.ZERO)
    private val enteredToAmountFlow = MutableStateFlow(BigDecimal.ZERO)

    private val isFromAmountFocused = MutableStateFlow(false)
    private val isToAmountFocused = MutableStateFlow(false)

    private val initFromAssetId =
        savedStateHandle.get<String>(SwapTokensFragment.KEY_SELECTED_ASSET_FROM_ID)
    private val initToAssetId =
        savedStateHandle.get<String>(SwapTokensFragment.KEY_SELECTED_ASSET_TO_ID)
    private val initFromChainId =
        savedStateHandle.get<String>(SwapTokensFragment.KEY_SELECTED_CHAIN_ID)

    private val fromAmountInputViewState = MutableStateFlow(
        AmountInputViewState.defaultObj.copy(
            totalBalance = resourceManager.getString(R.string.common_available_format, "0")
        )
    )
    private val toAmountInputViewState = MutableStateFlow(
        AmountInputViewState.defaultObj.copy(
            totalBalance = resourceManager.getString(R.string.common_balance_format, "0"))
        )

    private var selectedMarket = MutableStateFlow(Market.SMART)
    private var slippageTolerance = MutableStateFlow(0.5)

    private val fromAssetIdFlow = MutableStateFlow(initFromAssetId)
    private val toAssetIdFlow = MutableStateFlow(initToAssetId)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val fromAssetFlow = fromAssetIdFlow.flatMapLatest {
        it?.let { polkaswapInteractor.assetFlow(it) } ?: flowOf { null }
    }.stateIn(this, SharingStarted.Eagerly, null)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val toAssetFlow = toAssetIdFlow.flatMapLatest {
        it?.let { polkaswapInteractor.assetFlow(it) } ?: flowOf { null }
    }.stateIn(this, SharingStarted.Eagerly, null)

    private var desired: WithDesired? = null

    private val dexes = flowOf { polkaswapInteractor.getAvailableDexes() }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        listOf()
    )

    private val isLoading = MutableStateFlow(false)
    private var initialFee = BigDecimal.ZERO
    private val availableDexPathsFlow: MutableStateFlow<List<Int>?> = MutableStateFlow(null)

    private val poolReservesFlow =
        combine(fromAssetFlow, toAssetFlow, selectedMarket) { fromAsset, toAsset, selectedMarket ->
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

    private val amountInput =
        combine(enteredFromAmountFlow, enteredToAmountFlow) { fromInput, toInput ->
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

    private val networkFeeFlow = combine(
        swapDetails,
        fromAssetFlow,
        toAssetFlow,
        transform = ::Triple
    ).transform { (swapDetails, fromAsset, toAsset) ->
        emit(LoadingState.Loading())
        val details = swapDetails.getOrNull()
            ?: return@transform emit(LoadingState.Loaded(null))

        val fromCurrencyId = fromAsset?.token?.configuration?.currencyId ?: return@transform emit(
            LoadingState.Loaded(null)
        )
        val toCurrencyId = toAsset?.token?.configuration?.currencyId ?: return@transform emit(
            LoadingState.Loaded(null)
        )

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
    }
        .catch { emit(LoadingState.Loaded(null)) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, LoadingState.Loaded(null))

    private val networkFeeViewStateFlow = networkFeeFlow.map { amountLoading ->
        amountLoading.map {
            it?.let { feeAmount ->
                val feeAsset =
                    swapDetails.value.getOrNull()?.feeAsset ?: return@let null
                SwapDetailsViewState.NetworkFee(
                    feeAsset.token.configuration.symbol.uppercase(),
                    feeAmount.formatCryptoDetail(feeAsset.token.configuration.symbol),
                    feeAsset.token.fiatAmount(feeAmount)?.formatFiat(feeAsset.token.fiatSymbol)
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LoadingState.Loaded(null))

    private val swapDetailsViewState = swapDetails.map {
        it.fold(
            onSuccess = { details ->
                details ?: return@map null
                val fromAsset = fromAssetFlow.value ?: return@map null
                val toAsset = toAssetFlow.value ?: return@map null
                fillSecondInputField(
                    details.amount.setScale(
                        MAX_DECIMALS_8,
                        RoundingMode.HALF_DOWN
                    )
                )
                detailsToViewState(
                    resourceManager,
                    amountInput.value,
                    fromAsset,
                    toAsset,
                    details,
                    desired ?: return@map null
                )
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

    private val quickInputsStateFlow = MutableStateFlow<Map<Double, BigDecimal>?>(null)

    val state = combine(
        fromAmountInputViewState,
        toAmountInputViewState,
        selectedMarket,
        swapDetailsViewState,
        networkFeeViewStateFlow,
        isLoading,
        polkaswapInteractor.observeHasReadDisclaimer(),
        isSoftKeyboardOpenFlow
    ) { fromAmountInput, toAmountInput, selectedMarket, swapDetails, networkFeeState, isLoading, hasReadDisclaimer, isSoftKeyboardOpen ->
        SwapTokensContentViewState(
            fromAmountInputViewState = fromAmountInput,
            toAmountInputViewState = toAmountInput,
            selectedMarket = selectedMarket,
            swapDetailsViewState = swapDetails,
            networkFeeViewState = networkFeeState,
            isLoading = isLoading,
            hasReadDisclaimer = hasReadDisclaimer,
            isSoftKeyboardOpen = isSoftKeyboardOpen
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        SwapTokensContentViewState.default(resourceManager)
    )

    init {
        polkaswapInteractor.setChainId(initFromChainId)

        subscribeFromAmountInputViewState()
        subscribeToAmountInputViewState()

        transactionSettingsJob?.cancel()
        transactionSettingsJob =
            polkaswapRouter.observeResult<TransactionSettingsModel>(TransactionSettingsFragment.SETTINGS_MODEL_KEY)
                .onEach {
                    selectedMarket.value = it.market
                    slippageTolerance.value = it.slippageTolerance
                }
                .launchIn(viewModelScope)

        viewModelScope.launch {
            observeAvailableSources()
            initialFee = polkaswapInteractor.calcFakeFee()
        }

        combine(
            fromAssetFlow.filterNotNull(),
            toAssetFlow.filterNotNull()
        ) { fromAsset, toAsset ->
            val quickInputs = quickInputsUseCase.calculatePolkaswapQuickInputs(
                fromAsset.token.configuration.id,
                toAsset.token.configuration.id
            )
            quickInputsStateFlow.update { quickInputs }
        }.launchIn(this)
    }

    private fun subscribeFromAmountInputViewState() {
        combine(
            enteredFromAmountFlow,
            fromAssetFlow,
            isFromAmountFocused
        ) { enteredAmount, asset, isFromAmountFocused ->
            if (isFromAmountFocused) {
                desired = WithDesired.INPUT
            }
            fromAmountInputViewState.value = getAmountInputViewState(
                title = resourceManager.getString(R.string.polkaswap_from),
                amount = enteredAmount,
                asset = asset,
                isFocused = isFromAmountFocused,
                totalFormatRes = R.string.common_available_format
            )
        }
            .launchIn(viewModelScope)
    }

    private fun subscribeToAmountInputViewState() {
        combine(
            enteredToAmountFlow,
            toAssetFlow,
            isToAmountFocused
        ) { enteredAmount, asset, isToAmountFocused ->
            toAmountInputViewState.value = getAmountInputViewState(
                title = resourceManager.getString(R.string.polkaswap_to),
                amount = enteredAmount,
                asset = asset,
                isFocused = isToAmountFocused
            )
            if (isToAmountFocused) {
                desired = WithDesired.OUTPUT
            }
        }
            .launchIn(viewModelScope)
    }

    private fun observeAvailableSources() {
        combine(
            fromAssetFlow.filterNotNull(),
            toAssetFlow.filterNotNull(),
            dexes
        ) { fromAsset, toAsset, dexes ->
            availableDexPathsFlow.value = null
            val fromCurrencyId = fromAsset.token.configuration.currencyId ?: return@combine
            val toCurrencyId = toAsset.token.configuration.currencyId ?: return@combine
            if (fromCurrencyId == toCurrencyId) return@combine

            val availableDexPaths =
                polkaswapInteractor.getAvailableDexesForPair(fromCurrencyId, toCurrencyId, dexes)
            if (availableDexPaths.isEmpty()) {
                showError(PathUnavailableException())
                return@combine
            }

            availableDexPathsFlow.value = availableDexPaths

            val availableMarkets =
                polkaswapInteractor.fetchAvailableSources(fromAsset, toAsset, availableDexPaths)
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
        fromAssetFlow.value ?: return emptyResult
        toAssetFlow.value ?: return emptyResult
        desired ?: return emptyResult
        if (availableDexPaths == null) return emptyResult
        if (availableDexPaths.isEmpty()) return emptyResult
        if (amount.isZero()) return emptyResult
        if (selectedMarket !in polkaswapInteractor.availableMarkets.values.flatten()
                .toSet()
        ) return emptyResult

        return polkaswapInteractor.calcDetails(
            availableDexPaths,
            fromAssetFlow.value!!,
            toAssetFlow.value!!,
            amount,
            desired!!,
            slippageTolerance,
            selectedMarket
        )
    }

    private fun fillSecondInputField(amount: BigDecimal) {
        when (desired) {
            WithDesired.INPUT -> {
                enteredToAmountFlow.value = amount
            }

            WithDesired.OUTPUT -> {
                enteredFromAmountFlow.value = amount
            }

            else -> Unit
        }
    }

    private fun getAmountInputViewState(
        title: String,
        amount: BigDecimal,
        asset: Asset?,
        isFocused: Boolean,
        @StringRes totalFormatRes: Int = R.string.common_balance_format
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
                allowAssetChoose = true
            )
        }

        val tokenBalance = asset.transferable.formatCrypto(asset.token.configuration.symbol)
        val fiatAmount =
            amount.applyFiatRate(asset.token.fiatRate)?.formatFiat(asset.token.fiatSymbol)

        return AmountInputViewState(
            tokenName = asset.token.configuration.symbol,
            tokenImage = asset.token.configuration.iconUrl,
            totalBalance = resourceManager.getString(totalFormatRes, tokenBalance),
            fiatAmount = fiatAmount,
            tokenAmount = amount,
            title = title,
            isFocused = isFocused,
            allowAssetChoose = true,
            precision = asset.token.configuration.precision
        )
    }

    override fun onChangeTokensClick() {
        val newDesired = if (desired == WithDesired.INPUT) WithDesired.OUTPUT else WithDesired.INPUT
        desired = newDesired

        val fromAssetId = fromAssetIdFlow.value
        val toAssetId = toAssetIdFlow.value
        toAssetIdFlow.value = null
        fromAssetIdFlow.value = null
        fromAssetIdFlow.value = toAssetId
        toAssetIdFlow.value = fromAssetId

        val enteredRawFromAmountModel = enteredFromAmountFlow.value
        val enteredRawToAmountModel = enteredToAmountFlow.value

        enteredFromAmountFlow.value = enteredRawToAmountModel

        enteredToAmountFlow.value = enteredRawFromAmountModel
    }

    override fun onPreviewClick() {
        viewModelScope.launch {
            isLoading.value = true
            val bestDexId = (polkaswapInteractor.bestDexIdFlow.value as? LoadingState.Loaded)?.data

            val swapDetailsViewStateReady = swapDetailsViewState.value != null
            val swapDetailsReady = swapDetails.value.getOrNull() != null
            val bestDexIdReady = bestDexId != null
            val networkFeeReady = networkFeeViewStateFlow.value.dataOrNull() != null
            val isAllDataReady =
                swapDetailsViewStateReady && swapDetailsReady && bestDexIdReady && networkFeeReady

            if (!isAllDataReady) {
                isLoading.value = false
                showError(WaitForFeeCalculationException(resourceManager))
                return@launch
            }

            val swapDetails = requireNotNull(swapDetails.value.getOrNull())

            validate()?.let {
                isLoading.value = false
                showError(it)
                return@launch
            }

            val amountInPlanks: BigInteger
            val minMaxInPlanks: BigInteger?

            when (desired) {
                WithDesired.INPUT -> {
                    amountInPlanks =
                        fromAssetFlow.value?.token?.planksFromAmount(enteredFromAmountFlow.value)
                            .orZero()
                    minMaxInPlanks =
                        toAssetFlow.value?.token?.planksFromAmount(swapDetails.minMax.orZero())
                }

                WithDesired.OUTPUT -> {
                    amountInPlanks =
                        toAssetFlow.value?.token?.planksFromAmount(enteredToAmountFlow.value)
                            .orZero()
                    minMaxInPlanks =
                        fromAssetFlow.value?.token?.planksFromAmount(swapDetails.minMax.orZero())
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
            polkaswapRouter.openSwapPreviewForResult(requireNotNull(swapDetailsViewState.value), detailsParcelModel)
                .onEach(::handleSwapPreviewResult)
                .launchIn(viewModelScope)
        }
    }

    private fun handleSwapPreviewResult(result: Int) {
        if (result == Activity.RESULT_OK) {
            resetFieldsState()
        } else {
            /* nothing */
        }
    }

    private fun resetFieldsState() {
        enteredFromAmountFlow.value = BigDecimal.ZERO
        enteredToAmountFlow.value = BigDecimal.ZERO
    }

    private suspend fun validate(): Throwable? {
        val feeAsset = requireNotNull(polkaswapInteractor.getFeeAsset())
        val amountToSwap = enteredFromAmountFlow.value
        val toTokenAmount = enteredToAmountFlow.value
        val available = requireNotNull(fromAssetFlow.value?.transferable)
        val networkFee = requireNotNull(networkFeeFlow.value.dataOrNull())
        val isFromFeeAsset =
            fromAssetFlow.value?.token?.configuration?.id == feeAsset.token.configuration.id
        val isToFeeAsset =
            toAssetFlow.value?.token?.configuration?.id == feeAsset.token.configuration.id

        return when {
            amountToSwap > available -> {
                SpendInsufficientBalanceException(resourceManager)
            }

            isToFeeAsset.not() && feeAsset.transferable <= networkFee -> {
                UnableToPayFeeException(resourceManager)
            }

            isToFeeAsset && feeAsset.transferable + toTokenAmount <= networkFee -> {
                NotEnoughResultedAmountToPayFeeException(resourceManager)
            }

            isFromFeeAsset && available <= amountToSwap + networkFee -> {
                SpendInsufficientBalanceException(resourceManager)
            }

            else -> null
        }
    }

    override fun onBackClick() {
        polkaswapRouter.back()
    }

    override fun onFromAmountChange(amount: BigDecimal) {
        enteredFromAmountFlow.value = amount
    }

    override fun onToAmountChange(amount: BigDecimal) {
        enteredToAmountFlow.value = amount
    }

    override fun onMarketSettingsClick() {
        if (fromAssetFlow.value == null || toAssetFlow.value == null) {
            _showMarketsWarningEvent.value = Event(Unit)
            return
        }
        val initialSettings =
            TransactionSettingsModel(selectedMarket.value, slippageTolerance.value)
        polkaswapRouter.openTransactionSettingsDialog(initialSettings)
    }

    override fun onFromTokenSelect() {
        openSelectAsset(
            selectedAssetIdFlow = fromAssetIdFlow,
            excludeAssetIdFlow = toAssetIdFlow
        )
    }

    override fun onToTokenSelect() {
        openSelectAsset(
            selectedAssetIdFlow = toAssetIdFlow,
            excludeAssetIdFlow = fromAssetIdFlow
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

        _showTooltipEvent.value =
            Event(resourceManager.getString(tooltip.first) to resourceManager.getString(tooltip.second))
    }

    override fun networkFeeTooltipClick() {
        _showTooltipEvent.value = Event(
            resourceManager.getString(R.string.common_network_fee) to
                    resourceManager.getString(R.string.polkaswap_network_fee_info)
        )
    }

    private fun openSelectAsset(
        selectedAssetIdFlow: MutableStateFlow<String?>,
        excludeAssetIdFlow: MutableStateFlow<String?>
    ) {
        observeResultFor(selectedAssetIdFlow)
        polkaswapRouter.openSelectAsset(
            chainId = polkaswapInteractor.polkaswapChainId,
            selectedAssetId = selectedAssetIdFlow.value,
            excludeAssetId = excludeAssetIdFlow.value
        )
    }

    private fun observeResultFor(assetIdFlow: MutableStateFlow<String?>) {
        assetResultJob?.cancel()
        assetResultJob = polkaswapRouter.observeResult<String>(WalletRouter.KEY_ASSET_ID)
            .map(polkaswapInteractor::getAsset)
            .onEach {
                selectedMarket.value = Market.SMART
                assetIdFlow.value = it?.token?.configuration?.id
            }
            .onEach { assetResultJob?.cancel() }
            .catch {
                showError(it)
            }
            .launchIn(viewModelScope)
    }

    fun marketAlertConfirmed() {
        when {
            fromAssetFlow.value == null -> {
                onFromTokenSelect()
            }

            toAssetFlow.value == null -> {
                onToTokenSelect()
            }
        }
    }

    override fun onQuickAmountInput(value: Double) {
        viewModelScope.launch {
            desired ?: return@launch

            val valuesMap = quickInputsStateFlow.first { !it.isNullOrEmpty() }.cast<Map<Double, BigDecimal>>()
            val amount = valuesMap[value] ?: return@launch

            enteredFromAmountFlow.value = amount.setScale(MAX_DECIMALS_8, RoundingMode.HALF_DOWN)
        }
    }

    override fun onDisclaimerClick() {
        polkaswapRouter.openPolkaswapDisclaimerFromSwapTokensFragment()
    }

    fun setSoftKeyboardOpen(isOpen: Boolean) {
        isSoftKeyboardOpenFlow.value = isOpen
    }

    override fun onPoolsClick() {
        polkaswapRouter.openPools(polkaswapInteractor.polkaswapChainId)
    }
}
