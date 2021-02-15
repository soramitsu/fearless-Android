package jp.co.soramitsu.core_db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import jp.co.soramitsu.core_db.model.AssetLocal
import jp.co.soramitsu.core_db.model.AssetWithToken
import jp.co.soramitsu.core_db.model.TokenLocal
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import kotlinx.coroutines.flow.Flow

private const val RETRIEVE_ASSET_SQL = """
           select * from assets as a inner join tokens as t where a.token = t.type
            and a.accountAddress = :accountAddress and a.token = :type
"""

interface AssetReadOnlyCache {
    fun observeAssets(accountAddress: String): Flow<List<AssetWithToken>>

    fun observeAsset(accountAddress: String, type: Token.Type): Flow<AssetWithToken>

    suspend fun getAsset(accountAddress: String, type: Token.Type): AssetWithToken?
}

@Dao
abstract class AssetDao : AssetReadOnlyCache {
    @Query("""
        select * from assets as a inner join tokens as t where a.token = t.type
        and a.accountAddress = :accountAddress
    """)
    abstract override fun observeAssets(accountAddress: String): Flow<List<AssetWithToken>>

    @Query(RETRIEVE_ASSET_SQL)
    abstract override fun observeAsset(accountAddress: String, type: Token.Type): Flow<AssetWithToken>

    @Query(RETRIEVE_ASSET_SQL)
    abstract override suspend fun getAsset(accountAddress: String, type: Token.Type): AssetWithToken?

    @Query("SELECT EXISTS(SELECT * FROM tokens WHERE type = :type)")
    abstract suspend fun isTokenExists(type: Token.Type): Boolean

    @Query("select * from tokens where type = :type")
    abstract suspend fun getToken(type: Token.Type): TokenLocal?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertToken(token: TokenLocal)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAsset(asset: AssetLocal)
}