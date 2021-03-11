package jp.co.soramitsu.feature_wallet_impl.presentation.balance.list.model

import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.feature_wallet_impl.presentation.model.AssetModel

class BalanceModel(val assetModels: List<AssetModel>) {
    val totalBalance = calculateTotalBalance()

    private fun calculateTotalBalance(): BigDecimal {
        return assetModels.fold(BigDecimal(BigInteger.ZERO)) { acc, current ->
            val toAdd = current.dollarAmount ?: BigDecimal.ZERO

            acc + toAdd
        }
    }
}
