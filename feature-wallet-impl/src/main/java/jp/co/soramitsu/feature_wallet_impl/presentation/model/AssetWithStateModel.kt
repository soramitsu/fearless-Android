package jp.co.soramitsu.feature_wallet_impl.presentation.model

data class AssetWithStateModel(
    val asset: AssetModel,
    val state: AssetUpdateState
)

class AssetUpdateState(
    val rateUpdate: Boolean?,
    val balanceUpdate: Boolean?,
    val chainUpdate: Boolean?
) {
    val isBalanceUpdating = chainUpdate != false || balanceUpdate != false
    val isRateUpdating = chainUpdate != false || rateUpdate != false
    val isFiatUpdating = chainUpdate != false || rateUpdate != false || balanceUpdate != false
}
