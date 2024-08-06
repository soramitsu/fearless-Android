package jp.co.soramitsu.wallet.impl.data.historySource

import android.os.Build
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Locale
import jp.co.soramitsu.common.data.model.CursorPage
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.shared_utils.runtime.AccountId
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
        val page = cursor?.toInt() ?: 1
        val responseResult =
            runCatching {
                val url = historyUrl.replace("{address}", accountAddress)
                walletOperationsApi.getKlaytnOperationsHistory(
                    url = url,
                    page = page
                )
            }

        return responseResult.fold(onSuccess = {
            val operations = it.result.map { element ->
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

            val nextCursor = (page + 1).toString()
            CursorPage(nextCursor, operations)
        }, onFailure = {
            CursorPage(null, emptyList())
        })
    }
}