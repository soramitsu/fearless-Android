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

    val network = if (token.configuration.isNative) null else token.configuration.chainName?.let {
        ManageAssetModel.Network(iconUrl = token.configuration.chainIcon ?: "", it)
    }

    return ManageAssetModel(
        chainId = token.configuration.chainId,
        tokenSymbol = token.configuration.symbol,
        accountId = asset.accountId,
        name = token.configuration.name,
        iconUrl = token.configuration.iconUrl,
        amount = "$totalBalance ${token.configuration.symbol}",
        network = network,
        position = asset.sortIndex,
        enabled = asset.enabled,
        isTestNet = token.configuration.isTestNet ?: false,
        hasAccount = hasAccount,
        markedAsNotNeed = asset.markedNotNeed
    )
}

fun ManageAssetModel.toUpdateItem(metaId: Long, setPosition: Int?) = AssetUpdateItem(metaId, chainId, accountId, tokenSymbol, setPosition ?: position, enabled)
