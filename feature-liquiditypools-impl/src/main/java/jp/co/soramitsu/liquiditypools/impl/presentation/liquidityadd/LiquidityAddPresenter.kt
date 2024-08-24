package jp.co.soramitsu.liquiditypools.impl.presentation.liquidityadd

import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.androidfoundation.format.isZero
import jp.co.soramitsu.common.base.errors.ValidationException
import jp.co.soramitsu.common.compose.component.AmountInputViewState
import jp.co.soramitsu.common.compose.component.FeeInfoViewState
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.presentation.dataOrNull
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.MAX_DECIMALS_8
import jp.co.soramitsu.common.utils.applyFiatRate
import jp.co.soramitsu.common.utils.combine
import jp.co.soramitsu.common.utils.flowOf
import jp.co.soramitsu.common.utils.formatCrypto
import jp.co.soramitsu.common.utils.formatCryptoDetail
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.common.utils.formatPercent
import jp.co.soramitsu.common.utils.moreThanZero
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.common.utils.requireValue
import jp.co.soramitsu.core.utils.utilityAsset
import jp.co.soramitsu.feature_liquiditypools_impl.R
import jp.co.soramitsu.liquiditypools.domain.interfaces.PoolsInteractor
import jp.co.soramitsu.liquiditypools.impl.presentation.CoroutinesStore
import jp.co.soramitsu.liquiditypools.impl.usecase.ValidateAddLiquidityUseCase
import jp.co.soramitsu.liquiditypools.impl.util.PolkaswapFormulas
import jp.co.soramitsu.liquiditypools.navigation.InternalPoolsRouter
import jp.co.soramitsu.liquiditypools.navigation.LiquidityPoolsNavGraphRoute
import jp.co.soramitsu.polkaswap.api.models.WithDesired
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.wallet.api.domain.fromValidationResult
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.domain.model.Asset
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject
import kotlin.math.min
import jp.co.soramitsu.core.models.Asset as CoreAsset

@Suppress("LargeClass")
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
    private val enteredBaseAmountFlow = MutableStateFlow(BigDecimal.ZERO)
    private val enteredTargetAmountFlow = MutableStateFlow(BigDecimal.ZERO)

    private val uiBaseAmountFlow = MutableStateFlow(BigDecimal.ZERO)
    private val uiTargetAmountFlow = MutableStateFlow(BigDecimal.ZERO)

    private var amountBase: BigDecimal = BigDecimal.ZERO
    private var amountTarget: BigDecimal = BigDecimal.ZERO

    private val isBaseAmountFocused = MutableStateFlow(false)
    private val isTargetAmountFocused = MutableStateFlow(false)

    private val isButtonLoading = MutableStateFlow(false)
    private val isCalculatingAmounts = MutableStateFlow<WithDesired?>(null)

    private var desired: WithDesired = WithDesired.INPUT

    private val _stateSlippage = MutableStateFlow(0.5)
    private val stateSlippage = _stateSlippage.asStateFlow()

    private val resetFlow = MutableStateFlow(Event(Unit))

    private val screenArgsFlow = internalPoolsRouter.createNavGraphRoutesFlow()
        .filterIsInstance<LiquidityPoolsNavGraphRoute.LiquidityAddScreen>()
        .distinctUntilChanged(areArgsEquivalent())
        .onEach {
            resetFlow.emit(Event(Unit))
        }
        .shareIn(coroutinesStore.uiScope, SharingStarted.Eagerly, 1)

    private val loadingAssetsInPoolFlow = MutableStateFlow<LoadingState<Pair<Asset, Asset>>>(LoadingState.Loading())

    private val tokensInPoolFlow = loadingAssetsInPoolFlow.filterIsInstance<LoadingState.Loaded<Pair<Asset, Asset>>>().map {
        it.data.first.token.configuration to it.data.second.token.configuration
    }.distinctUntilChanged()

    val isPoolPairEnabled =
        screenArgsFlow.map { screenArgs ->
            val (baseTokenId, targetTokenId) = screenArgs.ids
            poolsInteractor.isPairEnabled(
                baseTokenId,
                targetTokenId
            )
        }

    val networkFeeFlow = combine(
        enteredBaseAmountFlow,
        enteredTargetAmountFlow,
        tokensInPoolFlow,
        stateSlippage,
        isPoolPairEnabled
    ) { amountBase, amountTarget, (baseAsset, targetAsset), slippage, pairEnabled ->
        getLiquidityNetworkFee(
            tokenBase = baseAsset,
            tokenTarget = targetAsset,
            tokenBaseAmount = amountBase,
            tokenToAmount = amountTarget,
            pairEnabled = pairEnabled,
            pairPresented = true,
            slippageTolerance = slippage
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val feeInfoViewStateFlow: Flow<FeeInfoViewState> =
        flowOf {
            requireNotNull(chainsRepository.getChain(poolsInteractor.poolsChainId).utilityAsset?.id)
        }.flatMapLatest { utilityAssetId ->
            combine(
                networkFeeFlow,
                walletInteractor.assetFlow(poolsInteractor.poolsChainId, utilityAssetId)
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

    @OptIn(ExperimentalCoroutinesApi::class)
    val poolFlow = screenArgsFlow.flatMapLatest { screenargs ->
        poolsInteractor.getPoolData(
            baseTokenId = screenargs.ids.first,
            targetTokenId = screenargs.ids.second
        )
    }

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private fun subscribeState(coroutineScope: CoroutineScope) {
        screenArgsFlow.onEach {
            loadingAssetsInPoolFlow.value = LoadingState.Loading()
        }.flatMapLatest { screenArgs ->
            val ids = screenArgs.ids
            val chainId = poolsInteractor.poolsChainId
            val chainAssets = chainsRepository.getChain(chainId).assets
            val baseAsset = chainAssets.firstOrNull { it.currencyId == ids.first }?.let {
                walletInteractor.getCurrentAssetOrNull(chainId, it.id)
            }
            val targetAsset = chainAssets.firstOrNull { it.currencyId == ids.second }?.let {
                walletInteractor.getCurrentAssetOrNull(chainId, it.id)
            }

            val assetsFlow = flowOf {
                if (baseAsset == null || targetAsset == null) {
                    null
                } else {
                    baseAsset to targetAsset
                }
            }.mapNotNull { it }

            assetsFlow
        }.onEach {
            loadingAssetsInPoolFlow.value = LoadingState.Loaded(it)
        }.launchIn(coroutineScope)

        enteredBaseAmountFlow
            .onEach {
                desired = WithDesired.INPUT
                isCalculatingAmounts.value = desired
            }
            .debounce(INPUT_DEBOUNCE)
            .onEach { amount ->
                amountBase = amount
                updateAmounts()
            }.launchIn(coroutineScope)

        enteredTargetAmountFlow
            .onEach {
                desired = WithDesired.OUTPUT
                isCalculatingAmounts.value = desired
            }
            .debounce(INPUT_DEBOUNCE)
            .onEach { amount ->
                amountTarget = amount
                updateAmounts()
            }.launchIn(coroutineScope)
    }

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    fun createScreenStateFlow(coroutineScope: CoroutineScope): StateFlow<LiquidityAddState> {
        subscribeState(coroutineScope)

        return resetFlow.onEach {
            amountTarget = BigDecimal.ZERO
            amountBase = BigDecimal.ZERO
            uiBaseAmountFlow.value = BigDecimal.ZERO
            uiTargetAmountFlow.value = BigDecimal.ZERO
        }.debounce(RESET_DEBOUNCE).flatMapLatest {
            combine(
                poolFlow,
                loadingAssetsInPoolFlow,
                uiBaseAmountFlow,
                uiTargetAmountFlow,
                isBaseAmountFocused,
                isTargetAmountFocused,
                stateSlippage,
                feeInfoViewStateFlow,
                isButtonLoading,
                isCalculatingAmounts
            ) { pool, loadingAssetsState, baseShown, targetShown, baseFocused, targetFocused, slippage, feeInfo, isButtonLoading, isCalulatingAmount ->
                val assetBase = loadingAssetsState.dataOrNull()?.first
                val assetTarget = loadingAssetsState.dataOrNull()?.second

                val baseAmountInputViewState = if (assetBase == null) {
                    AmountInputViewState.defaultObj
                } else {
                    val totalBaseCrypto = assetBase.total?.formatCrypto(assetBase.token.configuration.symbol).orEmpty()
                    val totalBaseFiat = assetBase.fiatAmount?.formatFiat(assetBase.token.fiatSymbol)
                    val argsBase = totalBaseCrypto + totalBaseFiat?.let { " ($it)" }.orEmpty()
                    val totalBaseBalance = resourceManager.getString(R.string.common_available_format, argsBase)

                    AmountInputViewState(
                        tokenName = assetBase.token.configuration.symbol,
                        tokenImage = assetBase.token.configuration.iconUrl,
                        totalBalance = totalBaseBalance,
                        tokenAmount = baseShown,
                        fiatAmount = baseShown.applyFiatRate(assetBase.token.fiatRate)?.formatFiat(assetBase.token.fiatSymbol),
                        isFocused = baseFocused,
                        isShimmerAmounts = isCalulatingAmount == WithDesired.OUTPUT
                    )
                }

                val targetAmountInputViewState = if (assetTarget == null) {
                    AmountInputViewState.defaultObj
                } else {
                    val totalTargetCrypto = assetTarget.total?.formatCrypto(assetTarget.token.configuration.symbol).orEmpty()
                    val totalTargetFiat = assetTarget.fiatAmount?.formatFiat(assetTarget.token.fiatSymbol)
                    val argsTarget = totalTargetCrypto + totalTargetFiat?.let { " ($it)" }.orEmpty()
                    val totalTargetBalance = resourceManager.getString(R.string.common_available_format, argsTarget)

                    AmountInputViewState(
                        tokenName = assetTarget.token.configuration.symbol,
                        tokenImage = assetTarget.token.configuration.iconUrl,
                        totalBalance = totalTargetBalance,
                        tokenAmount = targetShown,
                        fiatAmount = targetShown.applyFiatRate(assetTarget.token.fiatRate)?.formatFiat(assetTarget.token.fiatSymbol),
                        isFocused = targetFocused,
                        isShimmerAmounts = isCalulatingAmount == WithDesired.INPUT
                    )
                }

                val isButtonEnabled = amountBase.moreThanZero() &&
                        amountTarget.moreThanZero() &&
                        feeInfo.feeAmount != null &&
                        isCalculatingAmounts.value == null

                LiquidityAddState(
                    apy = poolsInteractor.getSbApy(pool.basic.reserveAccount)?.toBigDecimal()?.formatPercent()?.let { "$it%" }.orEmpty(),
                    slippage = "$slippage%",
                    feeInfo = feeInfo,
                    buttonEnabled = isButtonEnabled,
                    buttonLoading = isButtonLoading,
                    baseAmountInputViewState = baseAmountInputViewState,
                    targetAmountInputViewState = targetAmountInputViewState
                )
            }
        }.stateIn(coroutineScope, SharingStarted.Lazily, LiquidityAddState())
    }

    private suspend fun updateAmounts() {
        calculateAmount()?.let { targetAmount ->
            val scaledTargetAmount = if (targetAmount.isZero()) {
                BigDecimal.ZERO
            } else {
                targetAmount.setScale(
                    min(MAX_DECIMALS_8, targetAmount.scale()),
                    RoundingMode.DOWN
                )
            }

            if (desired == WithDesired.INPUT) {
                uiTargetAmountFlow.value = scaledTargetAmount
                amountTarget = scaledTargetAmount
            } else {
                uiBaseAmountFlow.value = scaledTargetAmount
                amountBase = scaledTargetAmount
            }
        }

        if (isCalculatingAmounts.value == desired) {
            isCalculatingAmounts.value = null
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun calculateAmount(): BigDecimal? {
        val assets = loadingAssetsInPoolFlow.firstOrNull()?.dataOrNull()

        val baseAmount = if (desired == WithDesired.INPUT) enteredBaseAmountFlow.value else enteredTargetAmountFlow.value
        val targetAmount = if (desired == WithDesired.INPUT) enteredTargetAmountFlow.value else enteredBaseAmountFlow.value

        val liquidity = screenArgsFlow.flatMapLatest { screenArgs ->
            poolsInteractor.getPoolData(
                baseTokenId = screenArgs.ids.first,
                targetTokenId = screenArgs.ids.second
            )
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
                    precisionFirst = baseAsset.token.configuration.precision,
                    precisionSecond = targetAsset.token.configuration.precision,
                    desired = desired
                )
            }
        }
    }

    private suspend fun getLiquidityNetworkFee(
        tokenBase: CoreAsset,
        tokenTarget: CoreAsset,
        tokenBaseAmount: BigDecimal,
        tokenToAmount: BigDecimal,
        pairEnabled: Boolean,
        pairPresented: Boolean,
        slippageTolerance: Double
    ): BigDecimal {
        val chainId = poolsInteractor.poolsChainId

        val soraChain = walletInteractor.getChain(chainId)
        val address = accountInteractor.selectedMetaAccount().address(soraChain).orEmpty()
        val result = poolsInteractor.calcAddLiquidityNetworkFee(
            chainId,
            address,
            tokenBase,
            tokenTarget,
            tokenBaseAmount,
            tokenToAmount,
            pairEnabled,
            pairPresented,
            slippageTolerance,
        )
        return result ?: BigDecimal.ZERO
    }

    private fun setButtonLoading(loading: Boolean) {
        isButtonLoading.value = loading
    }

    override fun onAddReviewClick() {
        setButtonLoading(true)

        coroutinesStore.uiScope.launch {
            val chainId = poolsInteractor.poolsChainId
            val utilityAssetId = requireNotNull(chainsRepository.getChain(chainId).utilityAsset?.id)
            val utilityAmount = walletInteractor.getCurrentAsset(chainId, utilityAssetId).total
            val feeAmount = networkFeeFlow.firstOrNull().orZero()

            val poolAssets = loadingAssetsInPoolFlow.firstOrNull()?.dataOrNull() ?: return@launch

            val validationResult = validateAddLiquidityUseCase(
                assetBase = poolAssets.first,
                assetTarget = poolAssets.second,
                utilityAssetId = utilityAssetId,
                utilityAmount = utilityAmount.orZero(),
                amountBase = amountBase,
                amountTarget = amountTarget,
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
            val apy =
                poolFlow.firstOrNull()?.basic?.reserveAccount?.let { poolsInteractor.getSbApy(it) }
                    ?.toBigDecimal()?.formatPercent()?.let { "$it%" }.orEmpty()

            internalPoolsRouter.openAddLiquidityConfirmScreen(ids, amountBase, amountTarget, apy)
        }.invokeOnCompletion {
            coroutinesStore.uiScope.launch {
                delay(DEBOUNCE_300)
                setButtonLoading(false)
            }
        }
    }

    override fun onAddBaseAmountChange(amount: BigDecimal) {
        enteredBaseAmountFlow.value = amount
        uiBaseAmountFlow.value = amount
        amountBase = amount
    }

    override fun onAddTargetAmountChange(amount: BigDecimal) {
        enteredTargetAmountFlow.value = amount
        uiTargetAmountFlow.value = amount
        amountTarget = amount
    }

    override fun onAddBaseAmountFocusChange(isFocused: Boolean) {
        isBaseAmountFocused.value = isFocused
        if (desired != WithDesired.INPUT) {
            desired = WithDesired.INPUT
        }
    }

    override fun onAddTargetAmountFocusChange(isFocused: Boolean) {
        isTargetAmountFocused.value = isFocused
        if (desired != WithDesired.OUTPUT) {
            desired = WithDesired.OUTPUT
        }
    }

    override fun onAddTableItemClick(itemId: Int) {
        internalPoolsRouter.openInfoScreen(itemId)
    }

    private fun showError(throwable: Throwable) {
        if (throwable is ValidationException) {
            val (title, text) = throwable
            internalPoolsRouter.openErrorsScreen(title, text)
        } else {
            throwable.message?.let { internalPoolsRouter.openErrorsScreen(message = it) }
        }
    }

    private fun areArgsEquivalent(): (
        old: LiquidityPoolsNavGraphRoute.LiquidityAddScreen,
        new: LiquidityPoolsNavGraphRoute.LiquidityAddScreen
    ) -> Boolean =
        { old, new ->
            old.routeName == new.routeName &&
                    old.ids.first == new.ids.first &&
                    old.ids.second == new.ids.second
        }

    companion object {
        private const val INPUT_DEBOUNCE = 900L
        private const val RESET_DEBOUNCE = 200L
        private const val DEBOUNCE_300 = 300L
    }
}
