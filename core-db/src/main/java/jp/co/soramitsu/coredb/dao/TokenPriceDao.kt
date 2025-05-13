package jp.co.soramitsu.coredb.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import jp.co.soramitsu.coredb.model.TokenPriceLocal
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal

@Dao
abstract class TokenPriceDao {
    @Query("select * from token_price where priceId = :priceId")
    abstract suspend fun getTokenPrice(priceId: String): TokenPriceLocal?

    @Query("select * from token_price where priceId = :priceId")
    abstract fun observeTokenPrice(priceId: String): Flow<TokenPriceLocal>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertTokenPrice(token: TokenPriceLocal)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertTokensPrice(tokens: List<TokenPriceLocal>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertTokenPriceOrIgnore(token: TokenPriceLocal)

    @Query("UPDATE token_price SET fiatRate = :fiatRate WHERE priceId = :priceId AND fiatSymbol = :fiatSymbol")
    abstract suspend fun updatePrices(priceId: String, fiatSymbol: String, fiatRate: BigDecimal)

    @Query("select * from token_price where fiatSymbol = :symbol")
    abstract fun observePrices(symbol: String): Flow<List<TokenPriceLocal>>
}
