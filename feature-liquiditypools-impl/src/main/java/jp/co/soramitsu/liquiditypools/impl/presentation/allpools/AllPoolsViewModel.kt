package jp.co.soramitsu.liquiditypools.impl.presentation.allpools

import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.androidfoundation.format.StringPair
import jp.co.soramitsu.androidfoundation.format.compareNullDesc
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.utils.flowOf
import jp.co.soramitsu.common.utils.formatCrypto
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.liquiditypools.domain.interfaces.PoolsInteractor
import jp.co.soramitsu.liquiditypools.impl.presentation.CoroutinesStore
import jp.co.soramitsu.liquiditypools.impl.presentation.liquidityadd.LiquidityAddCallbacks
import jp.co.soramitsu.liquiditypools.impl.presentation.liquidityadd.LiquidityAddPresenter
import jp.co.soramitsu.liquiditypools.impl.presentation.liquidityadd.LiquidityAddState
import jp.co.soramitsu.liquiditypools.impl.presentation.liquidityaddconfirm.LiquidityAddConfirmCallbacks
import jp.co.soramitsu.liquiditypools.impl.presentation.liquidityaddconfirm.LiquidityAddConfirmPresenter
import jp.co.soramitsu.liquiditypools.impl.presentation.liquidityaddconfirm.LiquidityAddConfirmState
import jp.co.soramitsu.liquiditypools.impl.presentation.pooldetails.PoolDetailsCallbacks
import jp.co.soramitsu.liquiditypools.impl.presentation.pooldetails.PoolDetailsState
import jp.co.soramitsu.liquiditypools.impl.presentation.poollist.PoolListScreenInterface
import jp.co.soramitsu.liquiditypools.impl.presentation.poollist.PoolListState
import jp.co.soramitsu.liquiditypools.navigation.InternalPoolsRouter
import jp.co.soramitsu.liquiditypools.navigation.LiquidityPoolsNavGraphRoute
import jp.co.soramitsu.liquiditypools.navigation.LiquidityPoolsRouter
import jp.co.soramitsu.liquiditypools.navigation.NavAction
import jp.co.soramitsu.polkaswap.api.domain.models.BasicPoolData
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraMainChainId
import jp.co.soramitsu.shared_utils.extensions.fromHex
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.presentation.cross_chain.confirm.GradientIconData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class AllPoolsViewModel @Inject constructor(
    private val poolsInteractor: PoolsInteractor,
    private val coroutinesStore: CoroutinesStore,
    private val poolsRouter: LiquidityPoolsRouter,
    private val innerPoolsRouter: InternalPoolsRouter,
    private val accountInteractor: AccountInteractor,
    private val liquidityAddPresenter: LiquidityAddPresenter,
    liquidityAddConfirmPresenter: LiquidityAddConfirmPresenter,
) : BaseViewModel(), AllPoolsScreenInterface, PoolListScreenInterface, PoolDetailsCallbacks,
    LiquidityAddCallbacks by liquidityAddPresenter,
    LiquidityAddConfirmCallbacks by liquidityAddConfirmPresenter
{

    val navGraphRoutesFlow: StateFlow<LiquidityPoolsNavGraphRoute> =
        innerPoolsRouter.createNavGraphRoutesFlow().stateIn(
            scope = CoroutineScope(Dispatchers.Main.immediate),
            started = SharingStarted.Eagerly,
            initialValue = LiquidityPoolsNavGraphRoute.Loading
        )
    val navGraphActionsFlow: SharedFlow<NavAction> =
        innerPoolsRouter.createNavGraphActionsFlow().shareIn(
            scope = CoroutineScope(Dispatchers.Main.immediate),
            started = SharingStarted.Eagerly,
            replay = 1
        )
    val liquidityAddScreenState: StateFlow<LiquidityAddState> =
        liquidityAddPresenter.createScreenStateFlow(coroutinesStore.uiScope)

    val liquidityAddConfirmState: StateFlow<LiquidityAddConfirmState> =
        liquidityAddConfirmPresenter.createScreenStateFlow(coroutinesStore.uiScope)

    private val enteredAssetQueryFlow = MutableStateFlow("")

    private val _state = MutableStateFlow(AllPoolsState())
    private val _poolListState = MutableStateFlow(PoolListState())
    private val _poolDetailState = MutableStateFlow(PoolDetailsState())
    val pools = combine(
        flowOf { poolsInteractor.getBasicPools() },
        enteredAssetQueryFlow
    ) { pools, query ->
        pools.filter {
            it.baseToken.isMatchFilter(query)
        }.sortedWith { o1, o2 ->
            compareNullDesc(o1.tvl, o2.tvl)
        }
    }.map {
        it.map(BasicPoolData::toListItemState)
    }.share()

    val state = _state.asStateFlow()
    val poolListState = _poolListState.asStateFlow()
    val poolDetailState = _poolDetailState.asStateFlow()

    private val poolDetailsScreenArgsFlow = innerPoolsRouter.createNavGraphRoutesFlow()
        .filterIsInstance<LiquidityPoolsNavGraphRoute.PoolDetailsScreen>()
        .shareIn(this, SharingStarted.Eagerly, 1)

    init {
        subscribeScreenState()
        launch {
            poolsInteractor.updateApy()
        }
        innerPoolsRouter.openAllPoolsScreen()

        liquidityAddConfirmState.onEach {
            println("!!! flow liquidityAddConfirmState: $it")
        }
    }

    private fun Asset.isMatchFilter(filter: String): Boolean =
        this.token.configuration.name?.lowercase()?.contains(filter.lowercase()) == true ||
                this.token.configuration.symbol.lowercase().contains(filter.lowercase()) ||
                this.token.configuration.id.lowercase().contains(filter.lowercase())

    private fun subscribeScreenState() {
        pools.onEach {
            _state.value = _state.value.copy(pools = it)
            _poolListState.value = _poolListState.value.copy(pools = it)
        }.launchIn(this)

        poolDetailsScreenArgsFlow.onEach {
            requestPoolDetails(it.ids)?.let {
                _poolDetailState.value = it
            }
        }.launchIn(this)
    }

    suspend fun requestPoolDetails(ids: StringPair): PoolDetailsState? {
        val soraChain = accountInteractor.getChain(soraMainChainId)
        val address = accountInteractor.selectedMetaAccount().address(soraChain).orEmpty()
        val baseAsset = soraChain.assets.firstOrNull { it.id == ids.first }
        val targetAsset = soraChain.assets.firstOrNull { it.id == ids.second }
        val baseTokenId = baseAsset?.currencyId ?: error("No currency for Asset ${baseAsset?.symbol}")
        val targetTokenId = targetAsset?.currencyId ?: error("No currency for Asset ${targetAsset?.symbol}")

        val retur = poolsInteractor.getUserPoolData(address, baseTokenId, targetTokenId.fromHex())?.let {
            PoolDetailsState(
                originTokenIcon = GradientIconData(baseAsset.iconUrl, null),
                destinationTokenIcon = GradientIconData(targetAsset.iconUrl, null),
                fromTokenSymbol = baseAsset.symbol,
                toTokenSymbol = targetAsset.symbol,
                tvl = null,
                apy = null
            )
        }
        return retur
    }

    override fun onPoolClicked(pair: StringPair) {
        val xorPswap = Pair("b774c386-5cce-454a-a845-1ec0381538ec", "37a999a2-5e90-4448-8b0e-98d06ac8f9d4")
        innerPoolsRouter.openDetailsPoolScreen(xorPswap)
    }

    override fun onNavigationClick() {
        innerPoolsRouter.back()
    }

    override fun onCloseClick() {
        innerPoolsRouter.back()
    }

    override fun onMoreClick() {
        innerPoolsRouter.openPoolListScreen()
    }

    override fun onAssetSearchEntered(value: String) {
        enteredAssetQueryFlow.value = value
    }

    fun onDestinationChanged(route: String) {
        println("!!! onDestinationChanged: $route")
    }

    fun exitFlow() {
        poolsRouter.back()
    }

    override fun onSupplyLiquidityClick() {
        launch {
            poolDetailsScreenArgsFlow.map {
                it.ids
            }.firstOrNull()?.let { ids ->
                innerPoolsRouter.openAddLiquidityScreen(ids)
            }
        }
    }

    override fun onRemoveLiquidityClick() {
        println("!!! onRemoveLiquidityClick")
    }
}

fun BasicPoolData.toListItemState(): BasicPoolListItemState {
    val tvl = this.baseToken.token.fiatRate?.times(BigDecimal(2))
        ?.multiply(this.baseReserves)

    return BasicPoolListItemState(
        ids = StringPair(this.baseToken.token.configuration.id, this.targetToken?.token?.configuration?.id.orEmpty()),  // todo
        token1Icon = this.baseToken.token.configuration.iconUrl,
        token2Icon = this.targetToken?.token?.configuration?.iconUrl.orEmpty(),
        text1 = "${this.baseToken.token.configuration.symbol}-${this.targetToken?.token?.configuration?.symbol}",
        text2 = tvl?.formatFiat().orEmpty(),
        text3 = this.sbapy?.let {
            "%s%%".format(it.toBigDecimal().formatCrypto())
        }.orEmpty(),
    )
}
