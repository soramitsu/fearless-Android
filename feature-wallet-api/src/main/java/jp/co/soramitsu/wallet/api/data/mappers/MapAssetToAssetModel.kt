package jp.co.soramitsu.wallet.api.data.mappers

import androidx.annotation.StringRes
import java.math.BigDecimal
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_wallet_api.R
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.api.presentation.formatters.formatTokenAmount
import jp.co.soramitsu.wallet.api.presentation.model.AssetModel

fun mapAssetToAssetModel(
    asset: Asset,
    resourceManager: ResourceManager,
    retrieveAmount: (Asset) -> BigDecimal = Asset::transferable,
    @StringRes patternId: Int? = R.string.common_available_format
): AssetModel {
    val amount = retrieveAmount(asset).formatTokenAmount(asset.token.configuration)
    val formattedAmount = patternId?.let { resourceManager.getString(patternId, amount) } ?: amount

    return with(asset) {
        AssetModel(
            chainId = asset.token.configuration.chainId,
            chainAssetId = asset.token.configuration.id,
            imageUrl = token.configuration.iconUrl,
            tokenName = token.configuration.name,
            assetBalance = formattedAmount
        )
    }
}
