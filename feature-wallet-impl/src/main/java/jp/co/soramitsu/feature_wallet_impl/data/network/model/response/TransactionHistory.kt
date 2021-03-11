package jp.co.soramitsu.feature_wallet_impl.data.network.model.response

import com.google.gson.annotations.SerializedName
import java.math.BigDecimal
import java.math.BigInteger
import java.util.concurrent.TimeUnit

class TransactionHistory(
    val count: Int,
    val transfers: List<TransactionRemote>?
)

class TransactionRemote(
    val from: String,
    val to: String,
    @SerializedName("extrinsic_index")
    val extrinsicIndex: String,
    val success: Boolean,
    val hash: String,
    @SerializedName("block_num")
    val blockNum: Int,
    @SerializedName("block_timestamp")
    val blockTimestamp: Long,
    val module: String,
    val amount: BigDecimal,
    @SerializedName("fee")
    val feeInPlanks: BigInteger
) {
    val timeInMillis: Long
        get() = TimeUnit.SECONDS.toMillis(blockTimestamp)
}