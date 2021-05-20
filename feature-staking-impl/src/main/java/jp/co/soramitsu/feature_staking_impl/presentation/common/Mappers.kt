package jp.co.soramitsu.feature_staking_impl.presentation.common

import androidx.annotation.StringRes
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.presentation.staking.main.model.AssetModel
import jp.co.soramitsu.feature_staking_impl.presentation.staking.main.model.icon
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.presentation.formatters.formatTokenAmount
import java.math.BigDecimal

fun mapAssetToAssetModel(
    asset: Asset,
    resourceManager: ResourceManager,
    retrieveAmount: (Asset) -> BigDecimal = Asset::transferable,
    @StringRes patternId: Int = R.string.common_available_format
): AssetModel {
    val amount = retrieveAmount(asset).formatTokenAmount(asset.token.type)

    return with(asset) {
        AssetModel(
            token.type.icon,
            token.type.displayName,
            resourceManager.getString(patternId, amount)
        )
    }
}

