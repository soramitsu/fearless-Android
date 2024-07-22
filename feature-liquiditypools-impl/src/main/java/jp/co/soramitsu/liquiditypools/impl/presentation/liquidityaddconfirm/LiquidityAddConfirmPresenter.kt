package jp.co.soramitsu.liquiditypools.impl.presentation.liquidityaddconfirm

import java.math.BigDecimal
import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.common.compose.component.FeeInfoViewState
import jp.co.soramitsu.common.utils.applyFiatRate
import jp.co.soramitsu.common.utils.flowOf
import jp.co.soramitsu.common.utils.formatCrypto
import jp.co.soramitsu.common.utils.formatCryptoDetail
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.utils.utilityAsset
import jp.co.soramitsu.liquiditypools.domain.interfaces.PoolsInteractor
import jp.co.soramitsu.liquiditypools.impl.presentation.CoroutinesStore
import jp.co.soramitsu.liquiditypools.navigation.InternalPoolsRouter
import jp.co.soramitsu.liquiditypools.navigation.LiquidityPoolsNavGraphRoute
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraMainChainId
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

class LiquidityAddConfirmPresenter @Inject constructor(
    private val coroutinesStore: CoroutinesStore,
    private val internalPoolsRouter: InternalPoolsRouter,
    private val walletInteractor: WalletInteractor,
    private val chainsRepository: ChainsRepository,
    private val poolsInteractor: PoolsInteractor,
    private val accountInteractor: AccountInteractor,
) : LiquidityAddConfirmCallbacks {

    private val _stateSlippage = MutableStateFlow(0.5)
    val stateSlippage = _stateSlippage.asStateFlow()


    private val screenArgsFlow = internalPoolsRouter.createNavGraphRoutesFlow()
        .filterIsInstance<LiquidityPoolsNavGraphRoute.LiquidityAddConfirmScreen>()
        .shareIn(coroutinesStore.uiScope, SharingStarted.Eagerly, 1)

    val assetsInPoolFlow = screenArgsFlow.flatMapLatest { screenArgs ->
        val ids = screenArgs.ids
        val assetsFlow = walletInteractor.assetsFlow().mapNotNull {
            val firstInPair = it.firstOrNull {
                it.asset.token.configuration.id == ids.first
                        && it.asset.token.configuration.chainId == soraMainChainId
            }
            val secondInPair = it.firstOrNull {
                it.asset.token.configuration.id == ids.second
                        && it.asset.token.configuration.chainId == soraMainChainId
            }
            if (firstInPair == null || secondInPair == null) {
                return@mapNotNull null
            } else {
                firstInPair to secondInPair
            }
        }
        assetsFlow
    }

    val tokensInPoolFlow = assetsInPoolFlow.map {
        it.first.asset.token to it.second.asset.token
    }.distinctUntilChanged()

    val isPoolPairEnabled = combine(
        flowOf {
            val chain = accountInteractor.getChain(soraMainChainId)
            val address = accountInteractor.selectedMetaAccount().address(chain)!!
            address
        },
        tokensInPoolFlow
    ) { address, tokens ->
        poolsInteractor.isPairEnabled(
            tokens.first.configuration.currencyId!!,
            tokens.second.configuration.currencyId!!,
            accountAddress = address
        )
    }

    init {

    }
    private val stateFlow = MutableStateFlow(LiquidityAddConfirmState())

    fun createScreenStateFlow(coroutineScope: CoroutineScope): StateFlow<LiquidityAddConfirmState> {
        subscribeState(coroutineScope)
        return stateFlow
    }

    private fun subscribeState(coroutineScope: CoroutineScope) {
        combine(screenArgsFlow, tokensInPoolFlow) { screenArgs, (assetFrom, assetTo) ->
            stateFlow.value = stateFlow.value.copy(
                assetFrom = assetFrom.configuration,
                assetTo = assetTo.configuration,
                baseAmount = screenArgs.amountFrom.formatCrypto(assetFrom.configuration.symbol),
                baseFiat = screenArgs.amountFrom.applyFiatRate(assetFrom.fiatRate)?.formatFiat(assetFrom.fiatSymbol).orEmpty(),
                targetAmount = screenArgs.amountTo.formatCrypto(assetTo.configuration.symbol),
                targetFiat = screenArgs.amountTo.applyFiatRate(assetTo.fiatRate)?.formatFiat(assetTo.fiatSymbol).orEmpty(),
                apy = screenArgs.apy
            )
        }.launchIn(coroutineScope)

        stateSlippage.onEach {
            stateFlow.value = stateFlow.value.copy(
                slippage = "$it%"
            )
        }.launchIn(coroutineScope)

        createFeeInfoViewState().onEach {
            stateFlow.value = stateFlow.value.copy(
                feeInfo = it
            )
        }.launchIn(coroutineScope)

    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun createFeeInfoViewState(): Flow<FeeInfoViewState> {
        val networkFeeHelperFlow = combine(
            screenArgsFlow,
            tokensInPoolFlow,
            stateSlippage,
            isPoolPairEnabled
        )
        { screenArgs, (baseAsset, targetAsset), slippage, pairEnabled ->
            val networkFee = getLiquidityNetworkFee(
                baseAsset.configuration,
                targetAsset.configuration,
                tokenFromAmount = screenArgs.amountFrom,
                tokenToAmount = screenArgs.amountTo,
                pairEnabled = pairEnabled,
                pairPresented = true, //pairPresented,
                slippageTolerance = slippage
            )
            networkFee
        }

        return flowOf {
            requireNotNull(chainsRepository.getChain(soraMainChainId).utilityAsset?.id)
        }.flatMapLatest { utilityAssetId ->
            combine(
                networkFeeHelperFlow,
                walletInteractor.assetFlow(soraMainChainId, utilityAssetId)
            ) { networkFee, utilityAsset ->
                val tokenSymbol = utilityAsset.token.configuration.symbol
                val tokenFiatRate = utilityAsset.token.fiatRate
                val tokenFiatSymbol = utilityAsset.token.fiatSymbol

                return@combine FeeInfoViewState(
                    feeAmount = networkFee.formatCryptoDetail(tokenSymbol),
                    feeAmountFiat = networkFee.applyFiatRate(tokenFiatRate)?.formatFiat(tokenFiatSymbol),
                )
            }
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
        val soraChain = walletInteractor.getChain(soraMainChainId)
        val user = accountInteractor.selectedMetaAccount().address(soraChain).orEmpty()
        val result = poolsInteractor.calcAddLiquidityNetworkFee(
            user,
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

    override fun onNavigationClick() {
        internalPoolsRouter.back()
    }

    override fun onConfirmClick() {
        println("!!! LiquidityAddConfirm onConfirmClick")
        coroutinesStore.uiScope.launch {
            val tokenFrom = tokensInPoolFlow.firstOrNull()?.first?.configuration ?: return@launch
            val tokento = tokensInPoolFlow.firstOrNull()?.second?.configuration ?: return@launch
            val amountFrom = screenArgsFlow.firstOrNull()?.amountFrom.orZero()
            val amountTo = screenArgsFlow.firstOrNull()?.amountTo.orZero()
            val pairEnabled = isPoolPairEnabled.firstOrNull() ?: true
            val pairPresented = true
            var result = ""
            try {
                println("!!! LiquidityAddConfirm onConfirmClick run observeAddLiquidity")
                result = poolsInteractor.observeAddLiquidity(
                    tokenFrom,
                    tokento,
                    amountFrom,
                    amountTo,
                    pairEnabled,
                    pairPresented,
                    _stateSlippage.value,
                )
            } catch (t: Throwable) {
                internalPoolsRouter.openErrorsScreen(message = t.message.orEmpty())
            }

            if (result.isNotEmpty()) {
                println("!!! LiquidityAddConfirm onConfirmClick result = $result")
                // TODO ALL DONE SCREEN
            }
        }
    }

}