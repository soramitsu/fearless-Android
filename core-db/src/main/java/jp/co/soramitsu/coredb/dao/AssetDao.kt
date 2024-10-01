package jp.co.soramitsu.coredb.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import jp.co.soramitsu.coredb.model.AssetLocal
import jp.co.soramitsu.coredb.model.AssetUpdateItem
import jp.co.soramitsu.coredb.model.AssetWithToken
import jp.co.soramitsu.shared_utils.runtime.AccountId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull

val emptyAccountIdValue: AccountId = ByteArray(0)

private const val RETRIEVE_ASSET_SQL_ACCOUNT_ID = """
            SELECT * FROM assets AS a 
            LEFT JOIN token_price AS tp ON a.tokenPriceId = tp.priceId 
            WHERE a.accountId IN (:accountId, :emptyAccountId) AND a.chainId = :chainId AND a.id = :assetId
              AND a.metaId = :metaId
            ORDER BY a.sortIndex
"""

private const val RETRIEVE_ASSETS_SQL_SYMBOL = """
            SELECT a.*, tp.* FROM assets AS a 
            LEFT JOIN token_price AS tp ON a.tokenPriceId = tp.priceId 
            LEFT JOIN chain_assets ca ON ca.id = a.id AND ca.chainId = a.chainId
            WHERE a.accountId IN (:accountId, :emptyAccountId) 
              AND a.chainId = :chainId 
              AND ca.symbol = :symbol
              AND a.metaId = :metaId
            ORDER BY a.sortIndex
"""

private const val RETRIEVE_ACCOUNT_ASSETS_QUERY = """
            SELECT a.*, tp.* FROM assets AS a 
            LEFT JOIN token_price AS tp ON a.tokenPriceId = tp.priceId 
            LEFT JOIN chain_accounts AS ca ON ca.metaId = a.metaId AND ca.chainId = a.chainId
            WHERE a.metaId = :metaId
            AND (ca.accountId = a.accountId OR ca.accountId IS NULL)
            ORDER BY a.sortIndex
"""

interface AssetReadOnlyCache {

    fun observeAssets(metaId: Long): Flow<List<AssetWithToken>>
    fun observeAllEnabledAssets(): Flow<List<AssetLocal>>
    suspend fun getAssets(metaId: Long): List<AssetWithToken>

    fun observeAsset(metaId: Long, accountId: AccountId, chainId: String, assetId: String): Flow<AssetWithToken>

    suspend fun getAsset(metaId: Long, accountId: AccountId, chainId: String, assetId: String): AssetWithToken?
    suspend fun getAssets(metaId: Long, accountId: AccountId, chainId: String, symbol: String): List<AssetWithToken>
}

@Dao
abstract class AssetDao : AssetReadOnlyCache {

    @Transaction
    open suspend fun updateAssets(assetsToAdd: List<AssetLocal>, assetsToRemoveIds: List<String>) {
        insertAssets(assetsToAdd)
        assetsToRemoveIds.chunked(900).forEach { chunk ->
            deleteAssets(chunk)
        }
    }

    @Query(RETRIEVE_ACCOUNT_ASSETS_QUERY)
    abstract override fun observeAssets(metaId: Long): Flow<List<AssetWithToken>>

    @Query("SELECT * FROM assets WHERE enabled = 1")
    abstract override fun observeAllEnabledAssets(): Flow<List<AssetLocal>>

    @Query(RETRIEVE_ACCOUNT_ASSETS_QUERY)
    abstract override suspend fun getAssets(metaId: Long): List<AssetWithToken>

    override fun observeAsset(metaId: Long, accountId: AccountId, chainId: String, assetId: String): Flow<AssetWithToken> =
        observeAssetWithEmpty(metaId, accountId, chainId, assetId, emptyAccountIdValue)
            .flowOn(Dispatchers.IO)
            .mapNotNull { it }
            .map { AssetWithToken(it.asset.copy(accountId = accountId), it.token) }

    @Query(RETRIEVE_ASSET_SQL_ACCOUNT_ID)
    protected abstract fun observeAssetWithEmpty(
        metaId: Long,
        accountId: AccountId,
        chainId: String,
        assetId: String,
        emptyAccountId: AccountId
    ): Flow<AssetWithToken>

    override suspend fun getAsset(metaId: Long, accountId: AccountId, chainId: String, assetId: String): AssetWithToken? =
        getAssetWithEmpty(metaId, accountId, chainId, assetId, emptyAccountIdValue)

    @Query(RETRIEVE_ASSET_SQL_ACCOUNT_ID)
    protected abstract suspend fun getAssetWithEmpty(
        metaId: Long,
        accountId: AccountId,
        chainId: String,
        assetId: String,
        emptyAccountId: AccountId
    ): AssetWithToken?

    override suspend fun getAssets(metaId: Long, accountId: AccountId, chainId: String, symbol: String): List<AssetWithToken> =
        getAssetsWithEmpty(metaId, accountId, chainId, symbol, emptyAccountIdValue)

    @Query(RETRIEVE_ASSETS_SQL_SYMBOL)
    protected abstract suspend fun getAssetsWithEmpty(
        metaId: Long,
        accountId: AccountId,
        chainId: String,
        symbol: String,
        emptyAccountId: AccountId
    ): List<AssetWithToken>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAsset(asset: AssetLocal)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertAssets(assets: List<AssetLocal>)

    @Update(entity = AssetLocal::class)
    abstract suspend fun updateAssets(item: List<AssetUpdateItem>): Int

    @Update(entity = AssetLocal::class)
    abstract suspend fun updateAsset(asset: AssetLocal)

    @Query("DELETE FROM assets WHERE metaId = :metaId AND accountId = :accountId AND chainId = :chainId AND id = :assetId")
    abstract fun deleteAsset(metaId: Long, accountId: AccountId, chainId: String, assetId: String)

    @Query("DELETE FROM assets WHERE id in (:assetIdsToDelete)")
    abstract fun deleteAssets(assetIdsToDelete: List<String>)

    open suspend fun getAssets(accountMetaId: Long, id: String): List<AssetWithToken> {
        return observeAssetSymbolById(id).flatMapLatest { symbol ->
            observeAssetsBySymbol(
                accountMetaId = accountMetaId,
                assetSymbol = symbol
            )
        }.first()
    }

    open fun observeAssets(accountMetaId: Long, id: String): Flow<List<AssetWithToken>> {
        return observeAssetSymbolById(id).flatMapLatest { symbol ->
            observeAssetsBySymbol(
                accountMetaId = accountMetaId,
                assetSymbol = symbol
            )
        }
    }

    @Query("UPDATE assets SET enabled = CASE WHEN EXISTS (SELECT 1 FROM assets WHERE metaId = :metaId AND freeInPlanks > 0) THEN 0 ELSE enabled END WHERE metaId = :metaId AND (freeInPlanks IS NULL OR freeInPlanks = 0)")
    abstract fun hideEmptyAssetsIfThereAreAtLeastOnePositiveBalance(metaId: Long)

    @Query(
        """
            SELECT symbol FROM chain_assets WHERE chain_assets.id = :assetId
        """
    )
    protected abstract fun observeAssetSymbolById(assetId: String): Flow<String>

    @Transaction
    @Query(
        """
            SELECT a.*, tp.* FROM assets a
            LEFT JOIN token_price AS tp ON a.tokenPriceId = tp.priceId
            LEFT JOIN chain_assets ca ON ca.id = a.id AND ca.chainId = a.chainId
            WHERE ca.symbol in (:assetSymbol, '$xcPrefix'||:assetSymbol)
            AND a.metaId = :accountMetaId
        """
    )
    protected abstract fun observeAssetsBySymbol(
        accountMetaId: Long,
        assetSymbol: String
    ): Flow<List<AssetWithToken>>

    companion object {
        const val xcPrefix = "xc"
    }
}
