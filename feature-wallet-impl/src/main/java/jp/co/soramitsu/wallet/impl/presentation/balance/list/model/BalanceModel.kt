package jp.co.soramitsu.wallet.impl.presentation.balance.list.model

import java.math.BigDecimal
import java.math.RoundingMode
import jp.co.soramitsu.common.utils.fractionToPercentage
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.common.utils.percentageToFraction
import jp.co.soramitsu.wallet.impl.presentation.model.AssetWithStateModel

class BalanceModel(val assetModels: List<AssetWithStateModel>, val fiatSymbol: String) {
    val totalBalance = calculateTotalBalance()
    val totalBalanceChange = calculateTotalBalanceChange()
    val isShowLoading = checkIsShowLoading()
    val isUpdating = checkIsUpdating()
    val isTokensUpdated = checkIsTokensUpdated()

    val rate = try {
        totalBalance?.let { totalBalanceChange.divide(totalBalance, RoundingMode.HALF_UP).fractionToPercentage() }
    } catch (e: ArithmeticException) {
        e.printStackTrace()
        null
    }

    private fun calculateTotalBalance(): BigDecimal? {
        return if (assetModels.filter { it.asset.token.fiatSymbol == fiatSymbol }.any { it.asset.fiatAmount != null }) {
            assetModels.fold(BigDecimal.ZERO) { acc, current ->
                val toAdd = current.asset.fiatAmount ?: BigDecimal.ZERO

                acc + toAdd
            }
        } else {
            null
        }
    }

    private fun calculateTotalBalanceChange(): BigDecimal {
        return assetModels.fold(BigDecimal.ZERO) { acc, current ->
            val toAdd = current.asset.totalFiat?.multiply(current.asset.token.recentRateChange.orZero())?.percentageToFraction().orZero()

            acc + toAdd
        }
    }

    private fun checkIsUpdating(): Boolean = assetModels.any { it.state.isFiatUpdating }

    private fun checkIsShowLoading(): Boolean {
        val amountTotal = assetModels.size
        val pareto = 0.8

        val amountOfCurrentBalanceUpdating = assetModels.filter { it.state.isBalanceUpdating }.size

        val assumedAmountOfUpdated = amountTotal - amountOfCurrentBalanceUpdating
        val isLoading = amountTotal == 0 || assumedAmountOfUpdated < pareto * amountTotal
        return isLoading
    }

    private fun checkIsTokensUpdated(): Boolean {
        return !assetModels.any { it.state.isTokenFiatChanged == true }
    }
}
