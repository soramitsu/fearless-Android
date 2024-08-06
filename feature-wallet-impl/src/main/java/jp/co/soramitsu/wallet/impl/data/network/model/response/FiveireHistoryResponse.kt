package jp.co.soramitsu.wallet.impl.data.network.model.response

import com.google.gson.annotations.SerializedName
import java.math.BigInteger
import jp.co.soramitsu.common.data.network.runtime.binding.BlockNumber

data class FiveireHistoryResponse(
    val message: String,
    val data: FiveireHistoryData,
    val page: Int,
    val total: Int
)

data class FiveireHistoryData(
    val count: Int,
    val transactions: List<FiveireHistoryItem>
)

data class FiveireHistoryItem(
    val hash: String,
    @SerializedName("receipt_status")
    val status: Int,
    @SerializedName("created_at")
    val createdAt: String,
    @SerializedName("block_number")
    val blockNumber: BlockNumber,
    @SerializedName("from_address")
    val fromAddress: String,
    @SerializedName("to_address")
    val toAddress: String?,
    val value: BigInteger,
    val gas: BigInteger,
    @SerializedName("receipt_cumulative_gas_used")
    val gasUsed: BigInteger,
    @SerializedName("gas_price")
    val gasPrice: BigInteger
)
