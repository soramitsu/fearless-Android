package jp.co.soramitsu.feature_staking_impl.data.network.subscan.response

import com.google.gson.annotations.SerializedName
import java.math.BigInteger

class StakingRewardHistory(
    val count: Int,
    @SerializedName("list")
    val rewards: List<StakingRewardRemote>?,
)

class StakingRewardRemote(
    @SerializedName("event_index")
    val eventIndex: String,
    @SerializedName("block_num")
    val blockNumber: Long,
    @SerializedName("extrinsic_idx")
    val extrinsicIndex: Int,
    @SerializedName("module_id")
    val moduleId: String,
    @SerializedName("event_id")
    val eventId: String,
    val params: String,
    @SerializedName("extrinsic_hash")
    val extrinsicHash: String,
    @SerializedName("event_idx")
    val eventIdx: Int,
    val amount: BigInteger,
    @SerializedName("block_timestamp")
    val blockTimestamp: Long,
    @SerializedName("slash_kton")
    val slashKton: String,
)
