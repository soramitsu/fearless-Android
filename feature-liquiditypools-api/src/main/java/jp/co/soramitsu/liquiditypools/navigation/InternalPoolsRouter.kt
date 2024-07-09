package jp.co.soramitsu.liquiditypools.navigation

import jp.co.soramitsu.androidfoundation.format.StringPair
import kotlinx.coroutines.flow.Flow

interface InternalPoolsRouter {
    fun createNavGraphRoutesFlow(): Flow<LiquidityPoolsNavGraphRoute>
    fun createNavGraphActionsFlow(): Flow<NavAction>
    fun back()

    fun openDetailsPoolScreen(ids: StringPair)

    fun openPoolListScreen()
}