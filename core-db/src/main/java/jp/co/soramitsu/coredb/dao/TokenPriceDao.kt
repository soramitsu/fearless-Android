package jp.co.soramitsu.coredb.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import jp.co.soramitsu.coredb.model.TokenPriceLocal
import kotlinx.coroutines.flow.Flow

@Dao
abstract class TokenPriceDao {

    @Query("SELECT EXISTS(SELECT * FROM token_price WHERE priceId = :priceId)")
    abstract suspend fun isTokenPriceExists(priceId: String): Boolean

    @Query("select * from token_price where priceId = :priceId")
    abstract suspend fun getTokenPrice(priceId: String): TokenPriceLocal?

    @Query("select * from token_price where priceId = :priceId")
    abstract fun observeTokenPrice(priceId: String): Flow<TokenPriceLocal>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertTokenPrice(token: TokenPriceLocal)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertTokenPriceOrIgnore(token: TokenPriceLocal)

    suspend fun ensureTokenPrice(priceId: String) = insertTokenPriceOrIgnore(TokenPriceLocal.createEmpty(priceId))
}
