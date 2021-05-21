package jp.co.soramitsu.core_db.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.math.BigInteger

@Entity(tableName = "total_reward")
data class TotalRewardLocal(
    @PrimaryKey
    val accountAddress: String,
    val totalReward: BigInteger?
)
