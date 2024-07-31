package jp.co.soramitsu.liquiditypools.impl.navigation

import java.math.BigDecimal
import java.util.Stack
import jp.co.soramitsu.androidfoundation.format.StringPair
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

class InternalPoolsRouterImpl(
    private val walletRouter: WalletRouter
): InternalPoolsRouter {
    private val routesStack = Stack<LiquidityPoolsNavGraphRoute>()

    private val mutableActionsFlow =
        MutableSharedFlow<NavAction>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private val mutableRoutesFlow =
        MutableSharedFlow<LiquidityPoolsNavGraphRoute>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    override fun createNavGraphRoutesFlow(): Flow<LiquidityPoolsNavGraphRoute> = mutableRoutesFlow.onEach { routesStack.push(it) }
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

    override fun openAllPoolsScreen(chainId: ChainId) {
        mutableRoutesFlow.tryEmit(LiquidityPoolsNavGraphRoute.AllPoolsScreen(chainId))
    }

    override fun openDetailsPoolScreen(chainId: ChainId, ids: StringPair) {
        mutableRoutesFlow.tryEmit(LiquidityPoolsNavGraphRoute.PoolDetailsScreen(chainId, ids))
    }

    override fun openAddLiquidityScreen(chainId: ChainId, ids: StringPair) {
        mutableRoutesFlow.tryEmit(LiquidityPoolsNavGraphRoute.LiquidityAddScreen(chainId, ids))
    }

    override fun openAddLiquidityConfirmScreen(chainId: ChainId, ids: StringPair, amountBase: BigDecimal, amountTarget: BigDecimal, apy: String) {
        mutableRoutesFlow.tryEmit(LiquidityPoolsNavGraphRoute.LiquidityAddConfirmScreen(chainId, ids, amountBase, amountTarget, apy))
    }

    override fun openRemoveLiquidityScreen(chainId: ChainId, ids: StringPair) {
        mutableRoutesFlow.tryEmit(LiquidityPoolsNavGraphRoute.LiquidityRemoveScreen(chainId, ids))
    }

    override fun openRemoveLiquidityConfirmScreen(
        chainId: ChainId,
        ids: StringPair,
        amountBase: BigDecimal,
        amountTarget: BigDecimal,
        firstAmountMin: BigDecimal,
        secondAmountMin: BigDecimal,
        desired: BigDecimal
    ) {
        mutableRoutesFlow.tryEmit(LiquidityPoolsNavGraphRoute.LiquidityRemoveConfirmScreen(chainId, ids, amountBase, amountTarget, firstAmountMin, secondAmountMin, desired))
    }

    override fun openPoolListScreen(chainId: ChainId, isUserPools: Boolean) {
        mutableRoutesFlow.tryEmit(LiquidityPoolsNavGraphRoute.ListPoolsScreen(chainId, isUserPools))
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
                openInfoScreen("Strategic Bonus APY", "Farming reward for liquidity provision.")
            }
            PoolsFlowViewModel.ITEM_FEE_ID -> {
                openInfoScreen("Network fee", "Network fee is used to ensure SORA system’s growth and  stable performance. ")
            }
        }
    }

    override fun openSuccessScreen(txHash: String, chainId: ChainId, customMessage: String) {
        walletRouter.openOperationSuccess(txHash, chainId, customMessage)
    }

    override fun <T : LiquidityPoolsNavGraphRoute> destination(clazz: Class<T>): T? {
        return routesStack.filterIsInstance(clazz).lastOrNull()
    }
}