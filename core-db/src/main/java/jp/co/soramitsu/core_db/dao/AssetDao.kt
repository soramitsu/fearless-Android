package jp.co.soramitsu.core_db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import jp.co.soramitsu.core_db.model.AssetLocal
import jp.co.soramitsu.core_db.model.AssetWithToken
import jp.co.soramitsu.core_db.model.TokenLocal
import kotlinx.coroutines.flow.Flow

private const val RETRIEVE_ASSET_SQL = """
           select * from assets as a inner join tokens as t where a.token = t.type
            and a.accountAddress = :accountAddress and a.token = :type
"""

interface AssetReadOnlyCache {
    fun observeAssets(accountAddress: String): Flow<List<AssetWithToken>>

    fun observeAsset(accountAddress: String, type: TokenLocal.Type): Flow<AssetWithToken>

    suspend fun getAsset(accountAddress: String, type: TokenLocal.Type): AssetWithToken?
}

@Dao
abstract class AssetDao : AssetReadOnlyCache {

    @Query(
        """
        select * from assets as a inner join tokens as t where a.token = t.type
        and a.accountAddress = :accountAddress
    """
    )
    abstract override fun observeAssets(accountAddress: String): Flow<List<AssetWithToken>>

    @Query(RETRIEVE_ASSET_SQL)
    abstract override fun observeAsset(accountAddress: String, type: TokenLocal.Type): Flow<AssetWithToken>

    @Query(RETRIEVE_ASSET_SQL)
    abstract override suspend fun getAsset(accountAddress: String, type: TokenLocal.Type): AssetWithToken?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAsset(asset: AssetLocal)
}
