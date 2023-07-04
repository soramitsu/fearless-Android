package jp.co.soramitsu.runtime.multiNetwork.chain.remote.model

class ChainAssetRemote(
    val id: String?,
    val name: String?,
    val symbol: String?,
    val precision: Int?,
    val icon: String?,
    val priceId: String?,
    val currencyId: String?,
    val existentialDeposit: String?,
    val color: String?,
    val isUtility: Boolean?,
    val isNative: Boolean?,
    val staking: String?,
    val purchaseProviders: List<String>?,
    val type: String?
)
