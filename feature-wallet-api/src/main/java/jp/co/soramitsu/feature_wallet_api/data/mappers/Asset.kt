package jp.co.soramitsu.feature_wallet_api.data.mappers

import androidx.annotation.StringRes
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_wallet_api.R
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.presentation.formatters.formatTokenAmount
import jp.co.soramitsu.feature_wallet_api.presentation.model.AssetModel
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import java.math.BigDecimal

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
            asset.token.configuration.chainId,
            asset.token.configuration.id,
            token.configuration.icon,
            token.configuration.iconUrl,
            token.configuration.symbol,
            formattedAmount
        )
    }
}

val Chain.Asset.icon: Int
    get() = R.drawable.ic_token_dot // TODO wallet - token icon
