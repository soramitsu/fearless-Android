package jp.co.soramitsu.common.data.network.ton

import com.google.gson.annotations.SerializedName

data class TonHealthStatus(
    @SerializedName("lastMasterSeqno")
    val lastMasterSeqno: Long? = null,
    @SerializedName("indexerLagSec")
    val indexerLagSec: Double? = null,
    @SerializedName("liteserverPoolStatus")
    val liteserverPoolStatus: String? = null
)

data class TonContractsResponse(
    @SerializedName("network")
    val network: String? = null,
    @SerializedName("count")
    val count: Int,
    @SerializedName("contracts")
    val contracts: Map<String, String> = emptyMap()
)

data class TonIndexerServiceInfo(
    @SerializedName("schemaVersion")
    val schemaVersion: Int,
    @SerializedName("serviceId")
    val serviceId: String,
    @SerializedName("serviceName")
    val serviceName: String,
    @SerializedName("ecosystem")
    val ecosystem: String,
    @SerializedName("chainId")
    val chainId: String,
    @SerializedName("network")
    val network: String,
    @SerializedName("publicBaseUrl")
    val publicBaseUrl: String,
    @SerializedName("readOnly")
    val readOnly: Boolean,
    @SerializedName("capabilities")
    val capabilities: List<String> = emptyList(),
    @SerializedName("endpoints")
    val endpoints: Map<String, String> = emptyMap()
)

fun TonIndexerServiceInfo.isExpectedTiServiceInfo(): Boolean {
    return schemaVersion == 1 &&
        serviceId == "ti.soramitsu.io" &&
        ecosystem == "ton" &&
        chainId == "ton:mainnet" &&
        publicBaseUrl == "https://ti.soramitsu.io" &&
        readOnly
}

data class TonBalanceResponse(
    @SerializedName("ton")
    val ton: TonNativeBalance,
    @SerializedName("jettons")
    val jettons: List<TonJettonBalance> = emptyList(),
    @SerializedName("confirmed")
    val confirmed: Boolean,
    @SerializedName("updated_at")
    val updatedAt: Long,
    @SerializedName("network")
    val network: String
)

data class TonNativeBalance(
    @SerializedName("balance")
    val balance: String,
    @SerializedName("last_tx_lt")
    val lastTxLt: String? = null,
    @SerializedName("last_tx_hash")
    val lastTxHash: String? = null
)

data class TonJettonBalance(
    @SerializedName("master")
    val master: String,
    @SerializedName("wallet")
    val wallet: String,
    @SerializedName("balance")
    val balance: String,
    @SerializedName("decimals")
    val decimals: Int? = null,
    @SerializedName("symbol")
    val symbol: String? = null
)

data class TonBalancesResponse(
    @SerializedName("address")
    val address: String,
    @SerializedName("ton_raw")
    val tonRaw: String,
    @SerializedName("ton")
    val ton: String,
    @SerializedName("assets")
    val assets: List<TonAssetBalance> = emptyList(),
    @SerializedName("confirmed")
    val confirmed: Boolean,
    @SerializedName("updated_at")
    val updatedAt: Long,
    @SerializedName("network")
    val network: String
)

data class TonAssetBalance(
    @SerializedName("kind")
    val kind: String,
    @SerializedName("symbol")
    val symbol: String? = null,
    @SerializedName("address")
    val address: String? = null,
    @SerializedName("wallet")
    val wallet: String? = null,
    @SerializedName("balance_raw")
    val balanceRaw: String,
    @SerializedName("balance")
    val balance: String,
    @SerializedName("decimals")
    val decimals: Int
)

data class TonAccountStateResponse(
    @SerializedName("address")
    val address: String,
    @SerializedName("balance")
    val balance: String,
    @SerializedName("lastTxLt")
    val lastTxLt: String? = null,
    @SerializedName("lastTxHash")
    val lastTxHash: String? = null,
    @SerializedName("accountState")
    val accountState: String? = null,
    @SerializedName("codeBoc")
    val codeBoc: String? = null,
    @SerializedName("dataBoc")
    val dataBoc: String? = null,
    @SerializedName("updatedAt")
    val updatedAt: Long
)

data class TonJettonTransferPayloadResponse(
    @SerializedName("custom_payload")
    val customPayload: String? = null,
    @SerializedName("state_init")
    val stateInit: String? = null
)

data class TonTransactionsResponse(
    @SerializedName("page")
    val page: Int,
    @SerializedName("page_size")
    val pageSize: Int,
    @SerializedName("total_txs")
    val totalTxs: Int,
    @SerializedName("total_pages")
    val totalPages: Int? = null,
    @SerializedName("total_pages_min")
    val totalPagesMin: Int,
    @SerializedName("history_complete")
    val historyComplete: Boolean,
    @SerializedName("txs")
    val txs: List<TonTransactionEntry> = emptyList(),
    @SerializedName("network")
    val network: String
)

data class TonTransactionEntry(
    @SerializedName("txId")
    val txId: String,
    @SerializedName("utime")
    val utime: Long,
    @SerializedName("status")
    val status: String,
    @SerializedName("reason")
    val reason: String? = null,
    @SerializedName("txType")
    val txType: String,
    @SerializedName("inSource")
    val inSource: String? = null,
    @SerializedName("inValue")
    val inValue: String? = null,
    @SerializedName("outCount")
    val outCount: Int,
    @SerializedName("detail")
    val detail: Map<String, Any?> = emptyMap(),
    @SerializedName("kind")
    val kind: String,
    @SerializedName("actions")
    val actions: List<Map<String, Any?>> = emptyList(),
    @SerializedName("lt")
    val lt: String,
    @SerializedName("hash")
    val hash: String,
    @SerializedName("inMessage")
    val inMessage: TonMessageSummary? = null,
    @SerializedName("outMessages")
    val outMessages: List<TonMessageSummary> = emptyList()
)

data class TonMessageSummary(
    @SerializedName("source")
    val source: String? = null,
    @SerializedName("destination")
    val destination: String? = null,
    @SerializedName("value")
    val value: String? = null,
    @SerializedName("op")
    val op: Long? = null,
    @SerializedName("body")
    val body: String? = null
)

data class TonSwapsResponse(
    @SerializedName("address")
    val address: String,
    @SerializedName("swaps")
    val swaps: List<TonSwapExecution> = emptyList(),
    @SerializedName("count")
    val count: Int,
    @SerializedName("network")
    val network: String
)

data class TonSwapExecution(
    @SerializedName("txId")
    val txId: String,
    @SerializedName("lt")
    val lt: String,
    @SerializedName("hash")
    val hash: String,
    @SerializedName("utime")
    val utime: Long,
    @SerializedName("status")
    val status: String,
    @SerializedName("reason")
    val reason: String? = null,
    @SerializedName("payToken")
    val payToken: String? = null,
    @SerializedName("receiveToken")
    val receiveToken: String? = null,
    @SerializedName("payAmount")
    val payAmount: String? = null,
    @SerializedName("receiveAmount")
    val receiveAmount: String? = null,
    @SerializedName("queryId")
    val queryId: String? = null,
    @SerializedName("executionType")
    val executionType: String? = null
)

data class TonRunGetMethodRequest(
    @SerializedName("address")
    val address: String,
    @SerializedName("method")
    val method: String,
    @SerializedName("stack")
    val stack: List<List<Any?>> = emptyList()
)

data class TonRunGetMethodsRequest(
    @SerializedName("calls")
    val calls: List<TonRunGetMethodRequest>
)

data class TonRunGetMethodResponse(
    @SerializedName("exit_code")
    val exitCode: Int,
    @SerializedName("gas_used")
    val gasUsed: Long,
    @SerializedName("stack")
    val stack: List<List<Any?>> = emptyList()
)

data class TonRunGetMethodsResponse(
    @SerializedName("results")
    val results: List<TonRunGetMethodBatchResult> = emptyList()
)

data class TonRunGetMethodBatchResult(
    @SerializedName("ok")
    val ok: Boolean,
    @SerializedName("exit_code")
    val exitCode: Int? = null,
    @SerializedName("gas_used")
    val gasUsed: Long? = null,
    @SerializedName("stack")
    val stack: List<List<Any?>> = emptyList(),
    @SerializedName("code")
    val code: String? = null,
    @SerializedName("error")
    val error: String? = null
)
