package jp.co.soramitsu.coredb.model.chain

import androidx.room.Embedded
import androidx.room.Relation

/** Catalog metadata deliberately excludes the unbounded operational chain_nodes relation. */
data class JoinedChainCatalogMetadata(
    @Embedded val chain: ChainLocal,
    @Relation(parentColumn = "id", entityColumn = "chainId", entity = ChainAssetLocal::class)
    val assets: List<ChainAssetLocal>,
    @Relation(parentColumn = "id", entityColumn = "chainId", entity = ChainExplorerLocal::class)
    val explorers: List<ChainExplorerLocal>
) {
    fun withoutNodes(): JoinedChainInfo = JoinedChainInfo(chain, emptyList(), assets, explorers)
}
