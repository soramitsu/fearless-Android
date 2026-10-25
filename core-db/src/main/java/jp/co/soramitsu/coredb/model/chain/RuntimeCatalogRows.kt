package jp.co.soramitsu.coredb.model.chain

import androidx.room.Embedded
import jp.co.soramitsu.coredb.model.AssetWithToken

/** Small row projections exclude the four optional JSON cells before CursorWindow materialization. */
data class RuntimeChainCatalogRow(@Embedded val chain: ChainLocal, val sourceRowId: Long, val xcmBytes: Long?, var xcmInline: ByteArray?)
data class RuntimeAssetCatalogRow(@Embedded val asset: ChainAssetLocal, val sourceRowId: Long,
    val purchaseBytes: Long?, val priceBytes: Long?, var purchaseInline: ByteArray?, var priceInline: ByteArray?)
data class RuntimeExplorerCatalogRow(@Embedded val explorer: ChainExplorerLocal, val sourceRowId: Long, val typesBytes: Long?, var typesInline: ByteArray?)
data class FundedCatalogAssetRow(val eligibleChainId: String, @Embedded val balance: AssetWithToken)
