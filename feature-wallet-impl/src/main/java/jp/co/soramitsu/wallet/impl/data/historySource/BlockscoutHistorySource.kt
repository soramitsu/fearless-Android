package jp.co.soramitsu.wallet.impl.data.historySource

import android.os.Build
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Locale
import jp.co.soramitsu.common.data.model.CursorPage
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.shared_utils.extensions.toHexString
import jp.co.soramitsu.shared_utils.runtime.AccountId
import jp.co.soramitsu.wallet.impl.data.network.subquery.OperationsHistoryApi
import jp.co.soramitsu.wallet.impl.domain.interfaces.TransactionFilter
import jp.co.soramitsu.wallet.impl.domain.model.Operation
import jp.co.soramitsu.wallet.impl.domain.model.planksFromAmount

class BlockscoutHistorySource(
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
                val urlBuilder = StringBuilder(historyUrl).append("addresses/").append(accountId.toHexString(true)).apply {
                    when (chainAsset.ethereumType) {
                        Asset.EthereumType.NORMAL -> {
                            this.append("/transactions").toString()
                        }
                        else -> {
                            this.append("/token-transfers?token=${chainAsset.id}").toString()
                        }
                    }
                }

                walletOperationsApi.getZetaOperationsHistory(
                    url = urlBuilder.toString()
                )
            }

        return responseResult.fold(onSuccess = {
            val operations = it.items.map { element ->
                val status = when (element.status) {
                    "success" -> Operation.Status.COMPLETED
                    "error" -> Operation.Status.FAILED
                    else -> Operation.Status.COMPLETED
                }
                val hash = element.hash ?: element.txHash ?: ""
                val amount = element.value ?: chainAsset.planksFromAmount(element.total?.value.orZero())
                Operation(
                    id = hash,
                    address = accountAddress,
                    time = parseTimeToMillis(element.timestamp),
                    chainAsset = chainAsset,
                    type = Operation.Type.Transfer(
                        hash = element.hash,
                        myAddress = accountAddress,
                        amount = amount,
                        receiver = element.to.hash.lowercase(),
                        sender = element.from.hash.lowercase(),
                        status = status,
                        fee = element.fee?.value
                    )
                )
            }
            CursorPage(null, operations)
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