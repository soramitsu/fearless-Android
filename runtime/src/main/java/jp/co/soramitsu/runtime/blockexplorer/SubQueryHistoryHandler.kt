package jp.co.soramitsu.runtime.blockexplorer

import jp.co.soramitsu.xnetworking.subquery.SubQueryClient
import jp.co.soramitsu.xnetworking.subquery.history.SubQueryHistoryItem
import jp.co.soramitsu.xnetworking.subquery.history.SubQueryHistoryResult
import jp.co.soramitsu.xnetworking.subquery.history.fearless.FearlessSubQueryResponse

class SubQueryHistoryHandler(
    private val subQueryClient: SubQueryClient<FearlessSubQueryResponse, SubQueryHistoryItem>,
) {

    suspend fun getHistoryPage(
        address: String,
        networkName: String,
        pageNumber: Long,
        url: String,
        modulesName: List<String>?
    ): SubQueryHistoryResult<SubQueryHistoryItem> {
        return subQueryClient.getTransactionHistoryPaged(
            address = address,
            networkName = networkName,
            page = pageNumber,
            url = url,
            filter = { historyItem ->
                modulesName?.map { it.lowercase() }?.contains(historyItem.module) ?: true
            }
        )
    }

    fun getTransferContacts(query: String, networkName: String): List<String> =
        subQueryClient.getTransactionPeers(query, networkName)
}
