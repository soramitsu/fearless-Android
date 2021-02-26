package jp.co.soramitsu.feature_staking_impl.presentation.common

import jp.co.soramitsu.feature_staking_impl.presentation.staking.model.AssetModel
import jp.co.soramitsu.feature_staking_impl.presentation.staking.model.TokenModel
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.Token

fun mapTokenToTokenModel(token: Token): TokenModel {
    return with(token) {
        TokenModel(
            type = type,
            dollarRate = dollarRate,
            recentRateChange = recentRateChange
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