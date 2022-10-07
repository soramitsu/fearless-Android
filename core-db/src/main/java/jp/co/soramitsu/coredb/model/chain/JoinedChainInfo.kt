package jp.co.soramitsu.coredb.model.chain

import androidx.room.Embedded
import androidx.room.Relation

class JoinedChainInfo(
    @Embedded
    val chain: ChainLocal,

    @Relation(parentColumn = "id", entityColumn = "chainId", entity = ChainNodeLocal::class)
    val nodes: List<ChainNodeLocal>,

    @Relation(parentColumn = "id", entityColumn = "chainId", entity = ChainAssetLocal::class)
    val assets: List<ChainAssetLocal>,

    @Relation(parentColumn = "id", entityColumn = "chainId", entity = ChainExplorerLocal::class)
    val explorers: List<ChainExplorerLocal>
)
