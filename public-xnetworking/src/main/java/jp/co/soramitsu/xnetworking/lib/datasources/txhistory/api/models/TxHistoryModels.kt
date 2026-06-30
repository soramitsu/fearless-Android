package jp.co.soramitsu.xnetworking.lib.datasources.txhistory.api.models

data class TxHistoryInfo(
    val endCursor: String?,
    val endReached: Boolean,
    val items: List<TxHistoryItem>
)

data class TxHistoryItem(
    val id: String,
    val blockHash: String,
    val module: String,
    val method: String,
    val timestamp: String,
    val networkFee: String,
    val success: Boolean,
    val data: List<TxHistoryItemParam>?,
    val nestedData: List<TxHistoryItemNested>?
)

data class TxHistoryItemParam(
    val paramName: String,
    val paramValue: String
)

data class TxHistoryItemNested(
    val module: String,
    val method: String,
    val hash: String,
    val data: List<TxHistoryItemParam>
)
