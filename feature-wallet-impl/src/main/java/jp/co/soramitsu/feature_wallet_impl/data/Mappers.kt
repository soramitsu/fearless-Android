package jp.co.soramitsu.feature_wallet_impl.data

import jp.co.soramitsu.core_db.model.AssetLocal
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset

fun AssetLocal.toAsset() : Asset {
    return Asset(
        token = token,
        balanceInPlanks = balanceInPlanks,
        dollarRate = dollarRate,
        recentRateChange = recentRateChange
    )
}

fun Asset.toLocal(accountAddress: String) : AssetLocal {
    return AssetLocal(accountAddress = accountAddress,
        token = token,
        balanceInPlanks = balanceInPlanks,
        dollarRate = dollarRate,
        recentRateChange = recentRateChange
    )
}