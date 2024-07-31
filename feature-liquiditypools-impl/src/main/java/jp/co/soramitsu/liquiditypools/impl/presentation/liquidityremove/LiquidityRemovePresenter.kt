package jp.co.soramitsu.liquiditypools.impl.presentation.liquidityremove

import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
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
import jp.co.soramitsu.common.utils.moreThanZero
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.common.utils.requireValue
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.utils.utilityAsset
import jp.co.soramitsu.feature_liquiditypools_impl.R
import jp.co.soramitsu.liquiditypools.domain.interfaces.DemeterFarmingInteractor
import jp.co.soramitsu.liquiditypools.domain.interfaces.PoolsInteractor
import jp.co.soramitsu.liquiditypools.impl.presentation.CoroutinesStore
import jp.co.soramitsu.liquiditypools.impl.usecase.ValidateRemoveLiquidityUseCase
import jp.co.soramitsu.liquiditypools.navigation.InternalPoolsRouter
import jp.co.soramitsu.liquiditypools.navigation.LiquidityPoolsNavGraphRoute
import jp.co.soramitsu.polkaswap.api.domain.models.CommonUserPoolData
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
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
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

@OptIn(FlowPreview::class)
class LiquidityRemovePresenter @Inject constructor(
    private val coroutinesStore: CoroutinesStore,
    private val internalPoolsRouter: InternalPoolsRouter,
    private val walletInteractor: WalletInteractor,
    private val chainsRepository: ChainsRepository,
    private val poolsInteractor: PoolsInteractor,
    private val demeterFarmingInteractor: DemeterFarmingInteractor,
    private val accountInteractor: AccountInteractor,
    private val resourceManager: ResourceManager,
    private val validateRemoveLiquidityUseCase: ValidateRemoveLiquidityUseCase,
) : LiquidityRemoveCallbacks {
    private val enteredBaseAmountFlow = MutableStateFlow(BigDecimal.ZERO)
    private val enteredTargetAmountFlow = MutableStateFlow(BigDecimal.ZERO)

    private var amountBase: BigDecimal = BigDecimal.ZERO
    private var amountTarget: BigDecimal = BigDecimal.ZERO

    private val isBaseAmountFocused = MutableStateFlow(false)
    private val isTargetAmountFocused = MutableStateFlow(false)

    private var poolInFarming = false
    private var poolDataUsable: CommonUserPoolData? = null
    private var poolDataReal: CommonUserPoolData? = null
    private var percent: Double = 0.0


    private val screenArgsFlow = internalPoolsRouter.createNavGraphRoutesFlow()
        .filterIsInstance<LiquidityPoolsNavGraphRoute.LiquidityRemoveScreen>()
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
    val assetsInPoolFlow = screenArgsFlow.flatMapLatest { screenArgs ->
        val ids = screenArgs.ids
        val chainId = poolsInteractor.poolsChainId
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

    private val tokensInPoolFlow = assetsInPoolFlow.map {
        it.first.asset.token.configuration to it.second.asset.token.configuration
    }.distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    val poolDataFlow = screenArgsFlow.flatMapLatest {
        poolsInteractor.getPoolData(
            baseTokenId = it.ids.first,
            targetTokenId = it.ids.second
        )
    }

    init {
        coroutinesStore.ioScope.launch {
            poolDataFlow.map { data ->
                println("!!! poolDataFlow data = $data")
                data.user?.let {
                    CommonUserPoolData(
                        data.basic,
                        data.user!!,
                    )
                }
            }
                .catch { showError(it) }
                .distinctUntilChanged()
                .debounce(500)
                .map { poolDataLocal ->
                    println("!!! poolDataFlow map poolDataLocal = $poolDataLocal")
                    poolDataReal = poolDataLocal
                    poolInFarming = false

                    val ids = screenArgsFlow.replayCache.firstOrNull()?.ids ?: return@map null
                    val (token1Id, token2Id) = ids

                    val chainId = poolsInteractor.poolsChainId
                    val result = if (poolDataLocal != null) {
                        val maxPercent = demeterFarmingInteractor.getFarmedPools(chainId)?.filter { pool ->
                            pool.tokenBase.token.configuration.currencyId == token1Id
                                    && pool.tokenTarget.token.configuration.currencyId == token2Id
                        }?.maxOfOrNull {
                            PolkaswapFormulas.calculateShareOfPoolFromAmount(
                                it.amount,
                                poolDataLocal.user.poolProvidersBalance,
                            )
                        }

                        if (maxPercent != null && !maxPercent.isNaN()) {
                            poolInFarming = true
                            val usablePercent = 100 - maxPercent
                            poolDataLocal.copy(
                                user = poolDataLocal.user.copy(
                                    basePooled = PolkaswapFormulas.calculateAmountByPercentage(
                                        poolDataLocal.user.basePooled,
                                        usablePercent,
                                        poolDataLocal.basic.baseToken.token.configuration.precision,
                                    ),
                                    targetPooled = PolkaswapFormulas.calculateAmountByPercentage(
                                        poolDataLocal.user.targetPooled,
                                        usablePercent,
                                        poolDataLocal.basic.baseToken.token.configuration.precision,
                                    ),
                                    poolProvidersBalance = PolkaswapFormulas.calculateAmountByPercentage(
                                        poolDataLocal.user.poolProvidersBalance,
                                        usablePercent,
                                        poolDataLocal.basic.baseToken.token.configuration.precision,
                                    ),
                                )
                            )
                        } else {
                            poolDataLocal
                        }
                    } else {
                        null
                    }
                    println("!!! poolDataFlow result = $result")
                    result
                }
                .collectLatest { poolDataLocal ->
                    println("!!! poolDataFlow collectLatest poolDataLocal = $poolDataLocal")

                    poolDataUsable = poolDataLocal
                    amountBase =
                        if (poolDataLocal != null) PolkaswapFormulas.calculateAmountByPercentage(
                            poolDataLocal.user.basePooled,
                            percent,
                            poolDataLocal.basic.baseToken.token.configuration.precision,
                        ) else BigDecimal.ZERO
                    amountTarget =
                        if (poolDataLocal != null) PolkaswapFormulas.calculateAmountByPercentage(
                            poolDataLocal.user.targetPooled,
                            percent,
                            poolDataLocal.basic.targetToken?.token?.configuration?.precision!!,
                        ) else BigDecimal.ZERO

                    coroutinesStore.ioScope.launch {
                        updateAmounts()
                    }
                }
        }
    }

    @OptIn(FlowPreview::class)
    private fun subscribeState(coroutineScope: CoroutineScope) {
        poolDataFlow.onEach {
            val baseToken = it.basic.baseToken.token
            val targetToken = it.basic.targetToken?.token

            val pooledBaseCrypto = it.user?.basePooled?.formatCrypto(baseToken.configuration.symbol).orEmpty()
            val pooledBaseFiat = it.user?.basePooled?.applyFiatRate(baseToken.fiatRate)?.formatFiat(baseToken.fiatSymbol)
            val argsBase = pooledBaseCrypto + pooledBaseFiat?.let { " ($it)" }.orEmpty()
            val pooledBaseBalance = resourceManager.getString(R.string.common_available_format, argsBase)

            val pooledTargetCrypto = it.user?.targetPooled?.formatCrypto(targetToken?.configuration?.symbol).orEmpty()
            val pooledTargetFiat = it.user?.targetPooled?.applyFiatRate(targetToken?.fiatRate)?.formatFiat(targetToken?.fiatSymbol)
            val argsTarget = pooledTargetCrypto + pooledTargetFiat?.let { " ($it)" }.orEmpty()
            val pooledTargetBalance = resourceManager.getString(R.string.common_available_format, argsTarget)

            stateFlow.value = stateFlow.value.copy(
                baseAmountInputViewState = stateFlow.value.baseAmountInputViewState.copy(
                    tokenName = baseToken.configuration.symbol,
                    tokenImage = baseToken.configuration.iconUrl,
                    totalBalance = pooledBaseBalance,
                ),
                targetAmountInputViewState = stateFlow.value.targetAmountInputViewState.copy(
                    tokenName = targetToken?.configuration?.symbol,
                    tokenImage = targetToken?.configuration?.iconUrl,
                    totalBalance = pooledTargetBalance,
                )
            )
        }.launchIn(coroutineScope)

        utilityAssetFlow.onEach {
            stateFlow.value = stateFlow.value.copy(
                transferableAmount = it.transferable.formatCrypto(it.token.configuration.symbol),
                transferableFiat = it.transferable.applyFiatRate(it.token.fiatRate)?.formatFiat(it.token.fiatSymbol)
            )
        }.launchIn(coroutineScope)

        enteredBaseAmountFlow.onEach {
            val baseToken = assetsInPoolFlow.firstOrNull()?.first?.asset?.token
            stateFlow.value = stateFlow.value.copy(
                baseAmountInputViewState = stateFlow.value.baseAmountInputViewState.copy(
                    fiatAmount = it.applyFiatRate(baseToken?.fiatRate)?.formatFiat(baseToken?.fiatSymbol),
                    tokenAmount = it,
                )
            )
        }
            .debounce(900)
            .onEach { amount ->
                poolDataUsable?.let {
                    amountBase = if (it.user.basePooled <= amount) amount else it.user.basePooled

                    val precisionTo = poolDataFlow.firstOrNull()?.basic?.targetToken?.token?.configuration?.precision
                    amountTarget = PolkaswapFormulas.calculateOneAmountFromAnother(
                        amountBase,
                        it.user.basePooled,
                        it.user.targetPooled,
                        precisionTo
                    )
                    percent = PolkaswapFormulas.calculateShareOfPoolFromAmount(
                        amountBase,
                        it.user.basePooled,
                    )
                }

                coroutinesStore.uiScope.launch {
                    updateAmounts()
                }
            }.launchIn(coroutineScope)

        enteredTargetAmountFlow.onEach {
            println("!!! enteredToAmountFlow.onEach = $it")

            val targetToken = assetsInPoolFlow.firstOrNull()?.second?.asset?.token
            stateFlow.value = stateFlow.value.copy(
                targetAmountInputViewState = stateFlow.value.targetAmountInputViewState.copy(
                    fiatAmount = it.applyFiatRate(targetToken?.fiatRate)?.formatFiat(targetToken?.fiatSymbol),
                    tokenAmount = it
                ),
            )
        }
            .debounce(900)
            .onEach { amount ->
                println("!!! enteredToAmountFlow.onEach debounced = $amount")
                poolDataUsable?.let {
                    amountTarget = if (amount <= it.user.targetPooled) amount else it.user.targetPooled

                    val precisionBase = poolDataFlow.firstOrNull()?.basic?.baseToken?.token?.configuration?.precision
                    amountBase = PolkaswapFormulas.calculateOneAmountFromAnother(
                        amountTarget,
                        it.user.targetPooled,
                        it.user.basePooled,
                        precisionBase
                    )
                    percent = PolkaswapFormulas.calculateShareOfPoolFromAmount(
                        amountBase,
                        it.user.basePooled,
                    )
                }

//                updateAmounts()
                coroutinesStore.uiScope.launch {
                    updateAmounts()
                }
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

        feeInfoViewStateFlow.onEach {
            stateFlow.value = stateFlow.value.copy(
                feeInfo = it
            )
            updateButtonState()
        }.launchIn(coroutineScope)
    }

    private val stateFlow = MutableStateFlow(LiquidityRemoveState())

    fun createScreenStateFlow(coroutineScope: CoroutineScope): StateFlow<LiquidityRemoveState> {
        subscribeState(coroutineScope)
        return stateFlow
    }

    private suspend fun updateAmounts() {
        assetsInPoolFlow.firstOrNull()?.let { (assetBase, assetTarget) ->
            if (amountBase.compareTo(stateFlow.value.baseAmountInputViewState.tokenAmount) != 0) {
                println("!!! updateAmounts amountFrom to $amountBase")

                val scaledAmountBase = when {
                    amountBase.isZero() -> BigDecimal.ZERO
                    else -> amountBase.setScale(
                        min(MAX_DECIMALS_8, amountBase.scale()),
                        RoundingMode.DOWN
                    )
                }

                stateFlow.value = stateFlow.value.copy(
                    baseAmountInputViewState = stateFlow.value.baseAmountInputViewState.copy(
                        tokenAmount = scaledAmountBase,
                        fiatAmount = amountBase.applyFiatRate(assetBase.asset.token.fiatRate)?.formatFiat(assetBase.asset.token.fiatSymbol),
                    )
                )
            }
            if (amountTarget.compareTo(stateFlow.value.targetAmountInputViewState.tokenAmount) != 0) {
                println("!!! updateAmounts amountTo to $amountTarget")
                val scaledAmountTarget = when {
                    amountTarget.isZero() -> BigDecimal.ZERO
                    else -> amountTarget.setScale(
                        min(MAX_DECIMALS_8, amountTarget.scale()),
                        RoundingMode.DOWN
                    )
                }
                stateFlow.value = stateFlow.value.copy(
                    targetAmountInputViewState = stateFlow.value.targetAmountInputViewState.copy(
                        tokenAmount = scaledAmountTarget,
                        fiatAmount = amountTarget.applyFiatRate(assetTarget.asset.token.fiatRate)?.formatFiat(assetTarget.asset.token.fiatSymbol),
                    )
                )
            }
        }

        updateButtonState()
    }

    private fun updateButtonState() {
        val isButtonEnabled = amountTarget.moreThanZero() && amountTarget.moreThanZero() && stateFlow.value.feeInfo.feeAmount != null
        stateFlow.value = stateFlow.value.copy(
            buttonEnabled = isButtonEnabled
        )
    }

    private val networkFeeFlow = tokensInPoolFlow.map { (baseAsset, targetAsset) ->
        val networkFee = getRemoveLiquidityNetworkFee(
            tokenBase = baseAsset,
            tokenTarget = targetAsset,
        )
        println("!!!! RemoveLiquidity FeeFlow emit $networkFee")
        networkFee
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val utilityAssetFlow = flowOf {
        requireNotNull(chainsRepository.getChain(poolsInteractor.poolsChainId).utilityAsset?.id)
    }.flatMapLatest { utilityAssetId ->
        walletInteractor.assetFlow(poolsInteractor.poolsChainId, utilityAssetId)
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

    private suspend fun getRemoveLiquidityNetworkFee(
        tokenBase: Asset,
        tokenTarget: Asset,
    ): BigDecimal {
        val result = poolsInteractor.calcRemoveLiquidityNetworkFee(
            tokenBase,
            tokenTarget,
        )
        return result ?: BigDecimal.ZERO
    }

    private fun setButtonLoading(loading: Boolean) {
        stateFlow.value = stateFlow.value.copy(
            buttonLoading = loading
        )
    }

    override fun onRemoveReviewClick() {
        setButtonLoading(true)

        coroutinesStore.uiScope.launch {
            val utilityAmount = utilityAssetFlow.firstOrNull()?.transferable ?: return@launch
            val feeAmount = networkFeeFlow.firstOrNull().orZero()

            val poolAssets = assetsInPoolFlow.firstOrNull() ?: return@launch

            val userBasePooled = poolDataReal?.user?.basePooled ?: return@launch
            val userTargetPooled = poolDataReal?.user?.targetPooled ?: return@launch

            val validationResult = validateRemoveLiquidityUseCase(
                utilityAmount = utilityAmount.orZero(),
                userBasePooled = userBasePooled,
                userTargetPooled = userTargetPooled,
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

            val slippage = 0.5

            val firstAmountMin =
                PolkaswapFormulas.calculateMinAmount(
                    amountBase,
                    slippage
                )
            val secondAmountMin =
                PolkaswapFormulas.calculateMinAmount(
                    amountTarget,
                    slippage
                )
            val desired =
                if (percent == 100.0) {
                    poolDataUsable?.user?.poolProvidersBalance.orZero()
                } else {
                    PolkaswapFormulas.calculateAmountByPercentage(
                        poolDataUsable?.user?.poolProvidersBalance.orZero(),
                        percent,
                        poolAssets.first.asset.token.configuration.precision
                    )
                }

            val ids = screenArgsFlow.replayCache.lastOrNull()?.ids ?: return@launch

            internalPoolsRouter.openRemoveLiquidityConfirmScreen(ids, amountBase, amountTarget, firstAmountMin, secondAmountMin, desired)
        }.invokeOnCompletion {
            println("!!! setButtonLoading(false)")
            coroutinesStore.uiScope.launch {
                delay(300)
                setButtonLoading(false)
            }
        }
    }

    override fun onRemoveBaseAmountChange(amount: BigDecimal) {
        println("!!! onRemoveBaseAmountChange(amount = $amount")
        enteredBaseAmountFlow.value = amount
        updateButtonState()
    }

    override fun onRemoveTargetAmountChange(amount: BigDecimal) {
        println("!!! onRemoveToAmountChange(amount = $amount")
        enteredTargetAmountFlow.value = amount
        updateButtonState()
    }

    override fun onRemoveBaseAmountFocusChange(isFocused: Boolean) {
        isBaseAmountFocused.value = isFocused
    }

    override fun onRemoveTargetAmountFocusChange(isFocused: Boolean) {
        isTargetAmountFocused.value = isFocused
    }

    override fun onRemoveItemClick(itemId: Int) {
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