package jp.co.soramitsu.core_db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import jp.co.soramitsu.core_db.model.AssetLocal
import jp.co.soramitsu.core_db.model.AssetUpdateItem
import jp.co.soramitsu.core_db.model.AssetWithToken
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import kotlinx.coroutines.flow.Flow

val emptyAccountIdValue: AccountId = ByteArray(0)

private const val RETRIEVE_ASSET_SQL_ACCOUNT_ID = """
            SELECT * FROM assets AS a INNER JOIN tokens AS t ON a.tokenSymbol = t.symbol 
            WHERE a.accountId IN (:accountId, :emptyAccountId) AND a.chainId = :chainId AND a.tokenSymbol = :symbol
              AND a.metaId = :metaId
            ORDER BY a.sortIndex
"""

private const val RETRIEVE_ACCOUNT_ASSETS_QUERY = """
            SELECT a.*, t.* FROM assets AS a 
            INNER JOIN tokens AS t ON a.tokenSymbol = t.symbol 
            LEFT JOIN chain_accounts AS ca ON ca.metaId = a.metaId AND ca.chainId = a.chainId
            WHERE a.metaId = :metaId
            ORDER BY a.sortIndex
"""

interface AssetReadOnlyCache {

    fun observeAssets(metaId: Long): Flow<List<AssetWithToken>>
    suspend fun getAssets(metaId: Long): List<AssetWithToken>

    fun observeAsset(metaId: Long, accountId: AccountId, chainId: String, symbol: String): Flow<AssetWithToken>

    suspend fun getAsset(metaId: Long, accountId: AccountId, chainId: String, symbol: String): AssetWithToken?
}

@Dao
abstract class AssetDao : AssetReadOnlyCache {

    @Query(RETRIEVE_ACCOUNT_ASSETS_QUERY)
    abstract override fun observeAssets(metaId: Long): Flow<List<AssetWithToken>>

    @Query(RETRIEVE_ACCOUNT_ASSETS_QUERY)
    abstract override suspend fun getAssets(metaId: Long): List<AssetWithToken>

    override fun observeAsset(metaId: Long, accountId: AccountId, chainId: String, symbol: String): Flow<AssetWithToken> =
        observeAssetWithEmpty(metaId, accountId, chainId, symbol, emptyAccountIdValue)

    @Query(RETRIEVE_ASSET_SQL_ACCOUNT_ID)
    protected abstract fun observeAssetWithEmpty(
        metaId: Long,
        accountId: AccountId,
        chainId: String,
        symbol: String,
        emptyAccountId: AccountId
    ): Flow<AssetWithToken>

    override suspend fun getAsset(metaId: Long, accountId: AccountId, chainId: String, symbol: String): AssetWithToken? =
        getAssetWithEmpty(metaId, accountId, chainId, symbol, emptyAccountIdValue)

    @Query(RETRIEVE_ASSET_SQL_ACCOUNT_ID)
    protected abstract suspend fun getAssetWithEmpty(
        metaId: Long,
        accountId: AccountId,
        chainId: String,
        symbol: String,
        emptyAccountId: AccountId
    ): AssetWithToken?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAsset(asset: AssetLocal)

    @Update(entity = AssetLocal::class)
    abstract suspend fun updateAssets(item: List<AssetUpdateItem>): Int

    @Update(entity = AssetLocal::class)
    abstract suspend fun updateAsset(asset: AssetLocal)

    @Query("DELETE FROM assets WHERE metaId = :metaId AND accountId = :accountId AND chainId = :chainId AND tokenSymbol = :symbol")
    abstract fun deleteAsset(metaId: Long, accountId: AccountId, chainId: String, symbol: String)
}
