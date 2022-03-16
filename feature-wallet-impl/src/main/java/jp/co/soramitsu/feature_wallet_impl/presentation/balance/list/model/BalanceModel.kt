package jp.co.soramitsu.feature_wallet_impl.presentation.balance.list.model

import jp.co.soramitsu.feature_wallet_impl.presentation.model.AssetWithStateModel
import java.math.BigDecimal

class BalanceModel(val assetModels: List<AssetWithStateModel>, val fiatSymbol: String) {
    val totalBalance = calculateTotalBalance()
    val isUpdating = checkIsUpdating()

    private fun calculateTotalBalance(): BigDecimal? {
        return if (assetModels.any { it.asset.fiatAmount != null }) {
            assetModels.fold(BigDecimal.ZERO) { acc, current ->
                val toAdd = current.asset.fiatAmount ?: BigDecimal.ZERO

                acc + toAdd
            }
        } else {
            null
        }
    }

    private fun checkIsUpdating(): Boolean = assetModels.any { it.state.isFiatUpdating }
}
