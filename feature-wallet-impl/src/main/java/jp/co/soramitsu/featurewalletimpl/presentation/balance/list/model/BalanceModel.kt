package jp.co.soramitsu.featurewalletimpl.presentation.balance.list.model

import java.math.BigDecimal
import jp.co.soramitsu.featurewalletimpl.presentation.model.AssetWithStateModel

class BalanceModel(val assetModels: List<AssetWithStateModel>, val fiatSymbol: String) {
    val totalBalance = calculateTotalBalance()
    val isUpdating = checkIsUpdating()
    val isTokensUpdated = checkIsTokensUpdated()

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

    private fun checkIsUpdating(): Boolean = assetModels.any { it.state.isFiatUpdating }

    private fun checkIsTokensUpdated(): Boolean {
        return !assetModels.any { it.state.isTokenFiatChanged == true }
    }
}
