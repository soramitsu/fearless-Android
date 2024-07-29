package jp.co.soramitsu.liquiditypools.navigation

import java.math.BigDecimal
import jp.co.soramitsu.androidfoundation.format.StringPair
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlinx.coroutines.flow.Flow

interface InternalPoolsRouter {
    fun createNavGraphRoutesFlow(): Flow<LiquidityPoolsNavGraphRoute>
    fun createNavGraphActionsFlow(): Flow<NavAction>
    fun back()
    fun popupToScreen(route: LiquidityPoolsNavGraphRoute)

    fun openAllPoolsScreen(chainId: ChainId)
    fun openDetailsPoolScreen(chainId: ChainId, ids: StringPair)

    fun openAddLiquidityScreen(chainId: ChainId, ids: StringPair)
    fun openAddLiquidityConfirmScreen(chainId: ChainId, ids: StringPair, amountFrom: BigDecimal, amountTo: BigDecimal, apy: String)

    fun openRemoveLiquidityScreen(chainId: ChainId, ids: StringPair)
    fun openRemoveLiquidityConfirmScreen(
        chainId: ChainId,
        ids: StringPair,
        amountFrom: BigDecimal,
        amountTo: BigDecimal,
        firstAmountMin: BigDecimal,
        secondAmountMin: BigDecimal,
        desired: BigDecimal
    )

    fun openPoolListScreen(chainId: ChainId, isUserPools: Boolean)

    fun openErrorsScreen(title: String? = null, message: String)
    fun openSuccessScreen(txHash: String, chainId: ChainId, customMessage: String)
}