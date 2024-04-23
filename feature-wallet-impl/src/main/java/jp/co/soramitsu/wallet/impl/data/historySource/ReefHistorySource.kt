package jp.co.soramitsu.wallet.impl.data.historySource

import android.os.Build
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Locale
import jp.co.soramitsu.common.data.model.CursorPage
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.shared_utils.runtime.AccountId
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

        var shouldLoadTransfersNextPage = false
        var shouldLoadRewardsNextPage = false
        var shouldLoadExtrinsicsNextPage = false
        var transfersResultOffset = 0
        var rewardsResultOffset = 0
        var extrinsicsResultOffset = 0

        val operations = mutableListOf<Operation>()
        if (filters.contains(TransactionFilter.TRANSFER)) {
            val transfersResponse = walletOperationsApi.getReefOperationsHistory(
                url = url,
                body = ReefRequestBuilder(
                    filters = setOf(TransactionFilter.TRANSFER),
                    accountAddress = accountAddress,
                    limit = overridePageSize,
                    transfersOffset = offset?.toString()
                ).buildRequest()
            )

            operations.addAll(transfersResponse.data.transfersConnection?.edges?.map { it.node }?.map {
                Operation(
                    id = it.id ?: it.extrinsicHash ?: it.hashCode().toString(),
                    address = accountAddress,
                    time = parseTimeToMillis(it.timestamp),
                    chainAsset = chainAsset,
                    type = Operation.Type.Transfer(
                        hash = it.extrinsicHash,
                        myAddress = accountAddress,
                        amount = it.amount,
                        receiver = it.to.id,
                        sender = it.from.id,
                        status = Operation.Status.fromSuccess(it.success),
                        fee = it.signedData?.fee?.partialFee
                    )
                )
            }.orEmpty())

            shouldLoadTransfersNextPage = transfersResponse.data.transfersConnection?.pageInfo?.hasNextPage ?: false
            transfersResultOffset = transfersResponse.data.transfersConnection?.pageInfo?.endCursor?.toIntOrNull() ?: 0
        }

        if (filters.contains(TransactionFilter.REWARD)) {
            val stakingsResponse = walletOperationsApi.getReefOperationsHistory(
                url = url,
                body = ReefRequestBuilder(
                    filters = setOf(TransactionFilter.REWARD),
                    accountAddress = accountAddress,
                    limit = overridePageSize,
                    transfersOffset = offset?.toString()
                ).buildRequest()
            )

            operations.addAll(stakingsResponse.data.stakingsConnection?.edges?.map { it.node }?.map {
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
            }.orEmpty())

            shouldLoadRewardsNextPage = stakingsResponse.data.stakingsConnection?.pageInfo?.hasNextPage ?: false
            rewardsResultOffset = stakingsResponse.data.stakingsConnection?.pageInfo?.endCursor?.toIntOrNull() ?: 0
        }

        if (filters.contains(TransactionFilter.EXTRINSIC)) {
            val extrinsicsResponse = walletOperationsApi.getReefOperationsHistory(
                url = url,
                body = ReefRequestBuilder(
                    filters = setOf(TransactionFilter.EXTRINSIC),
                    accountAddress = accountAddress,
                    limit = overridePageSize,
                    transfersOffset = offset?.toString()
                ).buildRequest()
            )

            operations.addAll(extrinsicsResponse.data.extrinsicsConnection?.edges?.map { it.node }?.map {
                Operation(
                    id = it.id,
                    address = accountAddress,
                    time = parseTimeToMillis(it.timestamp),
                    chainAsset = chainAsset,
                    type = Operation.Type.Extrinsic(
                        hash = it.hash,
                        module = it.section,
                        call = it.method,
                        fee = it.signedData?.fee?.partialFee.orZero(),
                        status = Operation.Status.fromSuccess(it.status == "success")
                    )
                )
            }.orEmpty())

            shouldLoadExtrinsicsNextPage = extrinsicsResponse.data.extrinsicsConnection?.pageInfo?.hasNextPage ?: false
            extrinsicsResultOffset = extrinsicsResponse.data.extrinsicsConnection?.pageInfo?.endCursor?.toIntOrNull() ?: 0
        }

        val hasNextPage = shouldLoadTransfersNextPage || shouldLoadRewardsNextPage || shouldLoadExtrinsicsNextPage

        val nextCursor = if (hasNextPage) {
            val nextOffset = maxOf(transfersResultOffset, rewardsResultOffset, extrinsicsResultOffset)
            if (nextOffset >= overridePageSize) nextOffset.toString() else null
        } else {
            null
        }

        return CursorPage(nextCursor, operations.sortedByDescending { it.time })
    }

    private val reefDateFormat by lazy {
        SimpleDateFormat(
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSSZ",
            Locale.getDefault()
        )
    }

    private fun parseTimeToMillis(timestamp: String): Long {
        return Instant.parse(timestamp).toEpochMilli()
    }
}
