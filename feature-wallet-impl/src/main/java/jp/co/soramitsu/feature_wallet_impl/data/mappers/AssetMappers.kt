package jp.co.soramitsu.feature_wallet_impl.data.mappers

import jp.co.soramitsu.core_db.model.AssetWithToken
import jp.co.soramitsu.core_db.model.TokenLocal
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import jp.co.soramitsu.feature_wallet_impl.presentation.model.AssetModel
import jp.co.soramitsu.feature_wallet_impl.presentation.model.TokenModel

fun mapTokenLocalToToken(tokenLocal: TokenLocal): Token {
    return with(tokenLocal) {
        Token(
            type = type,
            dollarRate = dollarRate,
            recentRateChange = recentRateChange
        )
    }
}

fun mapTokenToTokenModel(token: Token): TokenModel {
    return with(token) {
        TokenModel(
            type = type,
            dollarRate = dollarRate,
            recentRateChange = recentRateChange
        )
    }
}

fun mapAssetLocalToAsset(assetLocal: AssetWithToken): Asset {
    return with(assetLocal) {
        Asset(
            token = mapTokenLocalToToken(token),
            freeInPlanks = asset.freeInPlanks,
            reservedInPlanks = asset.reservedInPlanks,
            feeFrozenInPlanks = asset.feeFrozenInPlanks,
            miscFrozenInPlanks = asset.miscFrozenInPlanks,
            bondedInPlanks = asset.bondedInPlanks,
            unbondingInPlanks = asset.unbondingInPlanks,
            redeemableInPlanks = asset.redeemableInPlanks
        )
    }
}

fun mapAssetToAssetModel(asset: Asset): AssetModel {
    return with(asset) {
        AssetModel(
            token = mapTokenToTokenModel(token),
            total = total,
            bonded = bonded,
            locked = locked,
            available = transferable,
            reserved = reserved,
            frozen = frozen,
            redeemable = redeemable,
            unbonding = unbonding,
            dollarAmount = dollarAmount
        )
    }
}