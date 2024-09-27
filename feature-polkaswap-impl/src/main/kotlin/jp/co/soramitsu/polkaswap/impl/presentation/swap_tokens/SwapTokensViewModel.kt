package jp.co.soramitsu.polkaswap.impl.presentation.swap_tokens

import android.app.Activity
import androidx.annotation.StringRes
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.base.errors.ValidationException
import jp.co.soramitsu.common.compose.component.AmountInputViewState
import jp.co.soramitsu.common.compose.component.FeeInfoViewState
import jp.co.soramitsu.common.compose.component.GradientIconState
import jp.co.soramitsu.common.data.network.okx.OkxDexRouter
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
import jp.co.soramitsu.feature_polkaswap_impl.R
import jp.co.soramitsu.polkaswap.api.domain.InsufficientLiquidityException
import jp.co.soramitsu.polkaswap.api.domain.PathUnavailableException
import jp.co.soramitsu.polkaswap.api.domain.PolkaswapInteractor
import jp.co.soramitsu.polkaswap.api.domain.models.OkxCrossChainSwapDetails
import jp.co.soramitsu.polkaswap.api.domain.models.OkxSwapDetails
import jp.co.soramitsu.polkaswap.api.domain.models.PolkaswapSwapDetails
import jp.co.soramitsu.polkaswap.api.domain.models.SwapDetails
import jp.co.soramitsu.polkaswap.api.models.Market
import jp.co.soramitsu.polkaswap.api.models.WithDesired
import jp.co.soramitsu.polkaswap.api.presentation.PolkaswapRouter
import jp.co.soramitsu.polkaswap.api.presentation.models.SwapDetailsParcelModel
import jp.co.soramitsu.polkaswap.api.presentation.models.SwapDetailsViewState
import jp.co.soramitsu.polkaswap.api.presentation.models.TransactionSettingsModel
import jp.co.soramitsu.polkaswap.impl.presentation.transaction_settings.TransactionSettingsFragment
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.wallet.api.presentation.WalletRouter
import jp.co.soramitsu.wallet.api.presentation.formatters.formatCryptoDetailFromPlanks
import jp.co.soramitsu.wallet.impl.domain.interfaces.QuickInputsUseCase
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks
import jp.co.soramitsu.wallet.impl.domain.model.planksFromAmount
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

typealias TooltipEvent = Event<Pair<String, String>>

@HiltViewModel(assistedFactory = SwapTokensViewModel.SwapViewModelFactory::class)
class SwapTokensViewModel @AssistedInject constructor(
    private val resourceManager: ResourceManager,
    private val polkaswapInteractor: PolkaswapInteractor,
    private val polkaswapRouter: PolkaswapRouter,
    private val walletInteractor: WalletInteractor,
    private val quickInputsUseCase: QuickInputsUseCase,
    @Assisted("chainId") val chainId: ChainId?,
    @Assisted("assetFromId") val assetFromId: String?,
    @Assisted("assetToId") val assetToId: String?
) : BaseViewModel(), SwapTokensCallbacks {

    @AssistedFactory
    fun interface SwapViewModelFactory {
        fun create(
            @Assisted("chainId") chainId: String?,
            @Assisted("assetFromId") assetIdFrom: String?,
            @Assisted("assetToId") assetIdTo: String?
        ): SwapTokensViewModel
    }

    data class AssetPayload(
        val chainId: String,
        val assetId: String
    )

    enum class SwapType {
        POLKASWAP, OKX_SWAP, OKX_CROSS_CHAIN
    }

    private val swapTypeDefault: SwapType
        get() = if (chainId == null || chainId == polkaswapInteractor.polkaswapChainId) {
            SwapType.POLKASWAP
        } else {
            SwapType.OKX_SWAP
        }


    private val _showMarketsWarningEvent = MutableLiveData<Event<Unit>>()
    val showMarketsWarningEvent: LiveData<Event<Unit>> = _showMarketsWarningEvent

    private val _showTooltipEvent = MutableLiveData<TooltipEvent>()
    val showTooltipEvent: LiveData<TooltipEvent> = _showTooltipEvent

    private val enteredFromAmountFlow = MutableStateFlow(BigDecimal.ZERO)
    private val enteredToAmountFlow = MutableStateFlow(BigDecimal.ZERO)

    private val isFromAmountFocused = MutableStateFlow(false)
    private val isToAmountFocused = MutableStateFlow(false)

    private val fromAmountInputViewState = MutableStateFlow(
        AmountInputViewState.defaultObj.copy(
            totalBalance = resourceManager.getString(R.string.common_available_format, "0")
        )
    )
    private val toAmountInputViewState = MutableStateFlow(
        AmountInputViewState.defaultObj.copy(
            totalBalance = resourceManager.getString(R.string.common_balance_format, "0")
        )
    )

    private var selectedMarket = MutableStateFlow(Market.SMART)
    private var slippageTolerance = MutableStateFlow(0.5)

    private val fromPayloadFlow = MutableStateFlow(
        if (chainId != null && assetFromId != null) {
            AssetPayload(chainId, assetFromId)
        } else null
    )
    private val toPayloadFlow = MutableStateFlow(
        if (chainId != null && assetToId != null) {
            AssetPayload(chainId, assetToId)
        } else null
    )

    private val swapTypeFlow = combine(fromPayloadFlow, toPayloadFlow) { from, to ->
        when {
            from?.chainId == null -> null
            from.chainId == polkaswapInteractor.polkaswapChainId -> SwapType.POLKASWAP
            to?.chainId != null && to.chainId != from.chainId -> SwapType.OKX_CROSS_CHAIN
//            to?.chainId != null && to.chainId == from.chainId -> SwapType.OKX_SWAP
            else -> SwapType.OKX_SWAP
        }
    }.filterNotNull()
        .stateIn(this, SharingStarted.Eagerly, swapTypeDefault)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val fromAssetFlow: StateFlow<Asset?> = fromPayloadFlow
        .flatMapLatest {
            it?.let { walletInteractor.assetFlow(it.chainId, it.assetId) } ?: flowOf { null }
        }
        .stateIn(this, SharingStarted.Eagerly, null)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val toAssetFlow = toPayloadFlow
        .flatMapLatest {
            it?.let { walletInteractor.assetFlow(it.chainId, it.assetId) } ?: flowOf { null }
        }
        .stateIn(this, SharingStarted.Eagerly, null)

    private var desired: WithDesired? = null

    private val dexes = flowOf { polkaswapInteractor.getAvailableDexes() }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        listOf()
    )

    private val isLoading = MutableStateFlow(false)
    private val isShowBannerLiquidity = MutableStateFlow(true)
    private var initialFee = BigDecimal.ZERO
    private val availableDexPathsFlow: MutableStateFlow<List<Int>?> = MutableStateFlow(null)

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
        swapTypeFlow,
        amountInput,
        selectedMarket,
        slippageTolerance,
        availableDexPathsFlow,
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

        when (details) {
            is PolkaswapSwapDetails -> {
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
                println("!!! GOT POLKASWAP SWAP FEE: $fee")
                emit(LoadingState.Loaded(fee))
            }

            is OkxSwapDetails -> {
                val feeInPlanks = details.tx.gas.toBigInteger() * (details.tx.maxPriorityFeePerGas.toBigInteger() + details.tx.gasPrice.toBigInteger())
                val fee = details.feeAsset.token.amountFromPlanks(feeInPlanks)
                println("!!! GOT OkxSwapDetails SWAP FEE: $fee")
                emit(LoadingState.Loaded(fee))
            }

            is OkxCrossChainSwapDetails -> {
                val feeInPlanks = details.tx.gasLimit.toBigInteger() * (details.tx.maxPriorityFeePerGas.toBigInteger() + details.tx.gasPrice.toBigInteger())
                val fee = details.feeAsset.token.amountFromPlanks(feeInPlanks)
                println("!!! GOT CROSS_CHAIN SWAP FEE: $fee")
                emit(LoadingState.Loaded(fee))
            }

            else -> {
                LoadingState.Loaded(null)
            }
        }
    }
        .catch { emit(LoadingState.Loaded(null)) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, LoadingState.Loaded(null))

    private val networkFeeViewStateFlow = networkFeeFlow.map { amountLoading ->
        amountLoading.map {
            it?.let { feeAmount ->
                val feeAsset = when (val swapDetails = swapDetails.value.getOrNull()) {
                    is PolkaswapSwapDetails -> swapDetails.feeAsset
                    is OkxSwapDetails -> swapDetails.feeAsset
                    is OkxCrossChainSwapDetails -> swapDetails.feeAsset
                    else -> return@let null
                }
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

                when (details) {
                    is PolkaswapSwapDetails -> {
                        fillSecondInputField(
                            details.amount.setScale(
                                MAX_DECIMALS_8,
                                RoundingMode.HALF_DOWN
                            )
                        )
                        details.detailsToViewState(
                            resourceManager,
                            amountInput.value,
                            fromAsset,
                            toAsset,
                            desired ?: return@map null
                        )
                    }

                    is OkxCrossChainSwapDetails -> {
                        enteredFromAmountFlow.value = fromAsset.token.amountFromPlanks(details.fromTokenAmount.toBigInteger())
                        details.detailsToViewState(
                            resourceManager,
                            amountInput.value,
                            fromAsset,
                            toAsset,
                        )
                    }

                    is OkxSwapDetails -> {
                        enteredFromAmountFlow.value = fromAsset.token.amountFromPlanks(details.fromTokenAmount.toBigInteger())
                        details.detailsToViewState(
                            resourceManager,
                            amountInput.value,
                            fromAsset,
                            toAsset,
                        )
                    }


                    else -> return@map null
                }
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
        isShowBannerLiquidity,
        polkaswapInteractor.observeHasReadDisclaimer(),
        isSoftKeyboardOpenFlow,
        swapTypeFlow
    ) { fromAmountInput, toAmountInput, selectedMarket, swapDetails, networkFeeState, isLoading, isShowBannerLiquidity, hasReadDisclaimer, isSoftKeyboardOpen, swapType ->
        val detailInfosViewStates = swapDetails?.toDetailItems(networkFeeState)
        println("!!! state detailInfosViewStates.size = ${detailInfosViewStates?.size}")

        SwapTokensContentViewState(
            fromAmountInputViewState = fromAmountInput,
            toAmountInputViewState = toAmountInput,
            selectedMarket = selectedMarket,
            detailInfosViewStates = detailInfosViewStates,
            networkFeeViewState = networkFeeState,
            isLoading = isLoading,
            showLiquidityBanner = isShowBannerLiquidity,
            hasReadDisclaimer = hasReadDisclaimer,
            isSoftKeyboardOpen = isSoftKeyboardOpen,
            swapType = swapType
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        SwapTokensContentViewState.default(resourceManager)
    )

    init {
        polkaswapInteractor.setChainId(chainId)

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

    private suspend fun getSwapDetails(
        swapType: SwapType,
        amount: BigDecimal,
        selectedMarket: Market,
        slippageTolerance: Double,
        availableDexPaths: List<Int>?
    ): Result<SwapDetails?> {
        val emptyResult = Result.success(null)
        println("!!! getSwapDetails swapType = $swapType")
        val fromAsset = fromAssetFlow.value?.token?.configuration ?: return emptyResult
        val toAsset = toAssetFlow.value?.token?.configuration ?: return emptyResult

        when (swapType) {
            SwapType.POLKASWAP -> {
                val desiredValue = desired ?: return emptyResult
                if (availableDexPaths == null) return emptyResult
                if (availableDexPaths.isEmpty()) return emptyResult
                if (amount.isZero()) return emptyResult
                if (selectedMarket !in polkaswapInteractor.availableMarkets.values.flatten().toSet()) return emptyResult

                return polkaswapInteractor.calcDetails(
                    availableDexPaths,
                    fromAsset,
                    toAsset,
                    amount,
                    desiredValue,
                    slippageTolerance,
                    selectedMarket
                )
            }

            SwapType.OKX_CROSS_CHAIN -> {
                val amountInPlanks = fromAsset.planksFromAmount(amount).orZero()

                val userAddress = walletInteractor.getChainAddressForSelectedMetaAccount(fromAsset.chainId) ?: return emptyResult
                return polkaswapInteractor.crossChainBuildTx(
                    fromAsset = fromAsset,
                    toAsset = toAsset,
                    amount = amountInPlanks.toString(),
                    slippage = slippageTolerance.toString(),
                    userWalletAddress = userAddress
                )
            }

            SwapType.OKX_SWAP -> {
                val amountInPlanks = fromAsset.planksFromAmount(amount).orZero()

                val userAddress = walletInteractor.getChainAddressForSelectedMetaAccount(fromAsset.chainId) ?: return emptyResult
                return polkaswapInteractor.getOkxSwap(
                    fromAsset = fromAsset,
                    toAsset = toAsset,
                    amount = amountInPlanks.toString(),
                    slippage = slippageTolerance.toString(),
                    userWalletAddress = userAddress
                )
            }
        }
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
            chainName = asset.token.configuration.chainName,
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

        val fromPayload = fromPayloadFlow.value
//        val toPayload = toPayloadFlow.value
//        fromPayloadFlow.value = toPayload
        fromPayloadFlow.value = toPayloadFlow.value
        toPayloadFlow.value = fromPayload

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

            val swapDetails = requireNotNull(swapDetails.value.getOrNull() as? PolkaswapSwapDetails)

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
                        fromAssetFlow.value?.token?.planksFromAmount(enteredFromAmountFlow.value).orZero()
                    minMaxInPlanks =
                        toAssetFlow.value?.token?.planksFromAmount(swapDetails.minMax.orZero())
                }

                WithDesired.OUTPUT -> {
                    amountInPlanks =
                        toAssetFlow.value?.token?.planksFromAmount(enteredToAmountFlow.value).orZero()
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
        val initialSettings = TransactionSettingsModel(selectedMarket.value, slippageTolerance.value)
        polkaswapRouter.openTransactionSettingsDialog(initialSettings)
    }

    override fun onFromTokenSelect() {
        launch {
            val okxChainsIds = walletInteractor.getOkxChainsIds()
            val swapQuickChainIds = okxChainsIds.plus(polkaswapInteractor.polkaswapChainId)

            observeResultFor(fromPayloadFlow)
            polkaswapRouter.openSelectAsset(
                chainId = fromPayloadFlow.value?.chainId,
                selectedAssetId = fromPayloadFlow.value?.assetId,
                excludeAssetId = toPayloadFlow.value?.assetId,
                swapQuickChains = swapQuickChainIds
            )
        }
    }

    override fun onToTokenSelect() {
        launch {
            val swapQuickChainIds = when (swapTypeFlow.value) {
                SwapType.POLKASWAP -> {
                    listOf(polkaswapInteractor.polkaswapChainId)
                }

                SwapType.OKX_SWAP,
                SwapType.OKX_CROSS_CHAIN -> {
                    walletInteractor.getOkxChainsIds()
                }
            }

            observeResultFor(toPayloadFlow)
            val toChainId = toPayloadFlow.value?.chainId?.takeIf { it in swapQuickChainIds }
            polkaswapRouter.openSelectAsset(
                chainId = toChainId,
                selectedAssetId = toPayloadFlow.value?.assetId,
                excludeAssetId = fromPayloadFlow.value?.assetId,
                swapQuickChains = swapQuickChainIds
            )
        }
    }

    override fun onFromAmountFocusChange(isFocused: Boolean) {
        isFromAmountFocused.value = isFocused
    }

    override fun onToAmountFocusChange(isFocused: Boolean) {
        isToAmountFocused.value = isFocused
    }

    override fun minMaxToolTipClick() {
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

    override fun minReceivedTooltipClick() {
        _showTooltipEvent.value = Event(
            resourceManager.getString(R.string.common_min_received) to
                    resourceManager.getString(R.string.polkaswap_minimum_received_info)
        )
    }

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private fun observeResultFor(assetPayloadFlow: MutableStateFlow<AssetPayload?>) {
        assetResultJob?.cancel()
        assetResultJob = combine(
            polkaswapRouter.observeResult<String>(WalletRouter.KEY_CHAIN_ID),
            polkaswapRouter.observeResult<String>(WalletRouter.KEY_ASSET_ID)
        ) { chainId, assetId ->
            chainId to assetId
        }
            .debounce(200)
//            .flatMapLatest { (chainId, assetId) ->
//                kotlin.runCatching {
//                    walletInteractor.assetFlow(chainId, assetId)
//                }.onFailure {
//                    println("!!! ALARMA!!! chainId = $chainId, assetId = $assetId")
//                    it.printStackTrace()
//                }.getOrNull() ?: flowOf { null }
//            }
//            .mapNotNull { it }
            .onEach { (chainId, assetId) ->
                if (chainId == polkaswapInteractor.polkaswapChainId) {
                    selectedMarket.value = Market.SMART
                }
                assetPayloadFlow.value = AssetPayload(chainId, assetId)

                if (assetPayloadFlow == fromPayloadFlow) {
                    clearUnsupportedToAsset()
                }
            }
            .onEach { assetResultJob?.cancel() }
            .catch {
                showError(it)
            }
            .launchIn(viewModelScope)
    }

    private fun clearUnsupportedToAsset() {
        toPayloadFlow.value ?: return
        val fromChainId = fromPayloadFlow.value?.chainId

        if (fromChainId == polkaswapInteractor.polkaswapChainId) {
            if (toPayloadFlow.value?.chainId != fromChainId) {
                toPayloadFlow.value = null
            }
        } else {
            if (toPayloadFlow.value?.chainId == polkaswapInteractor.polkaswapChainId) {
                toPayloadFlow.value = null
            }
        }
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
        polkaswapRouter.openPools()
    }

    override fun onLiquidityBannerClose() {
        isShowBannerLiquidity.value = false
    }

    fun OkxSwapDetails.detailsToViewState(
        resourceManager: ResourceManager,
        amount: BigDecimal,
        fromAsset: Asset,
        toAsset: Asset,
    ): SwapDetailsViewState {
        val details = this
        val fromAmount = amount.formatCryptoDetail(fromAsset.token.configuration.symbol)
        val toAmount = details.toTokenAmount.toBigInteger().formatCryptoDetailFromPlanks(toAsset.token.configuration)
        val minMaxTitle = resourceManager.getString(jp.co.soramitsu.feature_polkaswap_api.R.string.common_min_received)
        val minMaxAmount = details.minmumReceive.toBigInteger().formatCryptoDetailFromPlanks(toAsset.token.configuration)
        val minMaxFiat = toAsset.token.fiatAmount(toAsset.token.configuration.amountFromPlanks(details.minmumReceive.toBigInteger()))?.formatFiat(toAsset.token.fiatSymbol)

        val tokenFromId = requireNotNull(fromAsset.token.configuration.currencyId)
        val tokenToId = requireNotNull(toAsset.token.configuration.currencyId)

        return SwapDetailsViewState(
            fromTokenId = tokenFromId,
            toTokenId = tokenToId,
            fromTokenName = fromAsset.token.configuration.symbol.uppercase(),
            toTokenName = toAsset.token.configuration.symbol.uppercase(),
            fromTokenImage = GradientIconState.Remote(fromAsset.token.configuration.iconUrl, fromAsset.token.configuration.color),
            toTokenImage = GradientIconState.Remote(toAsset.token.configuration.iconUrl, toAsset.token.configuration.color),
            fromTokenAmount = fromAmount,
            toTokenAmount = toAmount,
            minmaxTitle = minMaxTitle,
            toTokenMinReceived = minMaxAmount,
            toFiatMinReceived = minMaxFiat.orEmpty(),
            fromTokenOnToToken = details.fromTokenOnToToken.formatCryptoDetail(),
            toTokenOnFromToken = details.toTokenOnFromToken.formatCryptoDetail(),
            route = details.routerList.joinToString(transform = OkxDexRouter::router, separator = " > "),

            fromChainId = fromAsset.token.configuration.chainId,
            toChainId = toAsset.token.configuration.chainId,
            fromChainIdImage = fromAsset.token.configuration.chainIcon,
            toChainIdImage = toAsset.token.configuration.chainIcon
        )
    }

    fun OkxCrossChainSwapDetails.detailsToViewState(
        resourceManager: ResourceManager,
        amount: BigDecimal,
        fromAsset: Asset,
        toAsset: Asset,
    ): SwapDetailsViewState {
        val details = this
        val fromAmount = amount.formatCryptoDetail(fromAsset.token.configuration.symbol)
        val toAmount = details.toTokenAmount.toBigInteger().formatCryptoDetailFromPlanks(toAsset.token.configuration)
        val minMaxTitle = resourceManager.getString(jp.co.soramitsu.feature_polkaswap_api.R.string.common_min_received)
        val minMaxAmount = details.minmumReceive.toBigInteger().formatCryptoDetailFromPlanks(toAsset.token.configuration)
        val minMaxFiat = toAsset.token.fiatAmount(toAsset.token.configuration.amountFromPlanks(details.minmumReceive.toBigInteger()))?.formatFiat(toAsset.token.fiatSymbol)

        val tokenFromId = requireNotNull(fromAsset.token.configuration.currencyId)
        val tokenToId = requireNotNull(toAsset.token.configuration.currencyId)

        return SwapDetailsViewState(
            fromTokenId = tokenFromId,
            toTokenId = tokenToId,
            fromTokenName = fromAsset.token.configuration.symbol.uppercase(),
            toTokenName = toAsset.token.configuration.symbol.uppercase(),
            fromTokenImage = GradientIconState.Remote(fromAsset.token.configuration.iconUrl, fromAsset.token.configuration.color),
            toTokenImage = GradientIconState.Remote(toAsset.token.configuration.iconUrl, toAsset.token.configuration.color),
            fromTokenAmount = fromAmount,
            toTokenAmount = toAmount,
            minmaxTitle = minMaxTitle,
            toTokenMinReceived = minMaxAmount,
            toFiatMinReceived = minMaxFiat.orEmpty(),
            fromTokenOnToToken = details.fromTokenOnToToken.formatCryptoDetail(),
            toTokenOnFromToken = details.toTokenOnFromToken.formatCryptoDetail(),
            route = details.router.bridgeName,

            fromChainId = fromAsset.token.configuration.chainId,
            toChainId = toAsset.token.configuration.chainId,
            fromChainIdImage = fromAsset.token.configuration.chainIcon,
            toChainIdImage = toAsset.token.configuration.chainIcon
        )
    }

    fun PolkaswapSwapDetails.detailsToViewState(
        resourceManager: ResourceManager,
        amount: BigDecimal,
        fromAsset: Asset,
        toAsset: Asset,
        desired: WithDesired,
    ): SwapDetailsViewState {
        val details = this
        var fromAmount = ""
        var toAmount = ""
        var minMaxTitle: String? = null
        var minMaxAmount: String? = null
        var minMaxFiat: String? = null

        when (desired) {
            WithDesired.INPUT -> {
                fromAmount = amount.formatCryptoDetail(fromAsset.token.configuration.symbol)
                toAmount = details.amount.formatCryptoDetail(toAsset.token.configuration.symbol)

                minMaxTitle = resourceManager.getString(jp.co.soramitsu.feature_polkaswap_api.R.string.common_min_received)
                minMaxAmount = details.minMax.formatCryptoDetail(toAsset.token.configuration.symbol)
                minMaxFiat = toAsset.token.fiatAmount(details.minMax)?.formatFiat(toAsset.token.fiatSymbol)
            }

            WithDesired.OUTPUT -> {
                fromAmount = details.amount.formatCryptoDetail(fromAsset.token.configuration.symbol)
                toAmount = amount.formatCryptoDetail(toAsset.token.configuration.symbol)

                minMaxTitle = resourceManager.getString(jp.co.soramitsu.feature_polkaswap_api.R.string.polkaswap_maximum_sold)
                minMaxAmount = details.minMax.formatCryptoDetail(fromAsset.token.configuration.symbol)
                minMaxFiat = fromAsset.token.fiatAmount(details.minMax)?.formatFiat(fromAsset.token.fiatSymbol)
            }
        }
        val tokenFromId = requireNotNull(fromAsset.token.configuration.currencyId)
        val tokenToId = requireNotNull(toAsset.token.configuration.currencyId)

        return SwapDetailsViewState(
            fromTokenId = tokenFromId,
            toTokenId = tokenToId,
            fromTokenName = fromAsset.token.configuration.symbol.uppercase(),
            toTokenName = toAsset.token.configuration.symbol.uppercase(),
            fromTokenImage = GradientIconState.Remote(fromAsset.token.configuration.iconUrl, fromAsset.token.configuration.color),
            toTokenImage = GradientIconState.Remote(toAsset.token.configuration.iconUrl, toAsset.token.configuration.color),
            fromTokenAmount = fromAmount,
            toTokenAmount = toAmount,
            minmaxTitle = minMaxTitle,
            toTokenMinReceived = minMaxAmount,
            toFiatMinReceived = minMaxFiat.orEmpty(),
            fromTokenOnToToken = details.fromTokenOnToToken.formatCryptoDetail(),
            toTokenOnFromToken = details.toTokenOnFromToken.formatCryptoDetail(),
            route = details.route,

            fromChainId = fromAsset.token.configuration.chainId,
            toChainId = toAsset.token.configuration.chainId,
            fromChainIdImage = fromAsset.token.configuration.chainIcon,
            toChainIdImage = toAsset.token.configuration.chainIcon
        )
    }

    fun SwapDetailsViewState.toDetailItems(feeState: LoadingState<out SwapDetailsViewState.NetworkFee?>): List<FeeInfoViewState> {
        return listOfNotNull(
            FeeInfoViewState(
                caption = minmaxTitle,
                feeAmount = toTokenMinReceived,
                feeAmountFiat = toFiatMinReceived,
                tooltip = true,
                onToolTip = ::minMaxToolTipClick
            ),
            FeeInfoViewState(
                caption = resourceManager.getString(R.string.common_route),
                feeAmount = route,
                feeAmountFiat = null
            ),
            FeeInfoViewState(
                caption = "$fromTokenName / $toTokenName",
                feeAmount = fromTokenOnToToken,
                feeAmountFiat = null
            ),
            FeeInfoViewState(
                caption = "$toTokenName / $fromTokenName",
                feeAmount = toTokenOnFromToken,
                feeAmountFiat = null
            ),
            when {
                feeState is LoadingState.Loading -> {
                    FeeInfoViewState(
                        caption = resourceManager.getString(R.string.common_network_fee),
                        feeAmount = null,
                        feeAmountFiat = null,
                        tooltip = true,
                        onToolTip = ::networkFeeTooltipClick
                    )
                }

                feeState is LoadingState.Loaded && feeState.dataOrNull() != null -> {
                    FeeInfoViewState(
                        caption = resourceManager.getString(R.string.common_network_fee),
                        feeAmount = feeState.dataOrNull()?.tokenAmount,
                        feeAmountFiat = feeState.dataOrNull()?.fiatAmount,
                        tooltip = true,
                        onToolTip = ::networkFeeTooltipClick
                    )
                }

                else -> null
            }
        )
    }
}
