package jp.co.soramitsu.wallet.impl.data.historySource

import android.os.Build
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Locale
import jp.co.soramitsu.common.data.model.CursorPage
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.shared_utils.runtime.AccountId
import jp.co.soramitsu.wallet.impl.data.network.model.request.ReefHistoryRequest
import jp.co.soramitsu.wallet.impl.data.network.model.request.ReefRequestBuilder
import jp.co.soramitsu.wallet.impl.data.network.subquery.OperationsHistoryApi
import jp.co.soramitsu.wallet.impl.domain.interfaces.TransactionFilter
import jp.co.soramitsu.wallet.impl.domain.model.Operation

class ReefHistorySource(
    private val walletOperationsApi: OperationsHistoryApi,
    private val url: String
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
        val overridePageSize = 50
        val offset = cursor?.toIntOrNull().takeIf { it != 0 }

        val response = walletOperationsApi.getReefOperationsHistory(
            url = url,
            ReefRequestBuilder(
                filters,
                accountAddress = accountAddress,
                overridePageSize,
                offset.toString()
            ).buildRequest()
        )
        val operations = mutableListOf<Operation>()
        if(filters.contains(TransactionFilter.TRANSFER)) {
            operations.addAll(response.data.transfersConnection.edges.map { it.node }.map {
                Operation(
                    id = it.extrinsic?.hash ?: it.id,
                    address = accountAddress,
                    time = parseTimeToMillis(it.timestamp),
                    chainAsset = chainAsset,
                    type = Operation.Type.Transfer(
                        hash = it.extrinsic?.hash,
                        myAddress = accountAddress,
                        amount = it.amount,
                        receiver = it.to.id,
                        sender = it.from.id,
                        status = Operation.Status.fromSuccess(it.success),
                        fee = it.feeAmount
                    )
                )
            })
        }
        if(filters.contains(TransactionFilter.REWARD)) {
            operations.addAll(response.data.stakingsConnection.edges.map { it.node }.map {
                Operation(
                    id = it.id,
                    address = accountAddress,
                    time = parseTimeToMillis(it.timestamp),
                    chainAsset = chainAsset,
                    type = Operation.Type.Reward(
                        amount = it.amount,
                        isReward = true,
                        era = 0,
                        validator = null
                    )
                )
            })
        }

        val transfersPageInfo = response.data.transfersConnection.pageInfo
        val rewardsPageInfo = response.data.stakingsConnection.pageInfo

        val shouldLoadTransfersNextPage = transfersPageInfo.hasNextPage
        val shouldLoadRewardsNextPage = rewardsPageInfo.hasNextPage

        val hasNextPage = shouldLoadTransfersNextPage || shouldLoadRewardsNextPage

        val nextCursor = if(hasNextPage) {
            val transfersOffset = transfersPageInfo.endCursor.toIntOrNull() ?: 0
            val rewardsOffset = rewardsPageInfo.endCursor.toIntOrNull() ?: 0
            val nextOffset = maxOf(transfersOffset, rewardsOffset)
            if(nextOffset >= overridePageSize) nextOffset.toString() else null
        } else {
            null
        }

        return CursorPage(nextCursor, operations.sortedByDescending { it.time })
    }

    private fun TransactionFilter.isAppliedOrNull(filters: Collection<TransactionFilter>) = when {
        this in filters -> true
        else -> null
    }

    private val reefDateFormat by lazy {
        SimpleDateFormat(
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSSZ",
            Locale.getDefault()
        )
    }

    private fun parseTimeToMillis(timestamp: String): Long {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Instant.parse(timestamp).toEpochMilli()
        } else {
            try {
                reefDateFormat.parse(timestamp)?.time ?: 0
            } catch (e: Exception) {
                0
            }
        }
    }
}
