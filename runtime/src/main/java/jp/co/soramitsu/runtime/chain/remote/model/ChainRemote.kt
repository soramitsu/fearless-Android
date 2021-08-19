package jp.co.soramitsu.runtime.chain.remote.model

data class ChainRemote(
    val chainId: String,
    val name: String,
    val assets: List<ChainAssetRemote>,
    val nodes: List<ChainNodeRemote>,
    val icon: String,
    val addressPrefix: Int,
    val types: ChainTypesInfo?,
    val options: List<String>?,
    val parentId: String?
)
