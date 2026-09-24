package jp.co.soramitsu.wallet.impl.data.historySource

import jp.co.soramitsu.common.data.model.CursorPage
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.wallet.impl.data.network.subquery.OperationsHistoryApi
import jp.co.soramitsu.wallet.impl.domain.interfaces.TransactionFilter
import jp.co.soramitsu.wallet.impl.domain.model.Operation

class KlaytnHistorySource(
    private val walletOperationsApi: OperationsHistoryApi,
    private val historyUrl: String
) : HistorySource {
    override suspend fun getOperations(
        pageSize: Int,
        cursor: String?,
        filters: Set<TransactionFilter>,
        accountId: AccountId,
        chain: Chain,
        chainAsset: Asset,
        accountAddress: String
    ): CursorPage<Operation> {
        if (pageSize <= 0 || TransactionFilter.TRANSFER !in filters) return CursorPage(null, emptyList())
        val page = cursor?.toIntOrNull() ?: 1
        require(cursor == null || (cursor.toIntOrNull() != null && page > 0)) { "Invalid Klaytn history cursor" }
        val urlBuilder = StringBuilder(historyUrl)
            .append("accounts/")
            .append(accountAddress)
            .append("/txs")
        val response = walletOperationsApi.getKlaytnOperationsHistory(
            url = urlBuilder.toString(),
            page = page
        )
        check(response.success && response.page == page && response.total >= 0) {
            "Klaytn history provider rejected or malformed the request"
        }
        val operations = response.result.map { element ->
            val status = if (element.txStatus == 1) Operation.Status.COMPLETED else Operation.Status.FAILED
            Operation(
                id = element.txHash,
                address = accountAddress,
                time = element.createdAt,
                chainAsset = chainAsset,
                type = Operation.Type.Transfer(
                    hash = element.txHash,
                    myAddress = accountAddress,
                    amount = element.amount,
                    receiver = element.toAddress.lowercase(),
                    sender = element.fromAddress.lowercase(),
                    status = status,
                    fee = element.txFee
                )
            )
        }
        val nextCursor = if (response.result.isNotEmpty() && page < Int.MAX_VALUE) (page + 1).toString() else null
        return CursorPage(nextCursor, operations)
    }
}
