package jp.co.soramitsu.runtime.multiNetwork.chain.remote.model

import kotlinx.serialization.Serializable
import java.util.Locale

@Serializable
data class AssetRemote(
    val id: String? = null,
    val chainId: String? = null,
    val precision: Int? = null,
    val priceId: String? = null,
    val icon: String? = null,
) {
    val symbol: String
        get() = id?.toUpperCase(Locale.ROOT).orEmpty()
}
