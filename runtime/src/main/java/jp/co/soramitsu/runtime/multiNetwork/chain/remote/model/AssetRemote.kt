package jp.co.soramitsu.runtime.multiNetwork.chain.remote.model

import jp.co.soramitsu.runtime.multiNetwork.chain.ChainAssetType

data class AssetRemote(
    val id: String?,
    val name: String?,
    val symbol: String?,
    @Deprecated("Tobe removed in favor of isUtility param of Chain.Asset")
    val chainId: String?,
    val displayName: String?,
    val precision: Int?,
    val priceId: String?,
    val icon: String?,
    val color: String?,
    val transfersEnabled: Boolean?,
    val type: ChainAssetType?,
    val currencyId: String?,
    val existentialDeposit: String?,
    val isNative: Boolean?
)
