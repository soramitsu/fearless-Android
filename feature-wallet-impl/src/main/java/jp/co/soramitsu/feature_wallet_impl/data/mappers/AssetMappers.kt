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
            recentRateChange = recentRateChange,
            bondedInPlanks = bondedInPlanks,
            unbondingInPlanks = unbondingInPlanks,
            redeemableInPlanks = redeemableInPlanks
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
            recentRateChange = recentRateChange,
            redeemableInPlanks = redeemableInPlanks,
            unbondingInPlanks = unbondingInPlanks,
            bondedInPlanks = bondedInPlanks
        )
    }
}

fun mapAssetToAssetModel(asset: Asset): AssetModel {
    return with(asset) {
        AssetModel(
            token = token,
            total = total,
            bonded = bonded,
            locked = locked,
            available = transferable,
            reserved = reserved,
            frozen = frozen,
            redeemable = redeemable,
            unbonding = unbonding,
            dollarRate = dollarRate,
            recentRateChange = recentRateChange,
            dollarAmount = dollarAmount
        )
    }
}