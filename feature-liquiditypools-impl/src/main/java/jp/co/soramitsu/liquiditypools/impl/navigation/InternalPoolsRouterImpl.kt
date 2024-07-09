package jp.co.soramitsu.liquiditypools.impl.navigation

import java.util.Stack
import jp.co.soramitsu.androidfoundation.format.StringPair
import jp.co.soramitsu.liquiditypools.navigation.InternalPoolsRouter
import jp.co.soramitsu.liquiditypools.navigation.LiquidityPoolsNavGraphRoute
import jp.co.soramitsu.liquiditypools.navigation.NavAction
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.onEach

class InternalPoolsRouterImpl: InternalPoolsRouter {
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

    override fun openDetailsPoolScreen(ids: StringPair) {
        mutableRoutesFlow.tryEmit(LiquidityPoolsNavGraphRoute.PoolDetailsScreen(ids))
    }

    override fun openPoolListScreen() {
        mutableRoutesFlow.tryEmit(LiquidityPoolsNavGraphRoute.ListPoolsScreen())
    }
}