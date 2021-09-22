package jp.co.soramitsu.feature_wallet_impl.data.mappers

import jp.co.soramitsu.core_db.model.AssetWithToken
import jp.co.soramitsu.core_db.model.TokenLocal
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_impl.presentation.model.AssetModel
import jp.co.soramitsu.feature_wallet_impl.presentation.model.TokenModel
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain

fun mapTokenLocalToToken(
    tokenLocal: TokenLocal,
    chainAsset: Chain.Asset,
): Token {
    return with(tokenLocal) {
        Token(
            configuration = chainAsset,
            dollarRate = dollarRate,
            recentRateChange = recentRateChange
        )
    }
}

fun mapTokenToTokenModel(token: Token): TokenModel {
    return with(token) {
        TokenModel(
            configuration = configuration,
            dollarRate = dollarRate,
            recentRateChange = recentRateChange
        )
    }
}

fun mapAssetLocalToAsset(
    assetLocal: AssetWithToken,
    chainAsset: Chain.Asset
): Asset {
    return with(assetLocal) {
        Asset(
            token = mapTokenLocalToToken(token, chainAsset),
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
