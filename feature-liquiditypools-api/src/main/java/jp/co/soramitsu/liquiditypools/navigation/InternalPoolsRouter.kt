package jp.co.soramitsu.liquiditypools.navigation

import java.math.BigDecimal
import jp.co.soramitsu.androidfoundation.format.StringPair
import kotlinx.coroutines.flow.Flow

interface InternalPoolsRouter {
    fun createNavGraphRoutesFlow(): Flow<LiquidityPoolsNavGraphRoute>
    fun createNavGraphActionsFlow(): Flow<NavAction>
    fun back()

    fun openAllPoolsScreen()
    fun openDetailsPoolScreen(ids: StringPair)

    fun openAddLiquidityScreen(ids: StringPair)
    fun openAddLiquidityConfirmScreen(ids: StringPair, amountFrom: BigDecimal, amountTo: BigDecimal, apy: String)

    fun openPoolListScreen()

    fun openErrorsScreen(title: String? = null, message: String)
}