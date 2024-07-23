package jp.co.soramitsu.liquiditypools.impl.presentation.liquidityadd

import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.androidfoundation.format.isZero
import jp.co.soramitsu.common.base.errors.ValidationException
import jp.co.soramitsu.common.compose.component.FeeInfoViewState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.MAX_DECIMALS_8
import jp.co.soramitsu.common.utils.applyFiatRate
import jp.co.soramitsu.common.utils.formatCrypto
import jp.co.soramitsu.common.utils.formatCryptoDetail
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.common.utils.formatPercent
import jp.co.soramitsu.common.utils.moreThanZero
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.common.utils.requireValue
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.utils.utilityAsset
import jp.co.soramitsu.feature_liquiditypools_impl.R
import jp.co.soramitsu.liquiditypools.domain.interfaces.PoolsInteractor
import jp.co.soramitsu.liquiditypools.impl.presentation.CoroutinesStore
import jp.co.soramitsu.liquiditypools.impl.usecase.ValidateAddLiquidityUseCase
import jp.co.soramitsu.liquiditypools.navigation.InternalPoolsRouter
import jp.co.soramitsu.liquiditypools.navigation.LiquidityPoolsNavGraphRoute
import jp.co.soramitsu.polkaswap.api.data.LiquidityData
import jp.co.soramitsu.polkaswap.api.models.WithDesired
import jp.co.soramitsu.polkaswap.impl.util.PolkaswapFormulas
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.wallet.api.domain.fromValidationResult
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch

class LiquidityAddPresenter @Inject constructor(
    private val coroutinesStore: CoroutinesStore,
    private val internalPoolsRouter: InternalPoolsRouter,
    private val walletInteractor: WalletInteractor,
    private val chainsRepository: ChainsRepository,
    private val poolsInteractor: PoolsInteractor,
    private val accountInteractor: AccountInteractor,
    private val resourceManager: ResourceManager,
    private val validateAddLiquidityUseCase: ValidateAddLiquidityUseCase,
) : LiquidityAddCallbacks {
    private val enteredFromAmountFlow = MutableStateFlow(BigDecimal.ZERO)
    private val enteredToAmountFlow = MutableStateFlow(BigDecimal.ZERO)

    private var amountFrom: BigDecimal = BigDecimal.ZERO
    private var amountTo: BigDecimal = BigDecimal.ZERO

    private val isFromAmountFocused = MutableStateFlow(false)
    private val isToAmountFocused = MutableStateFlow(false)

    private var desired: WithDesired = WithDesired.INPUT

    private val _stateSlippage = MutableStateFlow(0.5)
    val stateSlippage = _stateSlippage.asStateFlow()

    private var liquidityData: LiquidityData = LiquidityData()

    private val screenArgsFlow = internalPoolsRouter.createNavGraphRoutesFlow()
        .filterIsInstance<LiquidityPoolsNavGraphRoute.LiquidityAddScreen>()
        .shareIn(coroutinesStore.uiScope, SharingStarted.Eagerly, 1)

    @OptIn(ExperimentalCoroutinesApi::class)
    val assetsInPoolFlow = screenArgsFlow.flatMapLatest { screenArgs ->
        val ids = screenArgs.ids
        val chainId = screenArgs.chainId
        println("!!! assetsInPoolFlow ids = $ids")
        val assetsFlow = walletInteractor.assetsFlow().mapNotNull {
            val firstInPair = it.firstOrNull {
                it.asset.token.configuration.currencyId == ids.first
                        && it.asset.token.configuration.chainId == chainId
            }
            val secondInPair = it.firstOrNull {
                it.asset.token.configuration.currencyId == ids.second
                        && it.asset.token.configuration.chainId == chainId
            }

            println("!!! assetsInPoolFlow result% $firstInPair; $secondInPair")
            if (firstInPair == null || secondInPair == null) {
                return@mapNotNull null
            } else {
                firstInPair to secondInPair
            }
        }
        assetsFlow
    }.distinctUntilChanged()

    val tokensInPoolFlow = assetsInPoolFlow.map {
        it.first.asset.token.configuration to it.second.asset.token.configuration
    }.distinctUntilChanged()

//    val userPoolDataFlow = flowOf { getPoolDataDto() }

    init {
    }

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private fun subscribeState(coroutineScope: CoroutineScope) {
        screenArgsFlow.flatMapLatest {
            val (tokenFromId, tokenToId) = it.ids
            poolsInteractor.getPoolData(it.chainId, tokenFromId, tokenToId).onEach {
                stateFlow.value = stateFlow.value.copy(
                    apy = it.basic.sbapy?.toBigDecimal()?.formatPercent()?.let { "$it%" }
                )
            }
        }.launchIn(coroutineScope)

//        userPoolDataFlow.onEach { pool ->
//            val apy = pool?.reservesAccount?.let { poolsInteractor.getPoolStrategicBonusAPY(it) }
//            stateFlow.value = stateFlow.value.copy(
//                apy = apy?.toBigDecimal()?.formatPercent()?.let { "$it%" }
//            )
//        }.launchIn(coroutineScope)

        assetsInPoolFlow.onEach { (assetFrom, assetTo) ->
            val totalFromCrypto = assetFrom.asset.total?.formatCrypto(assetFrom.asset.token.configuration.symbol).orEmpty()
            val totalFromFiat = assetFrom.asset.fiatAmount?.formatFiat(assetFrom.asset.token.fiatSymbol)
            val argsFrom = totalFromCrypto + totalFromFiat?.let { " ($it)" }
            val totalFromBalance = resourceManager.getString(R.string.common_available_format, argsFrom)

            val totalToCrypto = assetTo.asset.total?.formatCrypto(assetTo.asset.token.configuration.symbol).orEmpty()
            val totalToFiat = assetTo.asset.fiatAmount?.formatFiat(assetTo.asset.token.fiatSymbol)
            val argsTo = totalToCrypto + totalToFiat?.let { " ($it)" }
            val totalToBalance = resourceManager.getString(R.string.common_available_format, argsTo)

            stateFlow.value = stateFlow.value.copy(
                fromAmountInputViewState = stateFlow.value.fromAmountInputViewState.copy(
                    tokenName = assetFrom.asset.token.configuration.symbol,
                    tokenImage = assetFrom.asset.token.configuration.iconUrl,
                    totalBalance = totalFromBalance,
                ),
                toAmountInputViewState = stateFlow.value.toAmountInputViewState.copy(
                    tokenName = assetTo.asset.token.configuration.symbol,
                    tokenImage = assetTo.asset.token.configuration.iconUrl,
                    totalBalance = totalToBalance,
                )
            )
        }.launchIn(coroutineScope)

        enteredFromAmountFlow.onEach {
            stateFlow.value = stateFlow.value.copy(
                fromAmountInputViewState = stateFlow.value.fromAmountInputViewState.copy(
                    fiatAmount = it.applyFiatRate(assetsInPoolFlow.firstOrNull()?.first?.asset?.token?.fiatRate)?.formatFiat(assetsInPoolFlow.firstOrNull()?.first?.asset?.token?.fiatSymbol),
                    tokenAmount = it,
                )
            )
        }
            .debounce(900)
            .onEach { amount ->
                amountFrom = amount
                desired = WithDesired.INPUT
                updateAmounts()
            }.launchIn(coroutineScope)

        enteredToAmountFlow.onEach {
            stateFlow.value = stateFlow.value.copy(
                toAmountInputViewState = stateFlow.value.toAmountInputViewState.copy(
                    fiatAmount = it.applyFiatRate(assetsInPoolFlow.firstOrNull()?.second?.asset?.token?.fiatRate)?.formatFiat(assetsInPoolFlow.firstOrNull()?.second?.asset?.token?.fiatSymbol),
                    tokenAmount = it
                ),
            )
        }
            .debounce(900)
            .onEach { amount ->
                amountTo = amount
                desired = WithDesired.OUTPUT
                updateAmounts()
            }.launchIn(coroutineScope)

        isFromAmountFocused.onEach {
            stateFlow.value = stateFlow.value.copy(
                fromAmountInputViewState = stateFlow.value.fromAmountInputViewState.copy(
                    isFocused = it
                ),
            )
        }.launchIn(coroutineScope)

        isToAmountFocused.onEach {
            stateFlow.value = stateFlow.value.copy(
                toAmountInputViewState = stateFlow.value.toAmountInputViewState.copy(
                    isFocused = it
                ),
            )
        }.launchIn(coroutineScope)

        stateSlippage.onEach {
            stateFlow.value = stateFlow.value.copy(
                slippage = "$it%"
            )
        }.launchIn(coroutineScope)

        feeInfoViewStateFlow.onEach {
            stateFlow.value = stateFlow.value.copy(
                feeInfo = it
            )
            updateButtonState()
        }.launchIn(coroutineScope)
    }


    private val stateFlow = MutableStateFlow(LiquidityAddState())

    fun createScreenStateFlow(coroutineScope: CoroutineScope): StateFlow<LiquidityAddState> {
        subscribeState(coroutineScope)
        return stateFlow
    }

//    suspend fun getPoolDataDto(): PoolDataDto? {
//        val chain = accountInteractor.getChain(soraMainChainId)
//        val address = accountInteractor.selectedMetaAccount().address(chain)
//        val assets = assetsInPoolFlow.firstOrNull()
//        val baseTokenId = assets?.first?.asset?.token?.configuration?.currencyId
//        val tokenToId = assets?.second?.asset?.token?.configuration?.currencyId?.fromHex()
//
//        if (address == null || baseTokenId == null || tokenToId == null) return null
//
//        return poolsInteractor.getUserPoolData(soraMainChainId, address, baseTokenId, tokenToId)
//    }

    private suspend fun updateAmounts() {
        calculateAmount()?.let { targetAmount ->
            val scaledTargetAmount = when {
                targetAmount.isZero() -> BigDecimal.ZERO
                else -> targetAmount.setScale(
                    min(MAX_DECIMALS_8, targetAmount.scale()),
                    RoundingMode.DOWN
                )
            }

            if (desired == WithDesired.INPUT) {
                val tokenTo = assetsInPoolFlow.firstOrNull()?.second?.asset?.token
                stateFlow.value = stateFlow.value.copy(
                    toAmountInputViewState = stateFlow.value.toAmountInputViewState.copy(
                        tokenAmount = scaledTargetAmount,
                        fiatAmount = scaledTargetAmount.applyFiatRate(tokenTo?.fiatRate)?.formatFiat(tokenTo?.fiatSymbol),

                    )
                )
                amountTo = scaledTargetAmount
            } else {
                val tokenFrom = assetsInPoolFlow.firstOrNull()?.first?.asset?.token
                stateFlow.value = stateFlow.value.copy(
                    fromAmountInputViewState = stateFlow.value.fromAmountInputViewState.copy(
                        tokenAmount = scaledTargetAmount,
                        fiatAmount = scaledTargetAmount.applyFiatRate(tokenFrom?.fiatRate)?.formatFiat(tokenFrom?.fiatSymbol),
                    )
                )
                amountFrom = scaledTargetAmount
            }
        }

        updateButtonState()
    }

    private fun updateButtonState() {
        val isButtonEnabled = amountTo.moreThanZero() && amountTo.moreThanZero() && stateFlow.value.feeInfo.feeAmount != null
        println("!!! updateButtonState: $amountTo; $amountTo; ${stateFlow.value.feeInfo.feeAmount}")
        println("!!! updateButtonState: isButtonEnabled = $isButtonEnabled")
        stateFlow.value = stateFlow.value.copy(
            buttonEnabled = isButtonEnabled
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun calculateAmount(): BigDecimal? {
        val assets = assetsInPoolFlow.firstOrNull()

        val baseAmount = if (desired == WithDesired.INPUT) enteredFromAmountFlow.value else enteredToAmountFlow.value
        val targetAmount = if (desired == WithDesired.INPUT) enteredToAmountFlow.value else enteredFromAmountFlow.value

//        val liquidity2 = userPoolDataFlow.firstOrNull()

        val liquidity = screenArgsFlow.flatMapLatest { screenArgs ->
            val chainId = screenArgs.chainId
            val (tokenFromId, tokenToId) = screenArgs.ids
            poolsInteractor.getPoolData(chainId, tokenFromId, tokenToId)
        }.firstOrNull()

        return assets?.let { (baseAsset, targetAsset) ->
            val reservesFirst = liquidity?.basic?.baseReserves.orZero()
            val reservesSecond = liquidity?.basic?.targetReserves.orZero()

            if (reservesSecond.isZero() || reservesSecond.isZero()) {
                targetAmount
            } else {
                PolkaswapFormulas.calculateAddLiquidityAmount(
                    baseAmount = baseAmount,
                    reservesFirst = reservesFirst,
                    reservesSecond = reservesSecond,
                    precisionFirst = baseAsset.asset.token.configuration.precision,
                    precisionSecond = targetAsset.asset.token.configuration.precision,
                    desired = desired
                )
            }
        }
    }

    val isPoolPairEnabled =
        screenArgsFlow.map { screenArgs ->
            val (tokenFromId, tokenToId) = screenArgs.ids
            poolsInteractor.isPairEnabled(
                screenArgs.chainId,
                tokenFromId,
                tokenToId
            )
        }

    val networkFeeFlow = combine(
        enteredFromAmountFlow,
        enteredToAmountFlow,
        tokensInPoolFlow,
        stateSlippage,
        isPoolPairEnabled
    )
    { amountFrom, amountTo, (baseAsset, targetAsset), slippage, pairEnabled ->
        val networkFee = getLiquidityNetworkFee(
            tokenFrom = baseAsset,
            tokenTo = targetAsset,
            tokenFromAmount = amountFrom,
            tokenToAmount = amountTo,
            pairEnabled = pairEnabled,
            pairPresented = true, //pairPresented,
            slippageTolerance = slippage
        )
        println("!!!! networkFeeFlow emit $networkFee")
        networkFee
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val feeInfoViewStateFlow: Flow<FeeInfoViewState> =
        screenArgsFlow.map { screenArgs ->
            val utilityAssetId = requireNotNull(chainsRepository.getChain(screenArgs.chainId).utilityAsset?.id)
            screenArgs.chainId to utilityAssetId
    }.flatMapLatest { (chainId, utilityAssetId) ->
        combine(
            networkFeeFlow,
            walletInteractor.assetFlow(chainId, utilityAssetId)
        ) { networkFee, utilityAsset ->
            val tokenSymbol = utilityAsset.token.configuration.symbol
            val tokenFiatRate = utilityAsset.token.fiatRate
            val tokenFiatSymbol = utilityAsset.token.fiatSymbol

            FeeInfoViewState(
                feeAmount = networkFee.formatCryptoDetail(tokenSymbol),
                feeAmountFiat = networkFee.applyFiatRate(tokenFiatRate)?.formatFiat(tokenFiatSymbol),
            )
        }
    }

    private suspend fun getLiquidityNetworkFee(
        tokenFrom: Asset,
        tokenTo: Asset,
        tokenFromAmount: BigDecimal,
        tokenToAmount: BigDecimal,
        pairEnabled: Boolean,
        pairPresented: Boolean,
        slippageTolerance: Double
    ): BigDecimal {
        val chainId = screenArgsFlow.replayCache.firstOrNull()?.chainId ?: return BigDecimal.ZERO

        val soraChain = walletInteractor.getChain(chainId)
        val address = accountInteractor.selectedMetaAccount().address(soraChain).orEmpty()
        val result = poolsInteractor.calcAddLiquidityNetworkFee(
            chainId,
            address,
            tokenFrom,
            tokenTo,
            tokenFromAmount,
            tokenToAmount,
            pairEnabled,
            pairPresented,
            slippageTolerance,
        )
        return result ?: BigDecimal.ZERO
    }

    private fun setButtonLoading(loading: Boolean) {
        stateFlow.value = stateFlow.value.copy(
            buttonLoading = loading
        )
    }

    override fun onReviewClick() {
        setButtonLoading(true)
        println("!!! should setButtonLoading(true)")

        coroutinesStore.uiScope.launch {
            val chainId = screenArgsFlow.replayCache.firstOrNull()?.chainId ?: return@launch

            val utilityAssetId = requireNotNull(chainsRepository.getChain(chainId).utilityAsset?.id)
            val utilityAmount = walletInteractor.getCurrentAsset(chainId, utilityAssetId).total
            val feeAmount = networkFeeFlow.firstOrNull().orZero()

            val poolAssets = assetsInPoolFlow.firstOrNull() ?: return@launch

            val validationResult = validateAddLiquidityUseCase(
                assetFrom = poolAssets.first,
                assetTo = poolAssets.second,
                utilityAssetId = utilityAssetId,
                utilityAmount = utilityAmount.orZero(),
                amountFrom = amountFrom,
                amountTo = amountTo,
                feeAmount = feeAmount,
            )

            validationResult.exceptionOrNull()?.let {
                showError(it)
                return@launch
            }

            val validationValue = validationResult.requireValue()
            ValidationException.fromValidationResult(validationValue, resourceManager)?.let {
                showError(it)
                return@launch
            }

            val ids = screenArgsFlow.replayCache.lastOrNull()?.ids ?: return@launch
            internalPoolsRouter.openAddLiquidityConfirmScreen(chainId, ids, amountFrom, amountTo, stateFlow.value.apy.orEmpty())
        }.invokeOnCompletion {
            println("!!! setButtonLoading(false)")
            coroutinesStore.uiScope.launch {
                delay(300)
                setButtonLoading(false)
            }
        }
    }

    override fun onFromAmountChange(amount: BigDecimal) {
        enteredFromAmountFlow.value = amount
        amountFrom = amount

        updateButtonState()
    }

    override fun onToAmountChange(amount: BigDecimal) {
        enteredToAmountFlow.value = amount
        amountTo = amount

        updateButtonState()
    }

    override fun onFromAmountFocusChange(isFocused: Boolean) {
        isFromAmountFocused.value = isFocused
        if (desired != WithDesired.INPUT) {
            desired = WithDesired.INPUT
        }
    }

    override fun onToAmountFocusChange(isFocused: Boolean) {
        isToAmountFocused.value = isFocused
        if (desired != WithDesired.OUTPUT) {
            desired = WithDesired.OUTPUT
        }
    }

    private fun showError(throwable: Throwable) {
        when (throwable) {
            is ValidationException -> {
                val (title, text) = throwable
                internalPoolsRouter.openErrorsScreen(title, text)
            }

            else -> {
                throwable.message?.let { internalPoolsRouter.openErrorsScreen(message = it) }
            }
        }
    }
}