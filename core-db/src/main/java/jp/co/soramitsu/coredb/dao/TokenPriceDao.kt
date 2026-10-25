package jp.co.soramitsu.coredb.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import jp.co.soramitsu.coredb.model.TokenPriceLocal
import jp.co.soramitsu.coredb.model.cache.CachedTokenPriceRead
import jp.co.soramitsu.coredb.model.cache.RawCachedTokenPriceRow
import jp.co.soramitsu.coredb.model.cache.CachedTokenPriceProjectionSql
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal

@Dao
abstract class TokenPriceDao {
    open suspend fun getTokenPrice(priceId: String): CachedTokenPriceRead? = getTokenPriceRow(priceId)?.decode()

    open fun observeTokenPrice(priceId: String): Flow<CachedTokenPriceRead?> =
        observeTokenPriceRow(priceId).map { it?.decode() }

    @Query(CachedTokenPriceProjectionSql.BY_ID)
    protected abstract suspend fun getTokenPriceRow(priceId: String): RawCachedTokenPriceRow?

    @Query(CachedTokenPriceProjectionSql.BY_ID)
    protected abstract fun observeTokenPriceRow(priceId: String): Flow<RawCachedTokenPriceRow?>

    /**
     * Currency-aware readers select these IDs from the current catalog and currency context.
     * Empty inputs perform no query; duplicate IDs are captured once before suspension.
     * Each batch uses at most 512 binds. This is not a transaction-wide quote-time snapshot.
     */
    open suspend fun getTokenPricesByIds(priceIds: List<String>): List<CachedTokenPriceRead> {
        val batches = priceIds.distinct().chunked(QUOTE_ID_BATCH_SIZE)
        return batches.flatMap { ids -> getTokenPriceRowsByIds(ids).map { it.decode() } }
    }

    /** All batches must first emit; later empty results, errors and cancellation are preserved. */
    open fun observeTokenPricesByIds(priceIds: List<String>): Flow<List<CachedTokenPriceRead>> {
        val batches = priceIds.distinct().chunked(QUOTE_ID_BATCH_SIZE)
        if (batches.isEmpty()) return flowOf(emptyList())
        val observations = batches.map { ids ->
            observeTokenPriceRowsByIds(ids).map { rows -> rows.map { it.decode() } }
        }
        if (observations.size == 1) return observations.single()
        return combine(observations) { rows -> rows.flatMap { it } }
    }

    @Query(CachedTokenPriceProjectionSql.BY_IDS)
    protected abstract suspend fun getTokenPriceRowsByIds(priceIds: List<String>): List<RawCachedTokenPriceRow>

    @Query(CachedTokenPriceProjectionSql.BY_IDS)
    protected abstract fun observeTokenPriceRowsByIds(priceIds: List<String>): Flow<List<RawCachedTokenPriceRow>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertTokenPrice(token: TokenPriceLocal)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertTokensPrice(tokens: List<TokenPriceLocal>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertTokenPriceOrIgnore(token: TokenPriceLocal)

    @Query("UPDATE token_price SET fiatRate = :fiatRate WHERE priceId = :priceId AND fiatSymbol = :fiatSymbol")
    abstract suspend fun updatePrices(priceId: String, fiatSymbol: String, fiatRate: BigDecimal)

    open fun observePrices(symbol: String): Flow<List<CachedTokenPriceRead>> =
        observePriceRows(symbol).map { rows -> rows.map(RawCachedTokenPriceRow::decode) }

    @Query(CachedTokenPriceProjectionSql.BY_SYMBOL)
    protected abstract fun observePriceRows(symbol: String): Flow<List<RawCachedTokenPriceRow>>
    companion object {
        const val QUOTE_ID_BATCH_SIZE = 512
    }
}
