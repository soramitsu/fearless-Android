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
import jp.co.soramitsu.common.utils.flowOf
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
    private val enteredBaseAmountFlow = MutableStateFlow(BigDecimal.ZERO)
    private val enteredTargetAmountFlow = MutableStateFlow(BigDecimal.ZERO)

    private var amountBase: BigDecimal = BigDecimal.ZERO
    private var amountTarget: BigDecimal = BigDecimal.ZERO

    private val isBaseAmountFocused = MutableStateFlow(false)
    private val isTargetAmountFocused = MutableStateFlow(false)

    private var desired: WithDesired = WithDesired.INPUT

    private val _stateSlippage = MutableStateFlow(0.5)
    val stateSlippage = _stateSlippage.asStateFlow()

    private val screenArgsFlow = internalPoolsRouter.createNavGraphRoutesFlow()
        .filterIsInstance<LiquidityPoolsNavGraphRoute.LiquidityAddScreen>()
        .onEach {
            resetState()
        }
        .shareIn(coroutinesStore.uiScope, SharingStarted.Eagerly, 1)

    private fun resetState() {
        amountTarget = BigDecimal.ZERO
        amountBase = BigDecimal.ZERO
        stateFlow.value = stateFlow.value.copy(
            baseAmountInputViewState = stateFlow.value.baseAmountInputViewState.copy(
                tokenAmount = BigDecimal.ZERO,
                fiatAmount = null
            ),
            targetAmountInputViewState = stateFlow.value.targetAmountInputViewState.copy(
                tokenAmount = BigDecimal.ZERO,
                fiatAmount = null
            )
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val assetsInPoolFlow = screenArgsFlow.distinctUntilChanged().flatMapLatest { screenArgs ->
        val ids = screenArgs.ids
        val chainId = poolsInteractor.poolsChainId
        println("!!! assetsInPoolFlow ADD ids = $ids")
        val assetsFlow = walletInteractor.assetsFlow().mapNotNull {
            val firstInPair = it.firstOrNull {
                it.asset.token.configuration.currencyId == ids.first
                        && it.asset.token.configuration.chainId == chainId
            }
            val secondInPair = it.firstOrNull {
                it.asset.token.configuration.currencyId == ids.second
                        && it.asset.token.configuration.chainId == chainId
            }

            println("!!! assetsInPoolFlow ADD result: $firstInPair; $secondInPair")
            if (firstInPair == null || secondInPair == null) {
                return@mapNotNull null
            } else {
                firstInPair to secondInPair
            }
        }
        assetsFlow
    }.distinctUntilChanged()

    private val tokensInPoolFlow = assetsInPoolFlow.map {
        it.first.asset.token.configuration to it.second.asset.token.configuration
    }.distinctUntilChanged()

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private fun subscribeState(coroutineScope: CoroutineScope) {
        println("!!! AddPresenter subscribeState")
        screenArgsFlow.flatMapLatest { screenargs ->
            println("!!! AddPresenter screenArgsFlow = $screenargs")
            poolsInteractor.getPoolData(
                baseTokenId = screenargs.ids.first,
                targetTokenId = screenargs.ids.second
            ).onEach {
                stateFlow.value = stateFlow.value.copy(
                    apy = it.basic.sbapy?.toBigDecimal()?.formatPercent()?.let { "$it%" }
                )
            }
        }.launchIn(coroutineScope)

        assetsInPoolFlow.onEach { (assetBase, assetTarget) ->
            val totalBaseCrypto = assetBase.asset.total?.formatCrypto(assetBase.asset.token.configuration.symbol).orEmpty()
            val totalBaseFiat = assetBase.asset.fiatAmount?.formatFiat(assetBase.asset.token.fiatSymbol)
            val argsBase = totalBaseCrypto + totalBaseFiat?.let { " ($it)" }.orEmpty()
            val totalBaseBalance = resourceManager.getString(R.string.common_available_format, argsBase)

            val totalTargetCrypto = assetTarget.asset.total?.formatCrypto(assetTarget.asset.token.configuration.symbol).orEmpty()
            val totalTargetFiat = assetTarget.asset.fiatAmount?.formatFiat(assetTarget.asset.token.fiatSymbol)
            val argsTarget = totalTargetCrypto + totalTargetFiat?.let { " ($it)" }.orEmpty()
            val totalTargetBalance = resourceManager.getString(R.string.common_available_format, argsTarget)

            stateFlow.value = stateFlow.value.copy(
                baseAmountInputViewState = stateFlow.value.baseAmountInputViewState.copy(
                    tokenName = assetBase.asset.token.configuration.symbol,
                    tokenImage = assetBase.asset.token.configuration.iconUrl,
                    totalBalance = totalBaseBalance,
                ),
                targetAmountInputViewState = stateFlow.value.targetAmountInputViewState.copy(
                    tokenName = assetTarget.asset.token.configuration.symbol,
                    tokenImage = assetTarget.asset.token.configuration.iconUrl,
                    totalBalance = totalTargetBalance,
                )
            )
        }.launchIn(coroutineScope)

        enteredBaseAmountFlow.onEach {
            stateFlow.value = stateFlow.value.copy(
                baseAmountInputViewState = stateFlow.value.baseAmountInputViewState.copy(
                    fiatAmount = it.applyFiatRate(assetsInPoolFlow.firstOrNull()?.first?.asset?.token?.fiatRate)?.formatFiat(assetsInPoolFlow.firstOrNull()?.first?.asset?.token?.fiatSymbol),
                    tokenAmount = it,
                )
            )
        }
            .debounce(900)
            .onEach { amount ->
                amountBase = amount
                desired = WithDesired.INPUT
                updateAmounts()
            }.launchIn(coroutineScope)

        enteredTargetAmountFlow.onEach {
            stateFlow.value = stateFlow.value.copy(
                targetAmountInputViewState = stateFlow.value.targetAmountInputViewState.copy(
                    fiatAmount = it.applyFiatRate(assetsInPoolFlow.firstOrNull()?.second?.asset?.token?.fiatRate)?.formatFiat(assetsInPoolFlow.firstOrNull()?.second?.asset?.token?.fiatSymbol),
                    tokenAmount = it
                ),
            )
        }
            .debounce(900)
            .onEach { amount ->
                amountTarget = amount
                desired = WithDesired.OUTPUT
                updateAmounts()
            }.launchIn(coroutineScope)

        isBaseAmountFocused.onEach {
            stateFlow.value = stateFlow.value.copy(
                baseAmountInputViewState = stateFlow.value.baseAmountInputViewState.copy(
                    isFocused = it
                ),
            )
        }.launchIn(coroutineScope)

        isTargetAmountFocused.onEach {
            stateFlow.value = stateFlow.value.copy(
                targetAmountInputViewState = stateFlow.value.targetAmountInputViewState.copy(
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
                val tokenTarget = assetsInPoolFlow.firstOrNull()?.second?.asset?.token
                stateFlow.value = stateFlow.value.copy(
                    targetAmountInputViewState = stateFlow.value.targetAmountInputViewState.copy(
                        tokenAmount = scaledTargetAmount,
                        fiatAmount = scaledTargetAmount.applyFiatRate(tokenTarget?.fiatRate)?.formatFiat(tokenTarget?.fiatSymbol),

                        )
                )
                amountTarget = scaledTargetAmount
            } else {
                val tokenBase = assetsInPoolFlow.firstOrNull()?.first?.asset?.token
                stateFlow.value = stateFlow.value.copy(
                    baseAmountInputViewState = stateFlow.value.baseAmountInputViewState.copy(
                        tokenAmount = scaledTargetAmount,
                        fiatAmount = scaledTargetAmount.applyFiatRate(tokenBase?.fiatRate)?.formatFiat(tokenBase?.fiatSymbol),
                    )
                )
                amountBase = scaledTargetAmount
            }
        }

        updateButtonState()
    }

    private fun updateButtonState() {
        val isButtonEnabled = amountTarget.moreThanZero() && amountTarget.moreThanZero() && stateFlow.value.feeInfo.feeAmount != null
        println("!!! updateButtonState: $amountTarget; $amountTarget; ${stateFlow.value.feeInfo.feeAmount}")
        println("!!! updateButtonState: isButtonEnabled = $isButtonEnabled")
        stateFlow.value = stateFlow.value.copy(
            buttonEnabled = isButtonEnabled
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun calculateAmount(): BigDecimal? {
        val assets = assetsInPoolFlow.firstOrNull()

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
                    precisionFirst = baseAsset.asset.token.configuration.precision,
                    precisionSecond = targetAsset.asset.token.configuration.precision,
                    desired = desired
                )
            }
        }
    }

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
    )
    { amountBase, amountTarget, (baseAsset, targetAsset), slippage, pairEnabled ->
        val networkFee = getLiquidityNetworkFee(
            tokenBase = baseAsset,
            tokenTarget = targetAsset,
            tokenBaseAmount = amountBase,
            tokenToAmount = amountTarget,
            pairEnabled = pairEnabled,
            pairPresented = true,
            slippageTolerance = slippage
        )
        println("!!!! networkFeeFlow emit $networkFee")
        networkFee
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

    private suspend fun getLiquidityNetworkFee(
        tokenBase: Asset,
        tokenTarget: Asset,
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
        stateFlow.value = stateFlow.value.copy(
            buttonLoading = loading
        )
    }

    override fun onAddReviewClick() {
        setButtonLoading(true)
        println("!!! should setButtonLoading(true)")

        coroutinesStore.uiScope.launch {
            val chainId = poolsInteractor.poolsChainId
            val utilityAssetId = requireNotNull(chainsRepository.getChain(chainId).utilityAsset?.id)
            val utilityAmount = walletInteractor.getCurrentAsset(chainId, utilityAssetId).total
            val feeAmount = networkFeeFlow.firstOrNull().orZero()

            val poolAssets = assetsInPoolFlow.firstOrNull() ?: return@launch

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
            internalPoolsRouter.openAddLiquidityConfirmScreen(ids, amountBase, amountTarget, stateFlow.value.apy.orEmpty())
        }.invokeOnCompletion {
            println("!!! setButtonLoading(false)")
            coroutinesStore.uiScope.launch {
                delay(300)
                setButtonLoading(false)
            }
        }
    }

    override fun onAddBaseAmountChange(amount: BigDecimal) {
        enteredBaseAmountFlow.value = amount
        amountBase = amount

        updateButtonState()
    }

    override fun onAddTargetAmountChange(amount: BigDecimal) {
        enteredTargetAmountFlow.value = amount
        amountTarget = amount

        updateButtonState()
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