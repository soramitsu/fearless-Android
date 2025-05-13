package jp.co.soramitsu.wallet.impl.data.network.model.response

import com.google.gson.annotations.SerializedName
import java.math.BigInteger
import jp.co.soramitsu.common.data.network.runtime.binding.BlockNumber

data class ZchainHistoryResponse(
    val data: List<ZchainHistoryItem>,
    val total: Int
)

data class ZchainHistoryItem(
    @SerializedName("s")
    val success: Boolean,
    @SerializedName("h")
    val hash: String,
    @SerializedName("ti")
    val timestamp: Long,
    @SerializedName("bn")
    val blockNumber: BlockNumber,
    @SerializedName("f")
    val from: ZchainHistoryAddress,
    @SerializedName("f")
    val to: ZchainHistoryAddress,
    @SerializedName("v")
    val value: BigInteger,
    @SerializedName("gl")
    val gas: BigInteger,
    @SerializedName("tf")
    val fee: BigInteger,
    @SerializedName("gu")
    val gasUsed: BigInteger,
    @SerializedName("gp")
    val gasPrice: BigInteger
)

data class ZchainHistoryAddress(
    @SerializedName("a")
    val address: String
)
