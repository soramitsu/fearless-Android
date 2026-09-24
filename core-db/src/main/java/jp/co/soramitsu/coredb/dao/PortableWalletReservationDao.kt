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

    @Query("SELECT COUNT(*) FROM portable_wallet_reservations")
    suspend fun count(): Int

    @Query("SELECT * FROM portable_wallet_reservations WHERE state = 0 ORDER BY metaId ASC")
    suspend fun pending(): List<PortableWalletReservationLocal>

    @Query(
        "SELECT * FROM portable_wallet_reservations " +
            "WHERE operationId = :operationId AND afterImageSha256 = :digest ORDER BY metaId ASC"
    )
    suspend fun forToken(operationId: String, digest: String): List<PortableWalletReservationLocal>

    @Query("SELECT EXISTS(SELECT 1 FROM portable_wallet_reservations WHERE metaId = :metaId)")
    suspend fun contains(metaId: Long): Boolean

    @Query(
        "UPDATE portable_wallet_reservations SET state = 1 " +
            "WHERE operationId = :operationId AND afterImageSha256 = :digest AND state = 0"
    )
    suspend fun markAbandoned(operationId: String, digest: String): Int
}
