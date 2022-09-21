package jp.co.soramitsu.runtime.multiNetwork.chain.remote.model

import jp.co.soramitsu.runtime.multiNetwork.chain.ChainAssetType

data class AssetRemote(
    val id: String?,
    val symbol: String?,
    val chainId: String?,
    val displayName: String?,
    val precision: Int?,
    val priceId: String?,
    val icon: String?,
    val transfersEnabled: Boolean?,
    val type: ChainAssetType?,
    val currencyId: String?,
    val existentialDeposit: String?
)
