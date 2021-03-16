package jp.co.soramitsu.core_db.model

import androidx.room.Entity
import androidx.room.Index
import java.math.BigInteger

@Entity(
    tableName = "staking_rewards",
    primaryKeys = ["accountAddress", "blockNumber", "extrinsicIndex"],
    indices = [
        Index(value = ["accountAddress"])
    ]
)
class StakingRewardLocal(
    val accountAddress: String,
    val eventId: String,
    val blockNumber: Long,
    val extrinsicIndex: Int,
    val extrinsicHash: String,
    val moduleId: String,
    val eventIndex: String,
    val amountInPlanks: BigInteger,
    val blockTimestamp: Long,
    val slashKton: String,
)
