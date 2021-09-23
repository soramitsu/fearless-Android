package jp.co.soramitsu.core_db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import jp.co.soramitsu.core_db.model.AssetLocal
import jp.co.soramitsu.core_db.model.AssetWithToken
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import kotlinx.coroutines.flow.Flow

private const val RETRIEVE_ASSET_SQL_META_ID = """
           select * from assets as a inner join tokens as t ON a.symbol = t.symbol WHERE
            a.metaId = :metaId and a.chainId = :chainId AND a.symbol = :symbol
"""

private const val RETRIEVE_ASSET_SQL_ACCOUNT_ID = """
           select * from assets as a inner join tokens as t ON a.symbol = t.symbol WHERE 
            a.accountId = :accountId and a.chainId = :chainId AND a.symbol = :symbol
"""

interface AssetReadOnlyCache {
    fun observeAssets(metaId: Long): Flow<List<AssetWithToken>>

    fun observeAsset(metaId: Long, chainId: String, symbol: String): Flow<AssetWithToken>

    fun observeAsset(accountId: AccountId, chainId: String, symbol: String): Flow<AssetWithToken>

    suspend fun getAsset(accountId: AccountId, chainId: String, symbol: String): AssetWithToken?
}

@Dao
abstract class AssetDao : AssetReadOnlyCache {

    @Query(
        """
       select * from assets as a inner join tokens as t on a.symbol = t.symbol WHERE a.metaId = :metaId
    """
    )
    abstract override fun observeAssets(metaId: Long): Flow<List<AssetWithToken>>

    @Query(RETRIEVE_ASSET_SQL_META_ID)
    abstract override fun observeAsset(metaId: Long, chainId: String, symbol: String): Flow<AssetWithToken>

    @Query(RETRIEVE_ASSET_SQL_ACCOUNT_ID)
    abstract override fun observeAsset(accountId: AccountId, chainId: String, symbol: String): Flow<AssetWithToken>

    @Query(RETRIEVE_ASSET_SQL_META_ID)
    abstract override suspend fun getAsset(accountId: AccountId, chainId: String, symbol: String): AssetWithToken?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAsset(asset: AssetLocal)
}
