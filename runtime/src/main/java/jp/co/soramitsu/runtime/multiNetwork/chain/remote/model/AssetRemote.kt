package jp.co.soramitsu.runtime.multiNetwork.chain.remote.model

import java.util.Locale

data class AssetRemote(
    val id: String?,
    val chainId: String?,
    val precision: Int?,
    val priceId: String?,
    val icon: String?
) {
    val symbol: String
        get() = id?.toUpperCase(Locale.ROOT).orEmpty()
}
