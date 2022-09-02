package jp.co.soramitsu.wallet.impl.presentation.model

data class AssetWithStateModel(
    val asset: AssetModel,
    val state: AssetUpdateState
)

data class AssetUpdateState(
    val rateUpdate: Boolean?,
    val balanceUpdate: Boolean?,
    val chainUpdate: Boolean?,
    val isTokenFiatChanged: Boolean?
) {
    val isBalanceUpdating = chainUpdate != false || balanceUpdate != false
    val isRateUpdating = chainUpdate != false || rateUpdate != false
    val isFiatUpdating = chainUpdate != false || rateUpdate != false || balanceUpdate != false
}
