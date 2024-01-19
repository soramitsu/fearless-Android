package jp.co.soramitsu.wallet.impl.data.historySource

import jp.co.soramitsu.common.data.model.CursorPage
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.shared_utils.extensions.toHexString
import jp.co.soramitsu.shared_utils.runtime.AccountId
import jp.co.soramitsu.wallet.impl.data.network.subquery.OperationsHistoryApi
import jp.co.soramitsu.wallet.impl.domain.interfaces.TransactionFilter
import jp.co.soramitsu.wallet.impl.domain.model.Operation
import jp.co.soramitsu.wallet.impl.domain.model.planksFromAmount

class OkLinkHistorySource(
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
        val responseResult =
            runCatching {
                val response = walletOperationsApi.getOkLinkOperationsHistory(
                    url = historyUrl,
                    address = accountId.toHexString(true),
                    symbol = chainAsset.symbol.lowercase()
                )

                if (response.code != 0 && response.data.isEmpty()) {
                    throw RuntimeException("OkLink exception: code: ${response.code}, message: ${response.msg}")
                }
                response
            }
        return responseResult.fold(onSuccess = {
            val firstPage = it.data.first()
            val operations = firstPage.transactionLists.map { element ->
                val status = when (element.state) {
                    "success" -> Operation.Status.COMPLETED
                    "fail" -> Operation.Status.FAILED
                    "pending" -> Operation.Status.PENDING
                    else -> throw IllegalArgumentException("Unknown OkLink transaction status: ${element.state}")
                }
                val fee = chainAsset.planksFromAmount(element.txFee)
                val amount = chainAsset.planksFromAmount(element.amount)
                Operation(
                    id = element.txId,
                    address = accountAddress,
                    time = element.transactionTime,
                    chainAsset = chainAsset,
                    type = Operation.Type.Transfer(
                        hash = element.txId,
                        myAddress = accountAddress,
                        amount = amount,
                        receiver = element.to,
                        sender = element.from,
                        status = status,
                        fee = fee
                    )
                )
            }
            CursorPage(null, operations)
        }, onFailure = {
            CursorPage(null, emptyList())
        })
    }

}