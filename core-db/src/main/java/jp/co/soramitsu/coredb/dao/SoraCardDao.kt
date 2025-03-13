package jp.co.soramitsu.coredb.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import jp.co.soramitsu.coredb.model.SoraCardInfoLocal
import kotlinx.coroutines.flow.Flow

@Dao
interface SoraCardDao {
    @Query("select * from sora_card where :id = id")
    fun observeSoraCardInfo(id: String): Flow<SoraCardInfoLocal?>

    @Query("select * from sora_card where :id = id")
    suspend fun getSoraCardInfo(id: String): SoraCardInfoLocal?

    @Query("update sora_card set kycStatus=:kycStatus where :id = id")
    suspend fun updateKycStatus(id: String, kycStatus: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(soraCardInfoLocal: SoraCardInfoLocal)

    @Query("delete from sora_card")
    suspend fun clearTable()
}
