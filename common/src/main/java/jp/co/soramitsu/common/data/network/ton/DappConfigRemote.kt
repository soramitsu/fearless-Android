package jp.co.soramitsu.common.data.network.ton

import kotlinx.serialization.Serializable

@Serializable
data class DappConfigRemote(
    val type: String?,
    val apps: List<DappRemote>,
)

@Serializable
data class DappRemote(
    val identifier: String,
    val chains: List<String>,
    val name: String?,
    val url: String?,
    val description: String?,
    val background: String?,
    val icon: String?
)
