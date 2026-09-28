package jp.co.soramitsu.coredb.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import jp.co.soramitsu.coredb.model.WalletCustodyLocal

@Dao
interface WalletCustodyDao {
    @Query("SELECT * FROM wallet_custody WHERE metaId = :metaId")
    suspend fun get(metaId: Long): WalletCustodyLocal?

    /** Never replace a WATCH marker through a generic upsert. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(marker: WalletCustodyLocal)

    @Query("DELETE FROM wallet_custody WHERE metaId = :metaId")
    suspend fun delete(metaId: Long): Int
}
