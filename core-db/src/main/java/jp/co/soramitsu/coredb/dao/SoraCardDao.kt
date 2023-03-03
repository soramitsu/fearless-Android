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
    fun getSoraCardInfo(id: String): Flow<SoraCardInfoLocal?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(soraCardInfoLocal: SoraCardInfoLocal)

    @Query("delete from sora_card")
    suspend fun clearTable()
}
