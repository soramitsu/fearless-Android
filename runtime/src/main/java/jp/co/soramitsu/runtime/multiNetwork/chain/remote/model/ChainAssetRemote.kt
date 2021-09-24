package jp.co.soramitsu.runtime.multiNetwork.chain.remote.model

class ChainAssetRemote(
    val assetId: Int,
    val symbol: String,
    val precision: Int,
    val priceId: String?,
    val name: String?,
    val staking: String?
)
