package jp.co.soramitsu.core_db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import jp.co.soramitsu.core_db.model.TotalRewardLocal
import kotlinx.coroutines.flow.Flow

@Dao
abstract class StakingTotalRewardDao {

    @Query("SELECT * FROM total_reward WHERE accountAddress = :accountAddress")
    abstract fun observeTotalRewards(accountAddress: String): Flow<TotalRewardLocal>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(totalRewardLocal: TotalRewardLocal)
}
