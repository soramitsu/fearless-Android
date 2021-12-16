package jp.co.soramitsu.feature_wallet_impl.presentation.balance.list.model

import jp.co.soramitsu.feature_wallet_impl.presentation.model.AssetModel
import java.math.BigDecimal

class BalanceModel(val assetModels: List<AssetModel>) {
    val totalBalance = calculateTotalBalance()

    private fun calculateTotalBalance(): BigDecimal {
        return assetModels.fold(BigDecimal.ZERO) { acc, current ->
            val toAdd = current.dollarAmount ?: BigDecimal.ZERO

            acc + toAdd
        }
    }
}
