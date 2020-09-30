package jp.co.soramitsu.feature_wallet_impl.data.mappers

import jp.co.soramitsu.core_db.model.AssetLocal
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_impl.presentation.model.AssetModel

fun mapAssetLocalToAsset(assetLocal: AssetLocal): Asset {
    return with(assetLocal) {
        Asset(
            token = token,
            freeInPlanks = freeInPlanks,
            reservedInPlanks = reservedInPlanks,
            feeFrozenInPlanks = feeFrozenInPlanks,
            miscFrozenInPlanks = miscFrozenInPlanks,
            dollarRate = dollarRate,
            recentRateChange = recentRateChange
        )
    }
}

fun mapAssetToAssetLocal(asset: Asset, accountAddress: String): AssetLocal {
    return with(asset) {
        AssetLocal(accountAddress = accountAddress,
            token = token,
            freeInPlanks = freeInPlanks,
            reservedInPlanks = reservedInPlanks,
            feeFrozenInPlanks = feeFrozenInPlanks,
            miscFrozenInPlanks = miscFrozenInPlanks,
            dollarRate = dollarRate,
            recentRateChange = recentRateChange
        )
    }
}

fun mapAssetToAssetModel(asset: Asset): AssetModel {
    return with(asset) {
        AssetModel(
            token = token,
            balance = free,
            bonded = reserved,
            available = transferable,
            dollarRate = dollarRate,
            recentRateChange = recentRateChange,
            dollarAmount = dollarAmount
        )
    }
}