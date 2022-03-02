package jp.co.soramitsu.feature_wallet_impl.presentation.balance.manageAssets

import jp.co.soramitsu.common.utils.format
import jp.co.soramitsu.core_db.model.AssetUpdateItem
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.amountFromPlanks
import jp.co.soramitsu.feature_wallet_api.domain.model.calculateTotalBalance
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId

data class ManageAssetModel(
    val chainId: ChainId,
    val tokenSymbol: String,
    val accountId: AccountId,
    val name: String,
    val iconUrl: String,
    // will be null if there is no account for this network
    val amount: String?,
    // will be null if the asset is native
    val network: Network?,
    var position: Int,
    var enabled: Boolean,
    val isTestNet: Boolean
) {
    data class Network(val iconUrl: String, val name: String)
}

fun Asset.toAssetModel(): ManageAssetModel {
    val totalAmount = calculateTotalBalance(freeInPlanks, reservedInPlanks)
    val totalBalance = token.amountFromPlanks(totalAmount).format()

    val network = if (token.configuration.isNative) null else token.configuration.chainName?.let {
        ManageAssetModel.Network(iconUrl = token.configuration.chainIcon ?: "", it)
    }

    return ManageAssetModel(
        chainId = token.configuration.chainId,
        tokenSymbol = token.configuration.symbol,
        accountId = accountId,
        name = token.configuration.name,
        iconUrl = token.configuration.iconUrl,
        amount = "$totalBalance ${token.configuration.symbol}",
        network = network,
        position = sortIndex,
        enabled = enabled,
        isTestNet = token.configuration.isTestNet ?: false
    )
}

fun ManageAssetModel.toUpdateItem(metaId: Long, setPosition: Int?) = AssetUpdateItem(metaId, chainId, accountId, tokenSymbol, setPosition ?: position, enabled)
