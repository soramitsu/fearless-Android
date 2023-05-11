package jp.co.soramitsu.runtime.multiNetwork.chain.remote.model

data class ChainRemote(
    val chainId: String,
    val name: String,
    val minSupportedVersion: String?,
    val assets: List<ChainAssetRemote>?,
    val nodes: List<ChainNodeRemote>?,
    val externalApi: ChainExternalApiRemote?,
    val icon: String?,
    val addressPrefix: Int,
    val options: List<String>?,
    val parentId: String?
)
