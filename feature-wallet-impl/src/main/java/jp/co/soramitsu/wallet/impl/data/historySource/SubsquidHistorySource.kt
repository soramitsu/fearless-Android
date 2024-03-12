package jp.co.soramitsu.wallet.impl.data.historySource

import jp.co.soramitsu.common.data.model.CursorPage
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.shared_utils.runtime.AccountId
import jp.co.soramitsu.wallet.impl.data.network.model.request.SubsquidHistoryRequest
import jp.co.soramitsu.wallet.impl.data.network.subquery.OperationsHistoryApi
import jp.co.soramitsu.wallet.impl.domain.interfaces.TransactionFilter
import jp.co.soramitsu.wallet.impl.domain.model.Operation

class SubsquidHistorySource(
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
        val offset = cursor?.toIntOrNull().takeIf { it != 0 }?.let { "\"$it\"" }
        val response = walletOperationsApi.getSubsquidOperationsHistory(
            url = url,
            SubsquidHistoryRequest(
                accountAddress = accountAddress,
                pageSize,
                offset.toString()
            )
        )

        val operations = response.data.historyElementsConnection.edges.map { it.node }.map {
            val transfer = TransactionFilter.TRANSFER.isAppliedOrNull(filters)?.let { transferApplied ->
                it.transfer?.let { transfer ->
                    Operation(
                        id = it.extrinsicIdx ?: it.id,
                        address = it.address,
                        time = it.timestamp,
                        chainAsset = chainAsset,
                        type = Operation.Type.Transfer(
                            hash = it.extrinsicHash,
                            myAddress = accountAddress,
                            amount = transfer.amount.toBigIntegerOrNull().orZero(),
                            receiver = transfer.to,
                            sender = transfer.from,
                            status = Operation.Status.fromSuccess(it.success),
                            fee = transfer.fee
                        )
                    )
                }
            }
            val reward = TransactionFilter.REWARD.isAppliedOrNull(filters)?.let { rewardApplied ->
                it.reward?.let { reward ->
                    Operation(
                        id = it.id,
                        address = it.address,
                        time = it.timestamp,
                        chainAsset = chainAsset,
                        type = Operation.Type.Reward(
                            amount = reward.amount.toBigIntegerOrNull().orZero(),
                            isReward = true,
                            era = reward.era ?: 0,
                            validator = reward.validator
                        )
                    )
                }
            }
            listOfNotNull(transfer, reward)
        }.flatten()
        val pageInfo = response.data.historyElementsConnection.pageInfo
        val nextCursor = if(pageInfo.hasNextPage && (pageInfo.endCursor.toIntOrNull()
                ?: 0) >= pageSize
        ) {
            pageInfo.endCursor
        } else {
            null
        }
        return CursorPage(nextCursor, operations)
    }

    private fun TransactionFilter.isAppliedOrNull(filters: Collection<TransactionFilter>) = when {
        this in filters -> true
        else -> null
    }
}
