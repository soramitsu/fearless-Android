package jp.co.soramitsu.wallet.impl.presentation.balance.manageAssets

import jp.co.soramitsu.common.utils.format
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.coredb.model.AssetUpdateItem
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.wallet.impl.domain.model.AssetWithStatus
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks
import jp.co.soramitsu.wallet.impl.domain.model.calculateTotalBalance

data class ManageAssetModel(
    val assetId: String,
    val chainId: ChainId,
    val tokenSymbol: String,
    val accountId: AccountId,
    val chainName: String,
    val iconUrl: String,
    // will be null if there is no account for this network
    val amount: String?,
    // will be null if the asset is native
    val network: Network?,
    var position: Int,
    var enabled: Boolean,
    var hasAccount: Boolean,
    val isTestNet: Boolean,
    val markedAsNotNeed: Boolean
) {
    data class Network(val iconUrl: String, val name: String)
}

fun AssetWithStatus.toAssetModel(): ManageAssetModel {
    val token = asset.token
    val totalAmount = calculateTotalBalance(asset.freeInPlanks, asset.reservedInPlanks).orZero()
    val totalBalance = token.amountFromPlanks(totalAmount).format()

    val network = when (token.configuration.isUtility) {
        true -> null
        else -> ManageAssetModel.Network(
            iconUrl = token.configuration.chainIcon.orEmpty(),
            name = token.configuration.chainName
        )
    }

    return ManageAssetModel(
        assetId = token.configuration.id,
        chainId = token.configuration.chainId,
        tokenSymbol = token.configuration.symbol,
        accountId = asset.accountId,
        chainName = token.configuration.chainName,
        iconUrl = token.configuration.iconUrl,
        amount = "$totalBalance ${token.configuration.symbolToShow.uppercase()}",
        network = network,
        position = asset.sortIndex,
        enabled = asset.enabled,
        isTestNet = token.configuration.isTestNet ?: false,
        hasAccount = hasAccount,
        markedAsNotNeed = asset.markedNotNeed
    )
}

fun ManageAssetModel.toUpdateItem(metaId: Long, setPosition: Int?) = AssetUpdateItem(metaId, chainId, accountId, assetId, setPosition ?: position, enabled)
