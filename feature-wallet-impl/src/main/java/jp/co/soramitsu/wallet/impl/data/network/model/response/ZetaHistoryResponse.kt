package jp.co.soramitsu.wallet.impl.data.network.model.response

import com.google.gson.annotations.SerializedName
import java.math.BigDecimal
import java.math.BigInteger

data class ZetaHistoryResponse(
    val items: List<ZetaHistoryItem>,
    @SerializedName("next_page_params")
    val nextPageParams: NextPageParams? = null
)

data class NextPageParams(
    @SerializedName("block_number")
    val blockNumber: BigInteger,
    val index: Long,
    @SerializedName("items_count")
    val itemsCount: Long
)

data class ZetaHistoryItem(
    val timestamp: String,
    val fee: ZetaFee? = null,
    val status: String,
    val to: ZetaAddress,
    val from: ZetaAddress,
    val value: BigInteger? = null,
    val total: ZetaTotal? = null,
    val hash: String? = null,
    @SerializedName("tx_hash")
    val txHash: String? = null,
)

data class ZetaTotal(
    val value: BigDecimal,
    val decimals: Int
)

data class ZetaAddress(
    val hash: String
)

data class ZetaFee(
    val type: String,
    val value: BigInteger
)