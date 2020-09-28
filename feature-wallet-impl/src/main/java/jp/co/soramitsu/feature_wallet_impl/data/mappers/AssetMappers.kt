package jp.co.soramitsu.feature_wallet_impl.data.mappers

import jp.co.soramitsu.core_db.model.AssetLocal
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset

fun mapAssetLocalToAsset(assetLocal: AssetLocal): Asset {
    return with(assetLocal) {
        Asset(
            token = token,
            balanceInPlanks = balanceInPlanks,
            dollarRate = dollarRate,
            recentRateChange = recentRateChange
        )
    }
}

fun mapAssetToAssetLocal(asset: Asset, accountAddress: String): AssetLocal {
    return with(asset) {
        AssetLocal(accountAddress = accountAddress,
            token = token,
            balanceInPlanks = balanceInPlanks,
            dollarRate = dollarRate,
            recentRateChange = recentRateChange
        )
    }
}