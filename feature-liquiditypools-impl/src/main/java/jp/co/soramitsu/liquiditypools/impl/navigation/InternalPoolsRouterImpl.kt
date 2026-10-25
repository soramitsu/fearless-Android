package jp.co.soramitsu.liquiditypools.impl.navigation

import jp.co.soramitsu.androidfoundation.format.StringPair
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.liquiditypools.impl.presentation.PoolsFlowViewModel
import jp.co.soramitsu.liquiditypools.navigation.InternalPoolsRouter
import jp.co.soramitsu.liquiditypools.navigation.LiquidityPoolsNavGraphRoute
import jp.co.soramitsu.liquiditypools.navigation.NavAction
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.Stack

class InternalPoolsRouterImpl(
    private val walletRouter: WalletRouter,
    private val resourceManager: ResourceManager,
    private val actionScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
) : InternalPoolsRouter {
    private val routesStack = Stack<LiquidityPoolsNavGraphRoute>()

    private val mutableActionsFlow =
        MutableSharedFlow<NavAction>(extraBufferCapacity = 1)

    private val mutableRoutesFlow =
        MutableSharedFlow<LiquidityPoolsNavGraphRoute>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    override fun createNavGraphRoutesFlow(): Flow<LiquidityPoolsNavGraphRoute> = mutableRoutesFlow

    override fun createNavGraphActionsFlow(): Flow<NavAction> = mutableActionsFlow

    private fun publishRoute(route: LiquidityPoolsNavGraphRoute) {
        // Presenters and the screen each collect routes; history belongs to navigation,
        // so collecting or replaying a route must not add another stack entry.
        routesStack.push(route)
        mutableRoutesFlow.tryEmit(route)
    }

    private fun publishAction(action: NavAction) {
        // Start each emission in call order. A slow collector suspends delivery instead
        // of losing consecutive BackPressed events; no-subscriber events still drop.
        actionScope.launch(start = CoroutineStart.UNDISPATCHED) { mutableActionsFlow.emit(action) }
    }

    override fun back() {
        if (!routesStack.isEmpty()) routesStack.pop()
        publishAction(NavAction.BackPressed)
    }

    override fun popupToScreen(route: LiquidityPoolsNavGraphRoute) {
        if (routesStack.any { it.routeName == route.routeName }) {
            do {
                val pop = routesStack.pop()
            } while (pop.routeName != route.routeName)
        }
    }

    override fun openAllPoolsScreen() {
        publishRoute(LiquidityPoolsNavGraphRoute.AllPoolsScreen())
    }

    override fun openDetailsPoolScreen(ids: StringPair) {
        publishRoute(LiquidityPoolsNavGraphRoute.PoolDetailsScreen(ids))
    }

    override fun openAddLiquidityScreen(ids: StringPair) {
        publishRoute(LiquidityPoolsNavGraphRoute.LiquidityAddScreen(ids))
    }

    override fun openAddLiquidityConfirmScreen(
        ids: StringPair,
        amountBase: BigDecimal,
        amountTarget: BigDecimal,
        apy: String
    ) {
        publishRoute(LiquidityPoolsNavGraphRoute.LiquidityAddConfirmScreen(ids, amountBase, amountTarget, apy))
    }

    override fun openRemoveLiquidityScreen(ids: StringPair) {
        publishRoute(LiquidityPoolsNavGraphRoute.LiquidityRemoveScreen(ids))
    }

    override fun openRemoveLiquidityConfirmScreen(
        ids: StringPair,
        amountBase: BigDecimal,
        amountTarget: BigDecimal,
        firstAmountMin: BigDecimal,
        secondAmountMin: BigDecimal,
        desired: BigDecimal
    ) {
        publishRoute(LiquidityPoolsNavGraphRoute.LiquidityRemoveConfirmScreen(ids, amountBase, amountTarget, firstAmountMin, secondAmountMin, desired))
    }

    override fun openPoolListScreen(isUserPools: Boolean) {
        publishRoute(LiquidityPoolsNavGraphRoute.ListPoolsScreen(isUserPools))
    }

    override fun openErrorsScreen(title: String?, message: String) {
        publishAction(NavAction.ShowError(title, message))
    }

    override fun openInfoScreen(title: String, message: String) {
        publishAction(NavAction.ShowInfo(title, message))
    }

    override fun openInfoScreen(itemId: Int) {
        when (itemId) {
            PoolsFlowViewModel.ITEM_APY_ID -> {
                openInfoScreen(resourceManager.getString(res = R.string.lp_apy_title), resourceManager.getString(res = R.string.lp_apy_alert_text))
            }
            PoolsFlowViewModel.ITEM_FEE_ID -> {
                openInfoScreen(resourceManager.getString(res = R.string.common_network_fee), resourceManager.getString(res = R.string.lp_network_fee_alert_text))
            }
        }
    }

    override fun openSuccessScreen(
        txHash: String,
        chainId: ChainId,
        customMessage: String
    ) {
        walletRouter.openOperationSuccess(txHash, chainId, customMessage, null)
    }

    override fun <T : LiquidityPoolsNavGraphRoute> destination(clazz: Class<T>): T? {
        return routesStack.filterIsInstance(clazz).lastOrNull()
    }

    override fun supplyLiquidityCompleted() {
        publishAction(NavAction.SupplyLiquidityCompleted)
    }
}
