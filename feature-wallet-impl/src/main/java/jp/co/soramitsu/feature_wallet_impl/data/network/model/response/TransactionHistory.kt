package jp.co.soramitsu.feature_wallet_impl.data.network.model.response


import com.google.gson.annotations.SerializedName
import java.math.BigDecimal

class TransactionHistory(
    val count: Int,
    val transfers: List<Transfer>
)

class Transfer(
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
    val fee: String
)