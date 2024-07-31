package jp.co.soramitsu.liquiditypools.impl.presentation.pooldetails

import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.androidfoundation.format.StringPair
import jp.co.soramitsu.common.utils.applyFiatRate
import jp.co.soramitsu.common.utils.formatCrypto
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.common.utils.formatPercent
import jp.co.soramitsu.liquiditypools.domain.interfaces.PoolsInteractor
import jp.co.soramitsu.liquiditypools.impl.presentation.CoroutinesStore
import jp.co.soramitsu.liquiditypools.navigation.InternalPoolsRouter
import jp.co.soramitsu.liquiditypools.navigation.LiquidityPoolsNavGraphRoute
import jp.co.soramitsu.polkaswap.api.domain.models.CommonPoolData
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.shared_utils.extensions.fromHex
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch

class PoolDetailsPresenter @Inject constructor(
    private val coroutinesStore: CoroutinesStore,
    private val internalPoolsRouter: InternalPoolsRouter,
    private val walletInteractor: WalletInteractor,
    private val chainsRepository: ChainsRepository,
    private val poolsInteractor: PoolsInteractor,
    private val accountInteractor: AccountInteractor,
) : PoolDetailsCallbacks {

    private val screenArgsFlow = internalPoolsRouter.createNavGraphRoutesFlow()
        .filterIsInstance<LiquidityPoolsNavGraphRoute.PoolDetailsScreen>()
        .shareIn(coroutinesStore.uiScope, SharingStarted.Eagerly, 1)


    init {

    }

    private val stateFlow = MutableStateFlow(PoolDetailsState())

    fun createScreenStateFlow(coroutineScope: CoroutineScope): StateFlow<PoolDetailsState> {
        subscribeState(coroutineScope)
        return stateFlow
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun subscribeState(coroutineScope: CoroutineScope) {
        screenArgsFlow.flatMapLatest {
            observePoolDetails(it.chainId, it.ids).onEach {
                stateFlow.value = it.mapToState()
            }
//            requestPoolDetails(it.ids)?.let {
//                stateFlow.value = it
//            }
        }.launchIn(coroutineScope)
    }


    override fun onSupplyLiquidityClick() {
        coroutinesStore.ioScope.launch {
            val ids = screenArgsFlow.replayCache.firstOrNull()?.ids ?: return@launch
            val chainId = screenArgsFlow.replayCache.firstOrNull()?.chainId  ?: return@launch
            internalPoolsRouter.openAddLiquidityScreen(chainId, ids)
        }
    }

    override fun onRemoveLiquidityClick() {
        coroutinesStore.ioScope.launch {
            val ids = screenArgsFlow.replayCache.firstOrNull()?.ids ?: return@launch
            val chainId = screenArgsFlow.replayCache.firstOrNull()?.chainId  ?: return@launch
            internalPoolsRouter.openRemoveLiquidityScreen(chainId, ids)
        }

    }

    override fun onDetailItemClick(itemId: Int) {
        internalPoolsRouter.openInfoScreen(itemId)
    }

    suspend fun observePoolDetails(chainId: ChainId, ids: StringPair): Flow<CommonPoolData> {
        val (baseTokenId, targetTokenId) = ids
        return poolsInteractor.getPoolData(chainId, baseTokenId, targetTokenId)
    }

    suspend fun requestPoolDetails(ids: StringPair): PoolDetailsState? {
        val chainId = screenArgsFlow.replayCache.firstOrNull()?.chainId ?: return null

        val soraChain = accountInteractor.getChain(chainId)
        val address = accountInteractor.selectedMetaAccount().address(soraChain).orEmpty()
        val baseAsset = soraChain.assets.firstOrNull { it.id == ids.first }
        val targetAsset = soraChain.assets.firstOrNull { it.id == ids.second }
        val baseTokenId = baseAsset?.currencyId ?: error("No currency for Asset ${baseAsset?.symbol}")
        val targetTokenId = targetAsset?.currencyId ?: error("No currency for Asset ${targetAsset?.symbol}")

        val result = poolsInteractor.getUserPoolData(chainId, address, baseTokenId, targetTokenId.fromHex())?.let {
            PoolDetailsState(
                assetBase = baseAsset,
                assetTarget = targetAsset,
                tvl = null,
                apy = null
            )
        }
        return result
    }
}

private fun CommonPoolData.mapToState(): PoolDetailsState {
    return PoolDetailsState(
        assetBase = basic.baseToken.token.configuration,
        assetTarget = basic.targetToken?.token?.configuration,
        pooledBaseAmount = user?.basePooled?.formatCrypto(basic.baseToken.token.configuration.symbol).orEmpty(),
        pooledBaseFiat = user?.basePooled?.applyFiatRate(basic.baseToken.token.fiatRate)?.formatFiat(basic.baseToken.token.fiatSymbol).orEmpty(),
        pooledTargetAmount = user?.targetPooled?.formatCrypto(basic.targetToken?.token?.configuration?.symbol).orEmpty(),
        pooledTargetFiat = user?.targetPooled?.applyFiatRate(basic.targetToken?.token?.fiatRate)?.formatFiat(basic.targetToken?.token?.fiatSymbol).orEmpty(),
        tvl = basic.tvl?.formatFiat(basic.baseToken.token.fiatSymbol),
        apy = "${basic.sbapy?.toBigDecimal()?.formatPercent()}%"
    )
}
