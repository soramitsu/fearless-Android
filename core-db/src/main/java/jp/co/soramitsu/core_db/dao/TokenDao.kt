package jp.co.soramitsu.core_db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import jp.co.soramitsu.core_db.model.TokenLocal
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import kotlinx.coroutines.flow.Flow

@Dao
abstract class TokenDao {

    @Query("SELECT EXISTS(SELECT * FROM tokens WHERE type = :type)")
    abstract suspend fun isTokenExists(type: Token.Type): Boolean

    @Query("select * from tokens where type = :type")
    abstract suspend fun getToken(type: Token.Type): TokenLocal?

    @Query("select * from tokens where type = :type")
    abstract fun observeToken(type: Token.Type): Flow<TokenLocal>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertToken(token: TokenLocal)
}