package jp.co.soramitsu.coredb.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import jp.co.soramitsu.coredb.model.TokenPriceLocal
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import jp.co.soramitsu.coredb.model.TonConnectionLocal

@Dao
abstract class TonConnectDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertTonConnection(connection: TonConnectionLocal)

    @Query("SELECT * FROM ton_connection")
    abstract fun observeTonConnections(): Flow<List<TonConnectionLocal>>

    @Query("SELECT * FROM ton_connection WHERE metaId = :metaId AND url LIKE :url")
    abstract suspend fun getTonConnection(metaId: Long, url: String): TonConnectionLocal?

    @Query("DELETE FROM ton_connection WHERE clientId = :dappId")
    abstract suspend fun deleteTonConnection(dappId: String)
}
//https://ton-explorer.dev.sora2.tachi.soramitsu.co.jp/
//https://ton-connect.example.fearless.soramitsu.co.jp/
//https://ton-connect.github.io/demo-dapp/