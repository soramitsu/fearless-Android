package jp.co.soramitsu.polkaswap.impl.presentation.swap_tokens

import androidx.compose.ui.focus.FocusState
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.math.BigInteger
import javax.inject.Inject
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.base.errors.ValidationException
import jp.co.soramitsu.common.compose.component.AmountInputViewState
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.applyFiatRate
import jp.co.soramitsu.common.utils.combine
import jp.co.soramitsu.common.utils.flowOf
import jp.co.soramitsu.common.utils.format
import jp.co.soramitsu.common.utils.formatAsCurrency
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.common.validation.ExistentialDepositCrossedException
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
import jp.co.soramitsu.polkaswap.api.presentation.models.TransactionSettingsModel
import jp.co.soramitsu.polkaswap.api.presentation.models.detailsToViewState
import jp.co.soramitsu.polkaswap.impl.presentation.transaction_settings.TransactionSettingsFragment
import jp.co.soramitsu.wallet.api.domain.ExistentialDepositUseCase
import jp.co.soramitsu.wallet.api.presentation.WalletRouter
import jp.co.soramitsu.wallet.api.presentation.formatters.formatTokenAmount
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks
import jp.co.soramitsu.wallet.impl.domain.model.planksFromAmount
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
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
    private val existentialDepositUseCase: ExistentialDepositUseCase,
    savedStateHandle: SavedStateHandle
) : BaseViewModel(), SwapTokensCallbacks {

    private val _showMarketsWarningEvent = MutableLiveData<Event<Unit>>()
    val showMarketsWarningEvent: LiveData<Event<Unit>> = _showMarketsWarningEvent

    private val enteredFromAmountFlow = MutableStateFlow("0")
    private val enteredToAmountFlow = MutableStateFlow("0")

    private val isFromAmountFocused = MutableStateFlow(false)
    private val isToAmountFocused = MutableStateFlow(false)

    private val initFromAssetId = savedStateHandle.get<String>(SwapTokensFragment.KEY_SELECTED_ASSET_ID)
    private val initFromChainId = savedStateHandle.get<String>(SwapTokensFragment.KEY_SELECTED_CHAIN_ID)

    private val fromAmountInputViewState = MutableStateFlow(AmountInputViewState.default(resourceManager))
    private val toAmountInputViewState = MutableStateFlow(AmountInputViewState.default(resourceManager))

    private var selectedMarket = MutableStateFlow(Market.SMART)
    private var slippageTolerance = MutableStateFlow(0.5)

    private val fromAsset = MutableStateFlow<Asset?>(null)
    private val toAsset = MutableStateFlow<Asset?>(null)

    private var desired: WithDesired? = null

    private val dexes = flowOf { polkaswapInteractor.getAvailableDexes() }.stateIn(viewModelScope, SharingStarted.Eagerly, listOf())

    private val isLoading = MutableStateFlow(false)

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
            WithDesired.INPUT -> fromInput.toBigDecimal()
            WithDesired.OUTPUT -> toInput.toBigDecimal()
            null -> BigDecimal.ZERO
        }
    }
        .catch { emit(BigDecimal.ZERO) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, BigDecimal.ZERO)

    private val swapDetails = combine(
        amountInput,
        fromAsset,
        toAsset,
        selectedMarket,
        slippageTolerance,
        dexes,
        poolReservesFlow,
        transform = ::getSwapDetails
    ).catch {
        emit(Result.failure(it))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, Result.success(null))

    private val swapDetailsViewState = swapDetails.map {
        it.fold(onSuccess = { details ->
            details ?: return@map null
            val fromAsset = fromAsset.value ?: return@map null
            val toAsset = toAsset.value ?: return@map null
            fillSecondInputField(details.amount)
            detailsToViewState(resourceManager, amountInput.value, fromAsset, toAsset, details, desired ?: return@map null)
        }, onFailure = { throwable ->
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
        })
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private var assetResultJob: Job? = null

    private var transactionSettingsJob: Job? = null

    val state = combine(
        fromAmountInputViewState,
        toAmountInputViewState,
        selectedMarket,
        swapDetailsViewState,
        isLoading
    ) { fromAmountInput, toAmountInput, selectedMarket, swapDetails, isLoading ->
        SwapTokensContentViewState(
            fromAmountInputViewState = fromAmountInput,
            toAmountInputViewState = toAmountInput,
            selectedMarket = selectedMarket,
            swapDetailsViewState = swapDetails,
            isLoading = isLoading
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

        viewModelScope.launch {
            combine(fromAsset.filterNotNull(), toAsset.filterNotNull(), dexes) { fromAsset, toAsset, dexes ->
                polkaswapInteractor.fetchAvailableSources(fromAsset, toAsset, dexes)
            }.launchIn(viewModelScope)
        }
    }

    private fun observeResultFor(assetFlow: MutableStateFlow<Asset?>) {
        assetResultJob?.cancel()
        assetResultJob = polkaswapRouter.observeResult<String>(WalletRouter.KEY_ASSET_ID)
            .map(polkaswapInteractor::getAsset)
            .onEach { assetFlow.value = it }
            .onEach { assetResultJob?.cancel() }
            .catch {
                showError(it)
            }
            .launchIn(viewModelScope)
    }

    @Suppress("UNUSED_PARAMETER")
    private suspend fun getSwapDetails(
        amount: BigDecimal,
        fromAsset: Asset?,
        toAsset: Asset?,
        selectedMarket: Market,
        slippageTolerance: Double,
        dexes: List<BigInteger>,
        reserves: Any
    ): Result<SwapDetails?> {
        val emptyResult = Result.success(null)
        fromAsset ?: return emptyResult
        toAsset ?: return emptyResult
        desired ?: return emptyResult
        if (dexes.isEmpty()) return emptyResult
        if (amount.compareTo(BigDecimal.ZERO) == 0) return emptyResult

        return polkaswapInteractor.calcDetails(dexes, fromAsset, toAsset, amount, desired!!, slippageTolerance, selectedMarket)
    }

    private fun fillSecondInputField(amount: BigDecimal) {
        when (desired) {
            WithDesired.INPUT -> {
                enteredToAmountFlow.value = amount.format()
            }
            WithDesired.OUTPUT -> {
                enteredFromAmountFlow.value = amount.format()
            }
            else -> Unit
        }
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
                polkaswapInteractor.setChainId(initFromChainId)
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
        viewModelScope.launch {
            isLoading.value = true
            val swapDetailsViewState = swapDetailsViewState.value ?: run {
                isLoading.value = false
                showError(WaitForFeeCalculationException(resourceManager))
                return@launch
            }
            val swapDetails = swapDetails.value.getOrNull() ?: run {
                isLoading.value = false
                showError(WaitForFeeCalculationException(resourceManager))
                return@launch
            }
            validate(swapDetails)?.let {
                isLoading.value = false
                showError(it)
                return@launch
            }
            var amountInPlanks: BigInteger = BigInteger.ZERO
            var minMaxInPlanks: BigInteger? = null
            val minMax = swapDetails.minMax
            when (desired) {
                WithDesired.INPUT -> {
                    amountInPlanks = fromAsset.value?.token?.planksFromAmount(enteredFromAmountFlow.value.toBigDecimal()).orZero()
                    minMaxInPlanks = toAsset.value?.token?.planksFromAmount(minMax.orZero())
                }
                WithDesired.OUTPUT -> {
                    amountInPlanks = toAsset.value?.token?.planksFromAmount(enteredToAmountFlow.value.toBigDecimal()).orZero()
                    minMaxInPlanks = fromAsset.value?.token?.planksFromAmount(minMax.orZero())
                }
                else -> Unit
            }

            val detailsParcelModel = SwapDetailsParcelModel(
                amountInPlanks,
                selectedMarket.value,
                requireNotNull(desired),
                requireNotNull((polkaswapInteractor.bestDexIdFlow.value as? LoadingState.Loaded)?.data),
                minMaxInPlanks
            )
            isLoading.value = false
            polkaswapRouter.openSwapPreviewDialog(swapDetailsViewState, detailsParcelModel)
        }
    }

    private suspend fun validate(swapDetails: SwapDetails): Throwable? {
        val feeAsset = requireNotNull(polkaswapInteractor.getFeeAsset())
        val ed = existentialDepositUseCase(feeAsset.token.configuration).let { feeAsset.token.configuration.amountFromPlanks(it) }
        val amountToSwap = enteredFromAmountFlow.value.toBigDecimal()
        val available = requireNotNull(fromAsset.value?.transferable)
        val fee = swapDetails.networkFee + swapDetails.liquidityProviderFee
        return when {
            amountToSwap >= available -> {
                SpendInsufficientBalanceException(resourceManager)
            }
            fromAsset.value?.token?.configuration?.id == feeAsset.token.configuration.id && available <= amountToSwap + fee -> {
                SpendInsufficientBalanceException(resourceManager)
            }
            feeAsset.transferable <= fee -> {
                UnableToPayFeeException(resourceManager)
            }
            fromAsset.value?.token?.configuration?.id == feeAsset.token.configuration.id && (available - amountToSwap - fee) <= ed -> {
                ExistentialDepositCrossedException(resourceManager)
            }

            (feeAsset.transferable - fee) <= ed -> {
                ExistentialDepositCrossedException(resourceManager)
            }
            else -> null
        }
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
}
