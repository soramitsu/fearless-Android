package jp.co.soramitsu.coredb.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import jp.co.soramitsu.coredb.model.TokenLocal
import kotlinx.coroutines.flow.Flow

@Dao
abstract class TokenDao {

    @Query("SELECT EXISTS(SELECT * FROM tokens WHERE assetId = :assetId)")
    abstract suspend fun isTokenExists(assetId: String): Boolean

    @Query("select * from tokens where assetId = :assetId")
    abstract suspend fun getToken(assetId: String): TokenLocal?

    @Query("select * from tokens where assetId = :assetId")
    abstract fun observeToken(assetId: String): Flow<TokenLocal>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertToken(token: TokenLocal)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertTokenOrIgnore(token: TokenLocal)

    suspend fun ensureToken(assetId: String) = insertTokenOrIgnore(TokenLocal.createEmpty(assetId))
}
