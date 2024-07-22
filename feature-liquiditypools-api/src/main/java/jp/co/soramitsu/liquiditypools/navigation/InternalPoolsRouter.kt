package jp.co.soramitsu.liquiditypools.navigation

import java.math.BigDecimal
import jp.co.soramitsu.androidfoundation.format.StringPair
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlinx.coroutines.flow.Flow

interface InternalPoolsRouter {
    fun createNavGraphRoutesFlow(): Flow<LiquidityPoolsNavGraphRoute>
    fun createNavGraphActionsFlow(): Flow<NavAction>
    fun back()

    fun openAllPoolsScreen(chainId: ChainId)
    fun openDetailsPoolScreen(ids: StringPair)

    fun openAddLiquidityScreen(ids: StringPair)
    fun openAddLiquidityConfirmScreen(ids: StringPair, amountFrom: BigDecimal, amountTo: BigDecimal, apy: String)

    fun openPoolListScreen()

    fun openErrorsScreen(title: String? = null, message: String)
}