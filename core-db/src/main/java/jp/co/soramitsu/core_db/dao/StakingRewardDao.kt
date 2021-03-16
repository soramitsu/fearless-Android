package jp.co.soramitsu.core_db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import jp.co.soramitsu.core_db.model.StakingRewardLocal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

@Dao
abstract class StakingRewardDao {

    @Query("SELECT * FROM staking_rewards WHERE accountAddress = :accountAddress")
    abstract fun observeRewards(accountAddress: String) : Flow<List<StakingRewardLocal>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insert(rewards: List<StakingRewardLocal>): List<Long>

    /**
     * @return true if collision were found. false - otherwise
     */
    suspend fun upsert(rewards: List<StakingRewardLocal>) = withContext(Dispatchers.Default) {
        val ids = insert(rewards)

        ids.any { it == -1L }
    }
}
