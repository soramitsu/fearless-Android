package jp.co.soramitsu.wallet.impl.data.mappers

import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.coredb.model.AssetWithToken
import jp.co.soramitsu.coredb.model.TokenLocal
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.Token
import jp.co.soramitsu.wallet.impl.presentation.model.AssetModel
import jp.co.soramitsu.wallet.impl.presentation.model.TokenModel
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain

fun mapTokenLocalToToken(
    tokenLocal: TokenLocal,
    chainAsset: Chain.Asset
): Token {
    return with(tokenLocal) {
        Token(
            configuration = chainAsset,
            fiatRate = fiatRate,
            fiatSymbol = fiatSymbol,
            recentRateChange = recentRateChange
        )
    }
}

fun mapTokenToTokenModel(token: Token): TokenModel {
    return with(token) {
        TokenModel(
            configuration = configuration,
            fiatRate = fiatRate,
            fiatSymbol = fiatSymbol,
            recentRateChange = recentRateChange
        )
    }
}

fun mapAssetLocalToAsset(
    assetLocal: AssetWithToken,
    chainAsset: Chain.Asset,
    minSupportedVersion: String?
): Asset {
    return with(assetLocal) {
        Asset(
            metaId = asset.metaId,
            token = mapTokenLocalToToken(token, chainAsset),
            accountId = asset.accountId,
            freeInPlanks = asset.freeInPlanks.orZero(),
            reservedInPlanks = asset.reservedInPlanks.orZero(),
            feeFrozenInPlanks = asset.feeFrozenInPlanks.orZero(),
            miscFrozenInPlanks = asset.miscFrozenInPlanks.orZero(),
            bondedInPlanks = asset.bondedInPlanks.orZero(),
            unbondingInPlanks = asset.unbondingInPlanks.orZero(),
            redeemableInPlanks = asset.redeemableInPlanks.orZero(),
            sortIndex = asset.sortIndex,
            enabled = asset.enabled,
            minSupportedVersion = minSupportedVersion,
            chainAccountName = asset.chainAccountName,
            markedNotNeed = asset.markedNotNeed
        )
    }
}

fun mapAssetToAssetModel(asset: Asset): AssetModel {
    return with(asset) {
        AssetModel(
            metaId = metaId,
            token = mapTokenToTokenModel(token),
            total = total,
            bonded = bonded,
            locked = locked,
            available = transferable,
            reserved = reserved,
            frozen = frozen,
            redeemable = redeemable,
            unbonding = unbonding,
            fiatAmount = fiatAmount,
            sortIndex = sortIndex,
            minSupportedVersion = minSupportedVersion,
            chainAccountName = chainAccountName
        )
    }
}
