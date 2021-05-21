package jp.co.soramitsu.core_db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import jp.co.soramitsu.core_db.model.StakingRewardLocal
import jp.co.soramitsu.core_db.model.TotalRewardLocal
import kotlinx.coroutines.flow.Flow

@Dao
abstract class StakingRewardDao {

    @Query("SELECT * FROM staking_rewards WHERE accountAddress = :accountAddress")
    abstract fun observeRewards(accountAddress: String): Flow<List<StakingRewardLocal>>

    @Query("SELECT COUNT(*) FROM staking_rewards WHERE accountAddress = :accountAddress")
    abstract suspend fun rewardCount(accountAddress: String): Int

    @Query("SELECT * FROM total_reward WHERE accountAddress = :accountAddress")
    abstract fun observeTotalRewards(accountAddress: String): Flow<TotalRewardLocal>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(totalRewardLocal: TotalRewardLocal)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insert(rewards: List<StakingRewardLocal>)
}
