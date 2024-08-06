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

class FiveireHistorySource(
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
                walletOperationsApi.getFiveireOperationsHistory(
                    url = url,
                    page = page,
                    limit = pageSize
                )
            }

        return responseResult.fold(onSuccess = {
            val operations = it.data.transactions.mapNotNull { element ->
                if (element.toAddress.isNullOrEmpty()) return@mapNotNull null
                val status = if (element.status == 1) Operation.Status.COMPLETED else Operation.Status.FAILED
                Operation(
                    id = element.hash,
                    address = accountAddress,
                    time = parseTimeToMillis(element.createdAt),
                    chainAsset = chainAsset,
                    type = Operation.Type.Transfer(
                        hash = element.hash,
                        myAddress = accountAddress,
                        amount = element.value,
                        receiver = element.toAddress.lowercase(),
                        sender = element.fromAddress.lowercase(),
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