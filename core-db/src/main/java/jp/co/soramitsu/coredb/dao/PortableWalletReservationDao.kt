package jp.co.soramitsu.coredb.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import jp.co.soramitsu.coredb.model.PortableWalletReservationLocal

@Dao
interface PortableWalletReservationDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(rows: List<PortableWalletReservationLocal>)

    @Query("SELECT * FROM portable_wallet_reservations ORDER BY metaId ASC")
    suspend fun all(): List<PortableWalletReservationLocal>

    @Query("SELECT EXISTS(SELECT 1 FROM portable_wallet_reservations WHERE metaId = :metaId)")
    suspend fun contains(metaId: Long): Boolean

    @Query("DELETE FROM portable_wallet_reservations WHERE operationId = :operationId AND afterImageSha256 = :digest")
    suspend fun deleteExact(operationId: String, digest: String): Int
}
