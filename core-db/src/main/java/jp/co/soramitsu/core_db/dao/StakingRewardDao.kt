package jp.co.soramitsu.core_db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import jp.co.soramitsu.core_db.model.StakingRewardLocal
import kotlinx.coroutines.flow.Flow

@Dao
abstract class StakingRewardDao {

    @Query("SELECT * FROM staking_rewards WHERE accountAddress = :accountAddress")
    abstract fun observeRewards(accountAddress: String): Flow<List<StakingRewardLocal>>

    @Query("SELECT COUNT(*) FROM staking_rewards WHERE accountAddress = :accountAddress")
    abstract suspend fun rewardCount(accountAddress: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insert(rewards: List<StakingRewardLocal>)
}
