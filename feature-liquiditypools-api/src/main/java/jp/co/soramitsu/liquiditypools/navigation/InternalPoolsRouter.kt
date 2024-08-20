package jp.co.soramitsu.liquiditypools.navigation

import jp.co.soramitsu.androidfoundation.format.StringPair
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal

@Suppress("ComplexInterface")
interface InternalPoolsRouter {
    fun createNavGraphRoutesFlow(): Flow<LiquidityPoolsNavGraphRoute>
    fun createNavGraphActionsFlow(): Flow<NavAction>
    fun back()
    fun popupToScreen(route: LiquidityPoolsNavGraphRoute)

    fun openAllPoolsScreen()
    fun openDetailsPoolScreen(ids: StringPair)

    fun openAddLiquidityScreen(ids: StringPair)
    fun openAddLiquidityConfirmScreen(
        ids: StringPair,
        amountBase: BigDecimal,
        amountTarget: BigDecimal,
        apy: String
    )

    fun openRemoveLiquidityScreen(ids: StringPair)
    fun openRemoveLiquidityConfirmScreen(
        ids: StringPair,
        amountBase: BigDecimal,
        amountTarget: BigDecimal,
        firstAmountMin: BigDecimal,
        secondAmountMin: BigDecimal,
        desired: BigDecimal
    )

    fun openPoolListScreen(isUserPools: Boolean)

    fun openErrorsScreen(title: String? = null, message: String)
    fun openInfoScreen(title: String, message: String)
    fun openInfoScreen(itemId: Int)
    fun openSuccessScreen(
        txHash: String,
        chainId: ChainId,
        customMessage: String
    )

    fun <T : LiquidityPoolsNavGraphRoute> destination(clazz: Class<T>): T?
}
