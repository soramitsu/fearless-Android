package jp.co.soramitsu.wallet.impl.presentation.balance.list.model

import jp.co.soramitsu.common.utils.fractionToPercentage
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.common.utils.percentageToFraction
import jp.co.soramitsu.common.utils.sumByBigDecimal
import jp.co.soramitsu.wallet.impl.presentation.model.AssetWithStateModel
import java.math.BigDecimal
import java.math.RoundingMode

class BalanceModel(val assetModels: List<AssetWithStateModel>, val fiatSymbol: String) {
    val totalBalance = calculateTotalBalance()
    val totalBalanceChange = calculateTotalBalanceChange()
    val isShowLoading = checkIsShowLoading()
    val isUpdating = checkIsUpdating()
    val isTokensUpdated = checkIsTokensUpdated()
    val totalTransferableBalance = calculateTotalTransferableBalance()
    val totalTransferableBalanceChange = calculateTotalTransferableBalanceChange()

    val rate = when {
        totalBalance == null -> null
        totalBalance.compareTo(BigDecimal.ZERO) == 0 -> BigDecimal.ZERO
        else -> totalBalanceChange.divide(totalBalance, RoundingMode.HALF_UP).fractionToPercentage()
    }

    val transferableRate = when {
        totalTransferableBalance == null -> null
        totalTransferableBalance.compareTo(BigDecimal.ZERO) == 0 -> BigDecimal.ZERO
        else -> totalTransferableBalanceChange.divide(totalTransferableBalance, RoundingMode.HALF_UP).fractionToPercentage()
    }

    private fun calculateTotalBalance(): BigDecimal? {
        return if (assetModels.filter { it.asset.token.fiatSymbol == fiatSymbol }.any { it.asset.fiatAmount != null }) {
            assetModels.sumByBigDecimal {
                it.asset.fiatAmount.orZero()
            }
        } else {
            null
        }
    }

    private fun calculateTotalTransferableBalance(): BigDecimal? {
        return if (assetModels.filter { it.asset.token.fiatSymbol == fiatSymbol }.any { it.asset.available != null }) {
            assetModels.sumByBigDecimal {
                it.asset.available.orZero()
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

    private fun calculateTotalTransferableBalanceChange(): BigDecimal {
        return assetModels.fold(BigDecimal.ZERO) { acc, current ->
            val toAdd = current.asset.availableFiat?.multiply(current.asset.token.recentRateChange.orZero())?.percentageToFraction().orZero()

            acc + toAdd
        }
    }

    private fun checkIsUpdating(): Boolean = assetModels.any { it.state.isFiatUpdating }

    private fun checkIsShowLoading(): Boolean {
        val amountTotal = assetModels.size
        val pareto = 0.8

        val amountOfCurrentBalanceUpdating = assetModels.filter { it.state.isBalanceUpdating }.size

        val assumedAmountOfUpdated = amountTotal - amountOfCurrentBalanceUpdating
        val isLoading = assumedAmountOfUpdated < amountTotal * pareto
        return isLoading
    }

    private fun checkIsTokensUpdated(): Boolean {
        return !assetModels.any { it.state.isTokenFiatChanged == true }
    }
}
