package jp.co.soramitsu.liquiditypools.impl.presentation

import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.androidfoundation.format.StringPair
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.models.TextModel
import jp.co.soramitsu.common.compose.theme.greenText
import jp.co.soramitsu.common.compose.theme.white50
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.utils.formatCrypto
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.feature_liquiditypools_impl.R
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
import jp.co.soramitsu.liquiditypools.impl.presentation.liquidityremove.LiquidityRemoveCallbacks
import jp.co.soramitsu.liquiditypools.impl.presentation.liquidityremove.LiquidityRemovePresenter
import jp.co.soramitsu.liquiditypools.impl.presentation.liquidityremove.LiquidityRemoveState
import jp.co.soramitsu.liquiditypools.impl.presentation.liquidityremoveconfirm.LiquidityRemoveConfirmCallbacks
import jp.co.soramitsu.liquiditypools.impl.presentation.liquidityremoveconfirm.LiquidityRemoveConfirmPresenter
import jp.co.soramitsu.liquiditypools.impl.presentation.liquidityremoveconfirm.LiquidityRemoveConfirmState
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
import jp.co.soramitsu.polkaswap.api.domain.models.CommonPoolData
import jp.co.soramitsu.wallet.impl.domain.model.Token
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

@HiltViewModel
class PoolsFlowViewModel @Inject constructor(
    allPoolsPresenter: AllPoolsPresenter,
    poolListPresenter: PoolListPresenter,
    poolDetailsPresenter: PoolDetailsPresenter,
    liquidityAddPresenter: LiquidityAddPresenter,
    liquidityAddConfirmPresenter: LiquidityAddConfirmPresenter,
    liquidityRemovePresenter: LiquidityRemovePresenter,
    liquidityRemoveConfirmPresenter: LiquidityRemoveConfirmPresenter,
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
    PoolDetailsCallbacks by poolDetailsPresenter,
    LiquidityRemoveCallbacks by liquidityRemovePresenter,
    LiquidityRemoveConfirmCallbacks by liquidityRemoveConfirmPresenter
{

    companion object {
        const val ITEM_APY_ID = 1
        const val ITEM_FEE_ID = 2
    }

    val allPoolsScreenState: StateFlow<AllPoolsState> =
        allPoolsPresenter.createScreenStateFlow()

    val poolListScreenState: StateFlow<PoolListState> =
        poolListPresenter.createScreenStateFlow(coroutinesStore.uiScope)

    val poolDetailsScreenState: StateFlow<PoolDetailsState> =
        poolDetailsPresenter.createScreenStateFlow(coroutinesStore.uiScope)

    val liquidityAddScreenState: StateFlow<LiquidityAddState> =
        liquidityAddPresenter.createScreenStateFlow(coroutinesStore.uiScope)

    val liquidityRemoveScreenState: StateFlow<LiquidityRemoveState> =
        liquidityRemovePresenter.createScreenStateFlow(coroutinesStore.uiScope)

    val liquidityAddConfirmState: StateFlow<LiquidityAddConfirmState> =
        liquidityAddConfirmPresenter.createScreenStateFlow(coroutinesStore.uiScope)

    val liquidityRemoveConfirmState: StateFlow<LiquidityRemoveConfirmState> =
        liquidityRemoveConfirmPresenter.createScreenStateFlow(coroutinesStore.uiScope)


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

    init {
        internalPoolsRouter.openAllPoolsScreen()

        launch {
            poolsInteractor.syncPools(poolsInteractor.poolsChainId)
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
                    TextModel.SimpleString("")
                )

            LiquidityPoolsNavGraphRoute.ListPoolsScreen.routeName -> {
                val destinationArgs = internalPoolsRouter.destination(LiquidityPoolsNavGraphRoute.ListPoolsScreen::class.java)
                val titleId = if (destinationArgs?.isUserPools == true) {
                    R.string.pl_user_pools
                } else {
                    R.string.pl_available_pools
                }

                LoadingState.Loaded(
                    TextModel.ResId(titleId)
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

            LiquidityPoolsNavGraphRoute.LiquidityAddConfirmScreen.routeName ->
                LoadingState.Loaded(
                    TextModel.SimpleString("Confirm liquidity")
                )

            LiquidityPoolsNavGraphRoute.LiquidityRemoveScreen.routeName ->
                LoadingState.Loaded(
                    TextModel.SimpleString("Remove liquidity")
                )

            LiquidityPoolsNavGraphRoute.LiquidityRemoveConfirmScreen.routeName ->
                LoadingState.Loaded(
                    TextModel.SimpleString("Remove liquidity")
                )

            else -> LoadingState.Loading()
        }

        mutableToolbarStateFlow.value = newToolbarState
    }

    override fun onPoolClicked(pair: StringPair) {
        internalPoolsRouter.openDetailsPoolScreen(pair)
    }

    fun onNavigationClick() {
        internalPoolsRouter.back()
    }

    fun exitFlow() {
        poolsRouter.back()
    }
}

fun CommonPoolData.toListItemState(baseToken: Token?): BasicPoolListItemState? {
        val tvl = baseToken?.fiatRate?.times(BigDecimal(2))
        ?.multiply(basic.baseReserves)
        ?.formatFiat(baseToken.fiatSymbol).orEmpty()

    val baseTokenId = basic.baseToken.currencyId ?: return null
    val targetTokenId = basic.targetToken?.currencyId ?: return null

    val baseSymbol = basic.baseToken.symbol
    val targetSymbol = basic.targetToken?.symbol
    val userPooledInfo = user?.let { "${it.basePooled.formatCrypto(baseSymbol)} - ${it.targetPooled.formatCrypto(targetSymbol)}" }
    val text2Color = if (user == null) white50 else user.let { greenText }

    return BasicPoolListItemState(
        ids = StringPair(baseTokenId, targetTokenId),
        token1Icon = basic.baseToken.iconUrl,
        token2Icon = basic.targetToken?.iconUrl.orEmpty(),
        text1 = "$baseSymbol-$targetSymbol".uppercase(),
        text2 = userPooledInfo ?: tvl,
        text2Color = text2Color,
        text3 = basic.sbapy?.let {
            "%s%%".format(it.toBigDecimal().formatCrypto())
        }.orEmpty(),
        text4 = "Earn PSWAP"
    )
}
