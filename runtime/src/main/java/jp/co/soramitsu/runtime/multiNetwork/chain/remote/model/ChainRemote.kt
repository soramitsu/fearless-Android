package jp.co.soramitsu.runtime.multiNetwork.chain.remote.model

import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainTypesInfo

data class ChainRemote(
    val chainId: String,
    val name: String,
    val minSupportedVersion: String?,
    val assets: List<ChainAssetRemote>?,
    val nodes: List<ChainNodeRemote>?,
    val externalApi: ChainExternalApiRemote?,
    val icon: String?,
    val addressPrefix: Int,
    val types: ChainTypesInfo?,
    val options: List<String>?,
    val parentId: String?
)
