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
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.onEach
import java.math.BigDecimal
import java.util.Stack

class InternalPoolsRouterImpl(
    private val walletRouter: WalletRouter,
    private val resourceManager: ResourceManager
) : InternalPoolsRouter {
    private val routesStack = Stack<LiquidityPoolsNavGraphRoute>()

    private val mutableActionsFlow =
        MutableSharedFlow<NavAction>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private val mutableRoutesFlow =
        MutableSharedFlow<LiquidityPoolsNavGraphRoute>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    override fun createNavGraphRoutesFlow(): Flow<LiquidityPoolsNavGraphRoute> = mutableRoutesFlow.onEach {
        routesStack.push(it)
    }
    override fun createNavGraphActionsFlow(): Flow<NavAction> =
        mutableActionsFlow.onEach { if (it is NavAction.BackPressed && !routesStack.isEmpty()) routesStack.pop() }

    override fun back() {
        mutableActionsFlow.tryEmit(NavAction.BackPressed)
    }

    override fun popupToScreen(route: LiquidityPoolsNavGraphRoute) {
        if (routesStack.any { it.routeName == route.routeName }) {
            do {
                val pop = routesStack.pop()
            } while (pop.routeName != route.routeName)
        }
    }

    override fun openAllPoolsScreen() {
        mutableRoutesFlow.tryEmit(LiquidityPoolsNavGraphRoute.AllPoolsScreen())
    }

    override fun openDetailsPoolScreen(ids: StringPair) {
        mutableRoutesFlow.tryEmit(LiquidityPoolsNavGraphRoute.PoolDetailsScreen(ids))
    }

    override fun openAddLiquidityScreen(ids: StringPair) {
        mutableRoutesFlow.tryEmit(LiquidityPoolsNavGraphRoute.LiquidityAddScreen(ids))
    }

    override fun openAddLiquidityConfirmScreen(
        ids: StringPair,
        amountBase: BigDecimal,
        amountTarget: BigDecimal,
        apy: String
    ) {
        mutableRoutesFlow.tryEmit(LiquidityPoolsNavGraphRoute.LiquidityAddConfirmScreen(ids, amountBase, amountTarget, apy))
    }

    override fun openRemoveLiquidityScreen(ids: StringPair) {
        mutableRoutesFlow.tryEmit(LiquidityPoolsNavGraphRoute.LiquidityRemoveScreen(ids))
    }

    override fun openRemoveLiquidityConfirmScreen(
        ids: StringPair,
        amountBase: BigDecimal,
        amountTarget: BigDecimal,
        firstAmountMin: BigDecimal,
        secondAmountMin: BigDecimal,
        desired: BigDecimal
    ) {
        mutableRoutesFlow.tryEmit(LiquidityPoolsNavGraphRoute.LiquidityRemoveConfirmScreen(ids, amountBase, amountTarget, firstAmountMin, secondAmountMin, desired))
    }

    override fun openPoolListScreen(isUserPools: Boolean) {
        mutableRoutesFlow.tryEmit(LiquidityPoolsNavGraphRoute.ListPoolsScreen(isUserPools))
    }

    override fun openErrorsScreen(title: String?, message: String) {
        mutableActionsFlow.tryEmit(NavAction.ShowError(title, message))
    }

    override fun openInfoScreen(title: String, message: String) {
        mutableActionsFlow.tryEmit(NavAction.ShowInfo(title, message))
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
        walletRouter.openOperationSuccess(txHash, chainId, customMessage)
    }

    override fun <T : LiquidityPoolsNavGraphRoute> destination(clazz: Class<T>): T? {
        return routesStack.filterIsInstance(clazz).lastOrNull()
    }
}
