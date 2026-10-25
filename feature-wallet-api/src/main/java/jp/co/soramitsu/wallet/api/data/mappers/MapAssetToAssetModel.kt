package jp.co.soramitsu.wallet.api.data.mappers

import androidx.annotation.StringRes
import java.math.BigDecimal
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.formatCrypto
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.feature_wallet_api.R
import jp.co.soramitsu.wallet.api.presentation.model.AssetModel
import jp.co.soramitsu.wallet.impl.domain.model.Asset

fun mapAssetToAssetModel(
    asset: Asset,
    resourceManager: ResourceManager,
    retrieveAmount: (Asset) -> BigDecimal? = Asset::availableForStaking,
    @StringRes patternId: Int? = R.string.common_available_format
): AssetModel {
    val value = retrieveAmount(asset)
    val amount = value?.formatCrypto(asset.token.configuration.symbol)
        ?: resourceManager.getString(jp.co.soramitsu.common.R.string.portfolio_balance_unavailable)
    val formattedAmount = patternId?.let { resourceManager.getString(patternId, amount) } ?: amount
    val fiatAmount = value?.let(asset.token::fiatAmount)?.formatFiat(asset.token.fiatSymbol)

    return with(asset) {
        AssetModel(
            chainId = asset.token.configuration.chainId,
            chainAssetId = asset.token.configuration.id,
            imageUrl = token.configuration.iconUrl,
            tokenName = token.configuration.chainName,
            assetBalance = formattedAmount + fiatAmount?.let { " ($it)" }.orEmpty()
        )
    }
}
