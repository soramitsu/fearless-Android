package jp.co.soramitsu.liquiditypools.impl.presentation.liquidityaddconfirm

import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.common.compose.component.FeeInfoViewState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.applyFiatRate
import jp.co.soramitsu.common.utils.flowOf
import jp.co.soramitsu.common.utils.formatCrypto
import jp.co.soramitsu.common.utils.formatCryptoDetail
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.utils.utilityAsset
import jp.co.soramitsu.feature_liquiditypools_impl.R
import jp.co.soramitsu.liquiditypools.domain.interfaces.PoolsInteractor
import jp.co.soramitsu.liquiditypools.impl.presentation.CoroutinesStore
import jp.co.soramitsu.liquiditypools.navigation.InternalPoolsRouter
import jp.co.soramitsu.liquiditypools.navigation.LiquidityPoolsNavGraphRoute
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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
import java.math.BigDecimal
import javax.inject.Inject

class LiquidityAddConfirmPresenter @Inject constructor(
    private val coroutinesStore: CoroutinesStore,
    private val internalPoolsRouter: InternalPoolsRouter,
    private val walletInteractor: WalletInteractor,
    private val chainsRepository: ChainsRepository,
    private val poolsInteractor: PoolsInteractor,
    private val accountInteractor: AccountInteractor,
    private val resourceManager: ResourceManager,
) : LiquidityAddConfirmCallbacks {

    private val _stateSlippage = MutableStateFlow(0.5)
    val stateSlippage = _stateSlippage.asStateFlow()

    private val screenArgsFlow = internalPoolsRouter.createNavGraphRoutesFlow()
        .filterIsInstance<LiquidityPoolsNavGraphRoute.LiquidityAddConfirmScreen>()
        .shareIn(coroutinesStore.uiScope, SharingStarted.Eagerly, 1)

    val assetsInPoolFlow = screenArgsFlow.flatMapLatest { screenArgs ->
        val ids = screenArgs.ids
        val chainId = poolsInteractor.poolsChainId
        val assetsFlow = walletInteractor.assetsFlow().mapNotNull {
            val firstInPair = it.firstOrNull {
                it.asset.token.configuration.currencyId == ids.first &&
                        it.asset.token.configuration.chainId == chainId
            }
            val secondInPair = it.firstOrNull {
                it.asset.token.configuration.currencyId == ids.second &&
                        it.asset.token.configuration.chainId == chainId
            }
            if (firstInPair == null || secondInPair == null) {
                return@mapNotNull null
            } else {
                firstInPair to secondInPair
            }
        }
        assetsFlow
    }

    private val tokensInPoolFlow = assetsInPoolFlow.map {
        it.first.asset.token to it.second.asset.token
    }.distinctUntilChanged()

    private val isPoolPairEnabled =
        screenArgsFlow.map { screenArgs ->
            poolsInteractor.isPairEnabled(
                baseTokenId = screenArgs.ids.first,
                targetTokenId = screenArgs.ids.second
            )
        }

    private val stateFlow = MutableStateFlow(LiquidityAddConfirmState())

    fun createScreenStateFlow(coroutineScope: CoroutineScope): StateFlow<LiquidityAddConfirmState> {
        subscribeState(coroutineScope)
        return stateFlow
    }

    private fun subscribeState(coroutineScope: CoroutineScope) {
        combine(screenArgsFlow, tokensInPoolFlow) { screenArgs, (assetBase, assetTarget) ->
            stateFlow.value = stateFlow.value.copy(
                assetBase = assetBase.configuration,
                assetTarget = assetTarget.configuration,
                baseAmount = screenArgs.amountBase.formatCrypto(assetBase.configuration.symbol),
                targetAmount = screenArgs.amountTarget.formatCrypto(assetTarget.configuration.symbol),
                apy = screenArgs.apy
            )
        }.launchIn(coroutineScope)

        stateSlippage.onEach {
            stateFlow.value = stateFlow.value.copy(
                slippage = "$it%"
            )
        }.launchIn(coroutineScope)

        feeInfoViewStateFlow.onEach {
            stateFlow.value = stateFlow.value.copy(
                feeInfo = it,
                buttonEnabled = it.feeAmount.isNullOrEmpty().not()
            )
        }.launchIn(coroutineScope)
    }

    val networkFeeFlow = combine(
        screenArgsFlow,
        tokensInPoolFlow,
        stateSlippage,
        isPoolPairEnabled
    ) { screenArgs, (baseAsset, targetAsset), slippage, pairEnabled ->
        getLiquidityNetworkFee(
            tokenBase = baseAsset.configuration,
            tokenTarget = targetAsset.configuration,
            tokenBaseAmount = screenArgs.amountBase,
            tokenTargetAmount = screenArgs.amountTarget,
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

    private suspend fun getLiquidityNetworkFee(
        tokenBase: Asset,
        tokenTarget: Asset,
        tokenBaseAmount: BigDecimal,
        tokenTargetAmount: BigDecimal,
        pairEnabled: Boolean,
        pairPresented: Boolean,
        slippageTolerance: Double
    ): BigDecimal {
        val chainId = poolsInteractor.poolsChainId
        val soraChain = walletInteractor.getChain(chainId)
        val user = accountInteractor.selectedMetaAccount().address(soraChain).orEmpty()
        val result = poolsInteractor.calcAddLiquidityNetworkFee(
            chainId = chainId,
            address = user,
            tokenBase = tokenBase,
            tokenTarget = tokenTarget,
            tokenBaseAmount = tokenBaseAmount,
            tokenTargetAmount = tokenTargetAmount,
            pairEnabled = pairEnabled,
            pairPresented = pairPresented,
            slippageTolerance = slippageTolerance,
        )
        return result ?: BigDecimal.ZERO
    }

    override fun onConfirmClick() {
        setButtonLoading(true)
        coroutinesStore.ioScope.launch {
            val chainId = poolsInteractor.poolsChainId
            val tokenBase = tokensInPoolFlow.firstOrNull()?.first?.configuration ?: return@launch
            val tokenTarget = tokensInPoolFlow.firstOrNull()?.second?.configuration ?: return@launch
            val amountBase = screenArgsFlow.firstOrNull()?.amountBase.orZero()
            val amountTarget = screenArgsFlow.firstOrNull()?.amountTarget.orZero()
            val pairEnabled = isPoolPairEnabled.firstOrNull() ?: true
            var result = ""
            try {
                result = poolsInteractor.observeAddLiquidity(
                    chainId = chainId,
                    tokenBase = tokenBase,
                    tokenTarget = tokenTarget,
                    amountBase = amountBase,
                    amountTarget = amountTarget,
                    enabled = pairEnabled,
                    presented = true,
                    slippageTolerance = _stateSlippage.value,
                )
            } catch (t: Throwable) {
                coroutinesStore.uiScope.launch {
                    internalPoolsRouter.openErrorsScreen(message = t.message.orEmpty())
                }
            }

            if (result.isNotEmpty()) {
                coroutinesStore.uiScope.launch {
//                    internalPoolsRouter.popupToScreen(LiquidityPoolsNavGraphRoute.PoolDetailsScreen)
                    internalPoolsRouter.back()
                    internalPoolsRouter.back()
                    internalPoolsRouter.back()
                    internalPoolsRouter.openSuccessScreen(result, chainId, resourceManager.getString(R.string.lp_liquidity_add_complete_text))
                }
            }
        }.invokeOnCompletion {
            coroutinesStore.ioScope.launch {
                delay(700)
                poolsInteractor.updateAccountPools()
            }

            coroutinesStore.uiScope.launch {
                delay(300)
                setButtonLoading(false)
            }
        }
    }

    override fun onAddItemClick(itemId: Int) {
        internalPoolsRouter.openInfoScreen(itemId)
    }

    private fun setButtonLoading(loading: Boolean) {
        stateFlow.value = stateFlow.value.copy(
            buttonLoading = loading
        )
    }
}
