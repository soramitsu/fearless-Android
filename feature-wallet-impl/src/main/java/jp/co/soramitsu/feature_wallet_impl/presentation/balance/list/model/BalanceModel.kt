package jp.co.soramitsu.feature_wallet_impl.presentation.balance.list.model

import jp.co.soramitsu.feature_wallet_impl.presentation.model.AssetModel

class BalanceModel(val assetModels: List<AssetModel>) {
    val totalBalance = calculateTotalBalance()

    private fun calculateTotalBalance(): Double {
        return assetModels.sumByDouble { it.dollarAmount }
    }
}