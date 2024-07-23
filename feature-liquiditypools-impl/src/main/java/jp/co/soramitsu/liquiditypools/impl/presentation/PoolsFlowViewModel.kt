package jp.co.soramitsu.liquiditypools.impl.presentation

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.androidfoundation.format.StringPair
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.models.TextModel
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.utils.formatCrypto
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.liquiditypools.domain.interfaces.PoolsInteractor
import jp.co.soramitsu.liquiditypools.impl.presentation.allpools.AllPoolsPresenter
import jp.co.soramitsu.liquiditypools.impl.presentation.allpools.AllPoolsScreenInterface
import jp.co.soramitsu.liquiditypools.impl.presentation.allpools.AllPoolsState
import jp.co.soramitsu.liquiditypools.impl.presentation.allpools.BasicPoolListItemState
import jp.co.soramitsu.liquiditypools.impl.presentation.liquidityadd.LiquidityAddCallbacks
import jp.co.soramitsu.liquiditypools.impl.presentation.liquidityadd.LiquidityAddPresenter
import jp.co.soramitsu.liquiditypools.impl.presentation.liquidityadd.LiquidityAddState
import jp.co.soramitsu.liquiditypools.impl.presentation.liquidityaddconfirm.LiquidityAddConfirmCallbacks
import jp.co.soramitsu.liquiditypools.impl.presentation.liquidityaddconfirm.LiquidityAddConfirmPresenter
import jp.co.soramitsu.liquiditypools.impl.presentation.liquidityaddconfirm.LiquidityAddConfirmState
import jp.co.soramitsu.liquiditypools.impl.presentation.pooldetails.PoolDetailsCallbacks
import jp.co.soramitsu.liquiditypools.impl.presentation.pooldetails.PoolDetailsPresenter
import jp.co.soramitsu.liquiditypools.impl.presentation.pooldetails.PoolDetailsState
import jp.co.soramitsu.liquiditypools.impl.presentation.poollist.PoolListPresenter
import jp.co.soramitsu.liquiditypools.impl.presentation.poollist.PoolListScreenInterface
import jp.co.soramitsu.liquiditypools.impl.presentation.poollist.PoolListState
import jp.co.soramitsu.liquiditypools.navigation.InternalPoolsRouter
import jp.co.soramitsu.liquiditypools.navigation.LiquidityPoolsNavGraphRoute
import jp.co.soramitsu.liquiditypools.navigation.LiquidityPoolsRouter
import jp.co.soramitsu.liquiditypools.navigation.NavAction
import jp.co.soramitsu.polkaswap.api.domain.models.BasicPoolData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class PoolsFlowViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    allPoolsPresenter: AllPoolsPresenter,
    poolListPresenter: PoolListPresenter,
    poolDetailsPresenter: PoolDetailsPresenter,
    liquidityAddPresenter: LiquidityAddPresenter,
    liquidityAddConfirmPresenter: LiquidityAddConfirmPresenter,
    private val coroutinesStore: CoroutinesStore,
    private val poolsInteractor: PoolsInteractor,
    private val accountInteractor: AccountInteractor,
    private val internalPoolsRouter: InternalPoolsRouter,
    private val poolsRouter: LiquidityPoolsRouter,
) : BaseViewModel(),
    LiquidityAddCallbacks by liquidityAddPresenter,
    LiquidityAddConfirmCallbacks by liquidityAddConfirmPresenter,
    AllPoolsScreenInterface by allPoolsPresenter,
    PoolListScreenInterface by poolListPresenter,
    PoolDetailsCallbacks by poolDetailsPresenter
{

    val allPoolsScreenState: StateFlow<AllPoolsState> =
        allPoolsPresenter.createScreenStateFlow(coroutinesStore.uiScope)

    val poolListScreenState: StateFlow<PoolListState> =
        poolListPresenter.createScreenStateFlow(coroutinesStore.uiScope)

    val poolDetailsScreenState: StateFlow<PoolDetailsState> =
        poolDetailsPresenter.createScreenStateFlow(coroutinesStore.uiScope)

    val liquidityAddScreenState: StateFlow<LiquidityAddState> =
        liquidityAddPresenter.createScreenStateFlow(coroutinesStore.uiScope)

    val liquidityAddConfirmState: StateFlow<LiquidityAddConfirmState> =
        liquidityAddConfirmPresenter.createScreenStateFlow(coroutinesStore.uiScope)


    val navGraphRoutesFlow: StateFlow<LiquidityPoolsNavGraphRoute> =
        internalPoolsRouter.createNavGraphRoutesFlow().stateIn(
            scope = coroutinesStore.uiScope,
            started = SharingStarted.Eagerly,
            initialValue = LiquidityPoolsNavGraphRoute.Loading
        )
    val navGraphActionsFlow: SharedFlow<NavAction> =
        internalPoolsRouter.createNavGraphActionsFlow().shareIn(
            scope = coroutinesStore.uiScope,
            started = SharingStarted.Eagerly,
            replay = 1
        )

    private val polkaswapChainId = savedStateHandle.get<String>(PoolsFlowFragment.POLKASWAP_CHAIN_ID)
        ?: error("Can't find ${PoolsFlowFragment.POLKASWAP_CHAIN_ID} in arguments")

    init {
        internalPoolsRouter.openAllPoolsScreen(polkaswapChainId)

        launch {
//            poolsInteractor.updateApy()
            poolsInteractor.updatePools(polkaswapChainId)
        }
    }

    private val mutableToolbarStateFlow = MutableStateFlow<LoadingState<TextModel>>(LoadingState.Loading())
    val toolbarStateFlow: StateFlow<LoadingState<TextModel>> = mutableToolbarStateFlow

    fun onDestinationChanged(route: String) {
        val newToolbarState: LoadingState<TextModel> = when (route) {
            LiquidityPoolsNavGraphRoute.Loading.routeName ->
                LoadingState.Loading()

            LiquidityPoolsNavGraphRoute.AllPoolsScreen.routeName ->
                LoadingState.Loaded(
                    TextModel.SimpleString("All pools")
                )

            LiquidityPoolsNavGraphRoute.ListPoolsScreen.routeName -> {
//                val destinationArgs = innternalPoolsRouter.destination(LiquidityPoolsNavGraphRoute.ListPoolsScreen::class.java)
//                val title = destinationArgs?.token?.title.orEmpty()

                LoadingState.Loaded(
                    TextModel.SimpleString("Your pools")
                )
            }

            LiquidityPoolsNavGraphRoute.PoolDetailsScreen.routeName ->
                LoadingState.Loaded(
                    TextModel.SimpleString("Pools details")
                )

            LiquidityPoolsNavGraphRoute.LiquidityAddScreen.routeName ->
                LoadingState.Loaded(
                    TextModel.SimpleString("Supply liquidity")
                )

            else -> LoadingState.Loading()
        }

        mutableToolbarStateFlow.value = newToolbarState
    }

    override fun onPoolClicked(pair: StringPair) {
//        val xorPswap = Pair("b774c386-5cce-454a-a845-1ec0381538ec", "37a999a2-5e90-4448-8b0e-98d06ac8f9d4")
        internalPoolsRouter.openDetailsPoolScreen(polkaswapChainId, pair)
    }

    fun onNavigationClick() {
        internalPoolsRouter.back()
    }

    fun exitFlow() {
        poolsRouter.back()
    }
}

fun BasicPoolData.toListItemState(): BasicPoolListItemState? {
    val tvl = this.baseToken.token.fiatRate?.times(BigDecimal(2))
        ?.multiply(this.baseReserves)

    val baseTokenId = this.baseToken.token.configuration.currencyId ?: return null
    val targetTokenId = this.targetToken?.token?.configuration?.currencyId ?: return null

    return BasicPoolListItemState(
        ids = StringPair(baseTokenId, targetTokenId),
        token1Icon = this.baseToken.token.configuration.iconUrl,
        token2Icon = this.targetToken?.token?.configuration?.iconUrl.orEmpty(),
        text1 = "${this.baseToken.token.configuration.symbol}-${this.targetToken?.token?.configuration?.symbol}".uppercase(),
        text2 = tvl?.formatFiat(this.baseToken.token.fiatSymbol).orEmpty(),
        text3 = this.sbapy?.let {
            "%s%%".format(it.toBigDecimal().formatCrypto())
        }.orEmpty(),
        text4 = "Earn PSWAP"
    )
}
