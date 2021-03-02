package jp.co.soramitsu.feature_staking_impl.presentation.common

import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.formatAsCurrency
import jp.co.soramitsu.common.wallet.formatWithDefaultPrecision
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.presentation.common.model.FeeModel
import jp.co.soramitsu.feature_staking_impl.presentation.staking.model.AssetModel
import jp.co.soramitsu.feature_staking_impl.presentation.staking.model.icon
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import java.math.BigDecimal

fun mapAssetToAssetModel(asset: Asset, resourceManager: ResourceManager): AssetModel {
    return with(asset) {
        val transferable = transferable.formatWithDefaultPrecision(asset.token.type)

        AssetModel(
            token.type.icon,
            token.type.displayName,
            resourceManager.getString(R.string.common_balance_format, transferable)
        )
    }
}

fun mapFeeToFeeModel(
    fee: BigDecimal,
    token: Token
) = FeeModel(
    fee = fee,
    displayToken = fee.formatWithDefaultPrecision(token.type),
    displayFiat = token.fiatAmount(fee)?.formatAsCurrency()
)