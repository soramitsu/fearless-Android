package jp.co.soramitsu.core_db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import jp.co.soramitsu.core_db.model.AssetLocal
import jp.co.soramitsu.core_db.model.AssetWithToken
import kotlinx.coroutines.flow.Flow

private const val RETRIEVE_ASSET_SQL = """
           select * from assets as a inner join tokens as t where a.symbol = t.symbol
            and a.accountId = :accountId and a.chainId = :chainId AND a.symbol = :symbol
"""

interface AssetReadOnlyCache {
    fun observeAssets(accountId: ByteArray): Flow<List<AssetWithToken>>

    fun observeAsset(accountId: ByteArray, chainId: String, symbol: String): Flow<AssetWithToken>

    suspend fun getAsset(accountId: ByteArray, chainId: String, symbol: String): AssetWithToken?
}

@Dao
abstract class AssetDao : AssetReadOnlyCache {

    @Query(
        """
       select * from assets as a inner join tokens as t where a.symbol = t.symbol
            and a.accountId = :accountId
    """
    )
    abstract override fun observeAssets(accountId: ByteArray): Flow<List<AssetWithToken>>

    @Query(RETRIEVE_ASSET_SQL)
    abstract override fun observeAsset(accountId: ByteArray, chainId: String, symbol: String): Flow<AssetWithToken>

    @Query(RETRIEVE_ASSET_SQL)
    abstract override suspend fun getAsset(accountId: ByteArray, chainId: String, symbol: String): AssetWithToken?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAsset(asset: AssetLocal)
}
