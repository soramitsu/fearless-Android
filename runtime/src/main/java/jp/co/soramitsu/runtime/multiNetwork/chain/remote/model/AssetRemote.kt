package jp.co.soramitsu.runtime.multiNetwork.chain.remote.model

data class AssetRemote(
    val id: String?,
    val symbol: String?,
    val chainId: String?,
    val precision: Int?,
    val priceId: String?,
    val icon: String?
)
