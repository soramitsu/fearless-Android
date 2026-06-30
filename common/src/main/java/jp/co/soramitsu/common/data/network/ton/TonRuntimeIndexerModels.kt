package jp.co.soramitsu.common.data.network.ton

import com.google.gson.annotations.SerializedName

data class TonIndexerBalancesResponse(
    @SerializedName("address")
    val address: String = "",
    @SerializedName("ton_raw")
    val tonRaw: String = "0",
    @SerializedName("assets")
    val assets: List<TonIndexerAssetBalance> = emptyList(),
    @SerializedName("updated_at")
    val updatedAt: Long = 0L
)

data class TonIndexerAssetBalance(
    @SerializedName("kind")
    val kind: String = "",
    @SerializedName("symbol")
    val symbol: String? = null,
    @SerializedName("address")
    val address: String? = null,
    @SerializedName("wallet")
    val wallet: String? = null,
    @SerializedName("balance_raw")
    val balanceRaw: String = "0",
    @SerializedName("decimals")
    val decimals: Int = 0
)

data class TonIndexerStateResponse(
    @SerializedName("address")
    val address: String = "",
    @SerializedName("accountState")
    val accountState: String? = null,
    @SerializedName("lastSeenUtime")
    val lastSeenUtime: Long? = null,
    @SerializedName("lastConfirmedSeqno")
    val lastConfirmedSeqno: Int? = null
)

data class TonIndexerRunGetMethodRequest(
    @SerializedName("address")
    val address: String,
    @SerializedName("method")
    val method: String,
    @SerializedName("stack")
    val stack: List<List<Any?>> = emptyList()
)

data class TonIndexerRunGetMethodResponse(
    @SerializedName("exit_code")
    val exitCode: Int = 0,
    @SerializedName("gas_used")
    val gasUsed: Long = 0L,
    @SerializedName("stack")
    val stack: List<List<Any?>> = emptyList()
)

data class TonIndexerTransactionsResponse(
    @SerializedName("page")
    val page: Int = 1,
    @SerializedName("page_size")
    val pageSize: Int = 0,
    @SerializedName("total_txs")
    val totalTxs: Int = 0,
    @SerializedName("total_pages")
    val totalPages: Int? = null,
    @SerializedName("history_complete")
    val historyComplete: Boolean = false,
    @SerializedName("txs")
    val txs: List<TonIndexerTransaction> = emptyList()
)

data class TonIndexerTransaction(
    @SerializedName("txId")
    val txId: String = "",
    @SerializedName("utime")
    val utime: Long = 0L,
    @SerializedName("status")
    val status: String = "",
    @SerializedName("reason")
    val reason: String? = null,
    @SerializedName("txType")
    val txType: String = "",
    @SerializedName("inSource")
    val inSource: String? = null,
    @SerializedName("inValue")
    val inValue: String? = null,
    @SerializedName("detail")
    val detail: TonIndexerTransactionDetail? = null,
    @SerializedName("lt")
    val lt: String = "",
    @SerializedName("hash")
    val hash: String = "",
    @SerializedName("fee")
    val fee: String? = null,
    @SerializedName("totalFees")
    val totalFees: String? = null,
    @SerializedName("inMessage")
    val inMessage: TonIndexerMessage? = null,
    @SerializedName("outMessages")
    val outMessages: List<TonIndexerMessage>? = null
)

data class TonIndexerTransactionDetail(
    @SerializedName("kind")
    val kind: String = "",
    @SerializedName("asset")
    val asset: String? = null,
    @SerializedName("amount")
    val amount: String? = null
)

data class TonIndexerMessage(
    @SerializedName("source")
    val source: String? = null,
    @SerializedName("destination")
    val destination: String? = null,
    @SerializedName("value")
    val value: String? = null
)
