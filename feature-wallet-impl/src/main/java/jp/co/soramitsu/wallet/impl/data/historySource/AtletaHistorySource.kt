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

class AtletaHistorySource(
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
                val urlBuilder = StringBuilder(historyUrl).append("addresses/").append(accountAddress).append("/transactions")
                walletOperationsApi.getAtletaOperationsHistory(
                    url = urlBuilder.toString()
                )
            }

        return responseResult.fold(onSuccess = {
            val operations = it.items.map { element ->
                val status = when (element.result) {
                    "success" -> Operation.Status.COMPLETED
                    "error" -> Operation.Status.FAILED
                    else -> Operation.Status.COMPLETED
                }
                Operation(
                    id = element.hash,
                    address = accountAddress,
                    time = parseTimeToMillis(element.timestamp),
                    chainAsset = chainAsset,
                    type = Operation.Type.Transfer(
                        hash = element.hash,
                        myAddress = accountAddress,
                        amount = element.value,
                        receiver = element.to.hash,
                        sender = element.from.hash,
                        status = status,
                        fee = element.fee?.value
                    )
                )
            }

            val nextCursor = (page + 1).toString()
            CursorPage(nextCursor, operations)
        }, onFailure = {
            CursorPage(null, emptyList())
        })

    }

    private val blockScanDateFormat by lazy {
        SimpleDateFormat(
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSSX",
            Locale.getDefault()
        )
    }

    private fun parseTimeToMillis(timestamp: String): Long {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Instant.parse(timestamp).toEpochMilli()
        } else {
            try {
                blockScanDateFormat.parse(timestamp)?.time ?: 0
            } catch (e: Exception) {
                0
            }
        }
    }
}