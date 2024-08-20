package jp.co.soramitsu.liquiditypools.impl.presentation.liquidityremove

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
import jp.co.soramitsu.liquiditypools.domain.model.CommonUserPoolData
import jp.co.soramitsu.liquiditypools.impl.presentation.CoroutinesStore
import jp.co.soramitsu.liquiditypools.impl.usecase.ValidateRemoveLiquidityUseCase
import jp.co.soramitsu.liquiditypools.impl.util.PolkaswapFormulas
import jp.co.soramitsu.liquiditypools.navigation.InternalPoolsRouter
import jp.co.soramitsu.liquiditypools.navigation.LiquidityPoolsNavGraphRoute
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.wallet.api.domain.fromValidationResult
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject
import kotlin.math.min

@OptIn(FlowPreview::class)
@Suppress("LargeClass")
class LiquidityRemovePresenter @Inject constructor(
    private val coroutinesStore: CoroutinesStore,
    private val internalPoolsRouter: InternalPoolsRouter,
    private val walletInteractor: WalletInteractor,
    private val chainsRepository: ChainsRepository,
    private val poolsInteractor: PoolsInteractor,
    private val demeterFarmingInteractor: DemeterFarmingInteractor,
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

    private val baseToTargetTokensFlow = screenArgsFlow.mapNotNull { screenArgs ->
        val currencyIds = screenArgs.ids
        val chainId = poolsInteractor.poolsChainId

        val chain = chainsRepository.getChain(chainId)
        val first = chain.assets.find { it.currencyId == currencyIds.first } ?: return@mapNotNull null
        val second = chain.assets.find { it.currencyId == currencyIds.second } ?: return@mapNotNull null

        coroutineScope {
            val baseTokenDeferred = async { walletInteractor.getToken(first) }
            val targetTokenDeferred = async { walletInteractor.getToken(second) }

            baseTokenDeferred.await() to targetTokenDeferred.await()
        }
    }.distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    val poolDataFlow = screenArgsFlow.flatMapLatest {
        poolsInteractor.getPoolData(
            baseTokenId = it.ids.first,
            targetTokenId = it.ids.second
        )
    }

    private val networkFeeFlow = baseToTargetTokensFlow.map { (baseToken, targetToken) ->
        getRemoveLiquidityNetworkFee(
            tokenBase = baseToken.configuration,
            tokenTarget = targetToken.configuration,
        )
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

    private val stateFlow = MutableStateFlow(LiquidityRemoveState())

    init {
        coroutinesStore.ioScope.launch {
            poolDataFlow.map { data ->
                data.user?.let {
                    CommonUserPoolData(
                        data.basic,
                        data.user!!,
                    )
                }
            }
                .catch { showError(it) }
                .distinctUntilChanged()
                .debounce(DEBOUNCE_500)
                .map { poolDataLocal ->
                    poolDataReal = poolDataLocal
                    poolInFarming = false

                    val ids = screenArgsFlow.replayCache.firstOrNull()?.ids ?: return@map null
                    val (token1Id, token2Id) = ids

                    val chainId = poolsInteractor.poolsChainId
                    val result = if (poolDataLocal != null) {
                        val maxPercent = demeterFarmingInteractor.getFarmedPools(chainId)?.filter { pool ->
                            pool.tokenBase.token.configuration.currencyId == token1Id &&
                                    pool.tokenTarget.token.configuration.currencyId == token2Id
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
                                        poolDataLocal.basic.baseToken.precision,
                                    ),
                                    targetPooled = PolkaswapFormulas.calculateAmountByPercentage(
                                        poolDataLocal.user.targetPooled,
                                        usablePercent,
                                        poolDataLocal.basic.baseToken.precision,
                                    ),
                                    poolProvidersBalance = PolkaswapFormulas.calculateAmountByPercentage(
                                        poolDataLocal.user.poolProvidersBalance,
                                        usablePercent,
                                        poolDataLocal.basic.baseToken.precision,
                                    ),
                                )
                            )
                        } else {
                            poolDataLocal
                        }
                    } else {
                        null
                    }
                    result
                }
                .collectLatest { poolDataLocal ->
                    poolDataUsable = poolDataLocal
                    amountBase =
                        if (poolDataLocal != null) {
                            PolkaswapFormulas.calculateAmountByPercentage(
                            poolDataLocal.user.basePooled,
                            percent,
                            poolDataLocal.basic.baseToken.precision,
                        )
                        } else {
                            BigDecimal.ZERO
                        }
                    amountTarget =
                        if (poolDataLocal != null) {
                            PolkaswapFormulas.calculateAmountByPercentage(
                            poolDataLocal.user.targetPooled,
                            percent,
                            poolDataLocal.basic.targetToken?.precision!!,
                        )
                        } else {
                            BigDecimal.ZERO
                        }

                    coroutinesStore.ioScope.launch {
                        updateAmounts()
                    }
                }
        }
    }

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

    @OptIn(FlowPreview::class)
    private fun subscribeState(coroutineScope: CoroutineScope) {
        poolDataFlow.onEach { pool ->
            val (baseToken, targetToken) = baseToTargetTokensFlow.first()

            val pooledBaseCrypto = pool.user?.basePooled?.formatCrypto(baseToken.configuration.symbol).orEmpty()
            val pooledBaseFiat = pool.user?.basePooled?.applyFiatRate(baseToken.fiatRate)?.formatFiat(baseToken.fiatSymbol)
            val argsBase = pooledBaseCrypto + pooledBaseFiat?.let { " ($it)" }.orEmpty()
            val pooledBaseBalance = resourceManager.getString(R.string.common_available_format, argsBase)

            val pooledTargetCrypto = pool.user?.targetPooled?.formatCrypto(targetToken.configuration.symbol).orEmpty()
            val pooledTargetFiat = pool.user?.targetPooled?.applyFiatRate(targetToken.fiatRate)?.formatFiat(targetToken.fiatSymbol)
            val argsTarget = pooledTargetCrypto + pooledTargetFiat?.let { " ($it)" }.orEmpty()
            val pooledTargetBalance = resourceManager.getString(R.string.common_available_format, argsTarget)

            stateFlow.value = stateFlow.value.copy(
                baseAmountInputViewState = stateFlow.value.baseAmountInputViewState.copy(
                    tokenName = baseToken.configuration.symbol,
                    tokenImage = baseToken.configuration.iconUrl,
                    totalBalance = pooledBaseBalance,
                ),
                targetAmountInputViewState = stateFlow.value.targetAmountInputViewState.copy(
                    tokenName = targetToken.configuration.symbol,
                    tokenImage = targetToken.configuration.iconUrl,
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
            val (baseToken, _) = baseToTargetTokensFlow.first()

            stateFlow.value = stateFlow.value.copy(
                baseAmountInputViewState = stateFlow.value.baseAmountInputViewState.copy(
                    fiatAmount = it.applyFiatRate(baseToken.fiatRate)?.formatFiat(baseToken.fiatSymbol),
                    tokenAmount = it,
                )
            )
        }
            .debounce(INPUT_DEBOUNCE)
            .onEach { amount ->
                poolDataUsable?.let {
                    amountBase = if (it.user.basePooled <= amount) amount else it.user.basePooled

                    val precisionTo = poolDataFlow.firstOrNull()?.basic?.targetToken?.precision
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
            val (_, targetToken) = baseToTargetTokensFlow.first()

            stateFlow.value = stateFlow.value.copy(
                targetAmountInputViewState = stateFlow.value.targetAmountInputViewState.copy(
                    fiatAmount = it.applyFiatRate(targetToken.fiatRate)?.formatFiat(targetToken.fiatSymbol),
                    tokenAmount = it
                ),
            )
        }
            .debounce(INPUT_DEBOUNCE)
            .onEach { amount ->
                poolDataUsable?.let {
                    amountTarget = if (amount <= it.user.targetPooled) amount else it.user.targetPooled

                    val precisionBase = poolDataFlow.firstOrNull()?.basic?.baseToken?.precision
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

    fun createScreenStateFlow(coroutineScope: CoroutineScope): StateFlow<LiquidityRemoveState> {
        subscribeState(coroutineScope)
        return stateFlow
    }

    @Suppress("NestedBlockDepth")
    private suspend fun updateAmounts() {
        baseToTargetTokensFlow.firstOrNull()?.let { (tokenBase, tokenTarget) ->
            if (amountBase.compareTo(stateFlow.value.baseAmountInputViewState.tokenAmount) != 0) {
                val scaledAmountBase = if (amountBase.isZero()) {
                    BigDecimal.ZERO
                } else {
                    amountBase.setScale(
                        min(MAX_DECIMALS_8, amountBase.scale()),
                        RoundingMode.DOWN
                    )
                }

                stateFlow.value = stateFlow.value.copy(
                    baseAmountInputViewState = stateFlow.value.baseAmountInputViewState.copy(
                        tokenAmount = scaledAmountBase,
                        fiatAmount = amountBase.applyFiatRate(tokenBase.fiatRate)?.formatFiat(tokenTarget.fiatSymbol),
                    )
                )
            }
            if (amountTarget.compareTo(stateFlow.value.targetAmountInputViewState.tokenAmount) != 0) {
                val scaledAmountTarget = if (amountTarget.isZero()) {
                    BigDecimal.ZERO
                } else {
                    amountTarget.setScale(
                        min(MAX_DECIMALS_8, amountTarget.scale()),
                        RoundingMode.DOWN
                    )
                }
                stateFlow.value = stateFlow.value.copy(
                    targetAmountInputViewState = stateFlow.value.targetAmountInputViewState.copy(
                        tokenAmount = scaledAmountTarget,
                        fiatAmount = amountTarget.applyFiatRate(tokenTarget.fiatRate)?.formatFiat(tokenTarget.fiatSymbol),
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

    private suspend fun getRemoveLiquidityNetworkFee(tokenBase: Asset, tokenTarget: Asset): BigDecimal {
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

            val poolAssets = baseToTargetTokensFlow.first()

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

            val slippage = DEFAULT_SLIPPAGE

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
                        poolAssets.first.configuration.precision
                    )
                }

            val ids = screenArgsFlow.replayCache.lastOrNull()?.ids ?: return@launch

            internalPoolsRouter.openRemoveLiquidityConfirmScreen(ids, amountBase, amountTarget, firstAmountMin, secondAmountMin, desired)
        }.invokeOnCompletion {
            coroutinesStore.uiScope.launch {
                delay(DEBOUNCE_300)
                setButtonLoading(false)
            }
        }
    }

    override fun onRemoveBaseAmountChange(amount: BigDecimal) {
        enteredBaseAmountFlow.value = amount
        updateButtonState()
    }

    override fun onRemoveTargetAmountChange(amount: BigDecimal) {
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
        if (throwable is ValidationException) {
            val (title, text) = throwable
            internalPoolsRouter.openErrorsScreen(title, text)
        } else {
            throwable.message?.let { internalPoolsRouter.openErrorsScreen(message = it) }
        }
    }

    companion object {
        private const val INPUT_DEBOUNCE = 900L
        private const val DEBOUNCE_300 = 300L
        private const val DEBOUNCE_500 = 500L
        private const val DEFAULT_SLIPPAGE = 0.5
    }
}
