package jp.co.soramitsu.core_db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import jp.co.soramitsu.core_db.model.TokenLocal
import kotlinx.coroutines.flow.Flow

@Dao
abstract class TokenDao {

    @Query("SELECT EXISTS(SELECT * FROM tokens WHERE type = :type)")
    abstract suspend fun isTokenExists(type: TokenLocal.Type): Boolean

    @Query("select * from tokens where type = :type")
    abstract suspend fun getToken(type: TokenLocal.Type): TokenLocal?

    @Query("select * from tokens where type = :type")
    abstract fun observeToken(type: TokenLocal.Type): Flow<TokenLocal>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertToken(token: TokenLocal)
}
