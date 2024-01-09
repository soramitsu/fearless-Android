package jp.co.soramitsu.wallet.impl.data.mappers

import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.coredb.model.AssetWithToken
import jp.co.soramitsu.coredb.model.TokenPriceLocal
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.Token
import jp.co.soramitsu.wallet.impl.presentation.model.AssetModel
import jp.co.soramitsu.wallet.impl.presentation.model.TokenModel
import jp.co.soramitsu.core.models.Asset as CoreAsset

fun combineAssetWithPrices(
    chainAsset: CoreAsset,
    tokenPriceLocal: TokenPriceLocal?
): Token {
    return Token(
        configuration = chainAsset,
        fiatRate = tokenPriceLocal?.fiatRate,
        fiatSymbol = tokenPriceLocal?.fiatSymbol,
        recentRateChange = tokenPriceLocal?.recentRateChange
    )
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
    chainAsset: CoreAsset,
    minSupportedVersion: String?
): Asset {
    return with(assetLocal) {
        Asset(
            metaId = asset.metaId,
            token = combineAssetWithPrices(chainAsset, token),
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
            chainAccountName = chainAccountName,
            isHidden = enabled == false
        )
    }
}
