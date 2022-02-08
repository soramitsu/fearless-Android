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

private const val RETRIEVE_ASSET_SQL_META_ID = """
           select * from assets as a inner join tokens as t ON a.tokenSymbol = t.symbol WHERE
            a.metaId = :metaId and a.chainId = :chainId AND a.tokenSymbol = :symbol
            ORDER BY a.sortIndex
"""

private const val RETRIEVE_ASSET_SQL_ACCOUNT_ID = """
           select * from assets as a inner join tokens as t ON a.tokenSymbol = t.symbol WHERE 
            a.accountId = :accountId and a.chainId = :chainId AND a.tokenSymbol = :symbol
            ORDER BY a.sortIndex
"""

private const val RETRIEVE_ACCOUNT_ASSETS_QUERY = """
       select * from assets as a inner join tokens as t on a.tokenSymbol = t.symbol WHERE a.metaId = :metaId ORDER BY a.sortIndex
"""

interface AssetReadOnlyCache {

    fun observeAssets(metaId: Long): Flow<List<AssetWithToken>>
    suspend fun getAssets(metaId: Long): List<AssetWithToken>

    fun observeAsset(metaId: Long, chainId: String, symbol: String): Flow<AssetWithToken>

    fun observeAsset(accountId: AccountId, chainId: String, symbol: String): Flow<AssetWithToken>

    suspend fun getAsset(accountId: AccountId, chainId: String, symbol: String): AssetWithToken?

    suspend fun getAsset(metaId: Long, chainId: String, symbol: String): AssetWithToken?
}

@Dao
abstract class AssetDao : AssetReadOnlyCache {

    @Query(RETRIEVE_ACCOUNT_ASSETS_QUERY)
    abstract override fun observeAssets(metaId: Long): Flow<List<AssetWithToken>>

    @Query(RETRIEVE_ACCOUNT_ASSETS_QUERY)
    abstract override suspend fun getAssets(metaId: Long): List<AssetWithToken>

    @Query(RETRIEVE_ASSET_SQL_META_ID)
    abstract override fun observeAsset(metaId: Long, chainId: String, symbol: String): Flow<AssetWithToken>

    @Query(RETRIEVE_ASSET_SQL_ACCOUNT_ID)
    abstract override fun observeAsset(accountId: AccountId, chainId: String, symbol: String): Flow<AssetWithToken>

    @Query(RETRIEVE_ASSET_SQL_ACCOUNT_ID)
    abstract override suspend fun getAsset(accountId: AccountId, chainId: String, symbol: String): AssetWithToken?

    @Query(RETRIEVE_ASSET_SQL_META_ID)
    abstract override suspend fun getAsset(metaId: Long, chainId: String, symbol: String): AssetWithToken?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAsset(asset: AssetLocal)

    @Update(entity = AssetLocal::class)
    abstract suspend fun updateAssets(item: List<AssetUpdateItem>): Int
}
