package jp.co.soramitsu.wallet.impl.data.historySource

import jp.co.soramitsu.common.data.model.CursorPage
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.shared_utils.runtime.AccountId
import jp.co.soramitsu.wallet.impl.data.network.subquery.OperationsHistoryApi
import jp.co.soramitsu.wallet.impl.domain.interfaces.TransactionFilter
import jp.co.soramitsu.wallet.impl.domain.model.Operation

class VicscanHistorySource(
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
        val page = cursor?.toInt() ?: 1
        val urlBuilder = StringBuilder(historyUrl).append("transaction/list")

        val responseResult =
            runCatching {
                walletOperationsApi.getVicscanOperationsHistory(
                    url = urlBuilder.toString(),
                    account = accountAddress,
                    limit = pageSize,
                    offset = (page - 1) * pageSize
                )
            }

        return responseResult.fold(onSuccess = {
            val operations = it.data.map { element ->
                val status = when (element.status) {
                    "success" -> Operation.Status.COMPLETED
                    "error" -> Operation.Status.FAILED
                    else -> Operation.Status.COMPLETED
                }
                Operation(
                    id = element.transactionIndex.toString(),
                    address = accountAddress,
                    time = element.timestamp,
                    chainAsset = chainAsset,
                    type = Operation.Type.Transfer(
                        hash = element.hash,
                        myAddress = accountAddress,
                        amount = element.value,
                        receiver = element.to.lowercase(),
                        sender = element.from.lowercase(),
                        status = status,
                        fee = element.gasUsed
                    )
                )
            }

            val nextCursor = (page + 1).toString()
            CursorPage(nextCursor, operations)
        }, onFailure = {
            CursorPage(null, emptyList())
        })
    }
}