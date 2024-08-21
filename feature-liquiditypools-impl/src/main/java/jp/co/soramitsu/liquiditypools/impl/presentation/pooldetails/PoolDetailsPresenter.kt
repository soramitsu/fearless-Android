package jp.co.soramitsu.liquiditypools.impl.presentation.pooldetails

import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.androidfoundation.format.StringPair
import jp.co.soramitsu.common.utils.applyFiatRate
import jp.co.soramitsu.common.utils.formatCrypto
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.common.utils.formatPercent
import jp.co.soramitsu.liquiditypools.domain.interfaces.PoolsInteractor
import jp.co.soramitsu.liquiditypools.domain.model.CommonPoolData
import jp.co.soramitsu.liquiditypools.impl.presentation.CoroutinesStore
import jp.co.soramitsu.liquiditypools.navigation.InternalPoolsRouter
import jp.co.soramitsu.liquiditypools.navigation.LiquidityPoolsNavGraphRoute
import jp.co.soramitsu.shared_utils.extensions.fromHex
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.domain.model.Token
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

class PoolDetailsPresenter @Inject constructor(
    private val coroutinesStore: CoroutinesStore,
    private val internalPoolsRouter: InternalPoolsRouter,
    private val walletInteractor: WalletInteractor,
    private val poolsInteractor: PoolsInteractor,
    private val accountInteractor: AccountInteractor,
) : PoolDetailsCallbacks {

    private val screenArgsFlow = internalPoolsRouter.createNavGraphRoutesFlow()
        .filterIsInstance<LiquidityPoolsNavGraphRoute.PoolDetailsScreen>()
        .shareIn(coroutinesStore.uiScope, SharingStarted.Eagerly, 1)

    private val stateFlow = MutableStateFlow(PoolDetailsState())

    fun createScreenStateFlow(coroutineScope: CoroutineScope): StateFlow<PoolDetailsState> {
        subscribeState(coroutineScope)
        return stateFlow
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun subscribeState(coroutineScope: CoroutineScope) {
        screenArgsFlow.flatMapLatest { args ->
            observePoolDetails(args.ids).onEach { pool ->
                val token = walletInteractor.getToken(pool.basic.baseToken)
                stateFlow.value = pool.mapToState(token)
            }
        }.onEach {
            val apy = poolsInteractor.getSbApy(it.basic.reserveAccount)
            stateFlow.update { prevState ->
                prevState.copy(apy = "${apy?.toBigDecimal()?.formatPercent()}%")
            }
        }.launchIn(coroutineScope)
    }

    override fun onSupplyLiquidityClick() {
        coroutinesStore.ioScope.launch {
            val ids = screenArgsFlow.replayCache.firstOrNull()?.ids ?: return@launch
            internalPoolsRouter.openAddLiquidityScreen(ids)
        }
    }

    override fun onRemoveLiquidityClick() {
        coroutinesStore.ioScope.launch {
            val ids = screenArgsFlow.replayCache.firstOrNull()?.ids ?: return@launch
            internalPoolsRouter.openRemoveLiquidityScreen(ids)
        }
    }

    override fun onDetailItemClick(itemId: Int) {
        internalPoolsRouter.openInfoScreen(itemId)
    }

    suspend fun observePoolDetails(ids: StringPair): Flow<CommonPoolData> {
        val (baseTokenId, targetTokenId) = ids
        return poolsInteractor.getPoolData(baseTokenId, targetTokenId)
    }

    suspend fun requestPoolDetails(ids: StringPair): PoolDetailsState? {
        val chainId = poolsInteractor.poolsChainId

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

private fun CommonPoolData.mapToState(token: Token): PoolDetailsState {
    val tvl = basic.getTvl(token.fiatRate)
    return PoolDetailsState(
        assetBase = basic.baseToken,
        assetTarget = basic.targetToken,
        pooledBaseAmount = user?.basePooled?.formatCrypto(basic.baseToken.symbol).orEmpty(),
        pooledBaseFiat = user?.basePooled?.applyFiatRate(token.fiatRate)?.formatFiat(token.fiatSymbol).orEmpty(),
        pooledTargetAmount = user?.targetPooled?.formatCrypto(basic.targetToken?.symbol).orEmpty(),
        pooledTargetFiat = user?.targetPooled?.applyFiatRate(token.fiatRate)?.formatFiat(token.fiatSymbol).orEmpty(),
        tvl = tvl?.formatFiat(token.fiatSymbol),
        apy = null
    )
}
