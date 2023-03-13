package jp.co.soramitsu.wallet.impl.data.historySource

import jp.co.soramitsu.common.data.model.CursorPage
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
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
        chainAsset: Chain.Asset,
        accountAddress: String
    ): CursorPage<Operation> {
        val page = cursor?.toIntOrNull() ?: 0
        val response = walletOperationsApi.getSubsquidOperationsHistory(
            url = url,
            SubsquidHistoryRequest(
                accountAddress = accountAddress,
                pageSize,
                pageSize * page
            )
        )

        val operations = response.data.historyElements.map {
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
                            status = Operation.Status.fromSuccess(transfer.success),
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
                            isReward = reward.isReward,
                            era = reward.era ?: 0,
                            validator = reward.validator
                        )
                    )
                }
            }
            val extrinsic = TransactionFilter.EXTRINSIC.isAppliedOrNull(filters)?.let { extrinsicApplied ->
                it.extrinsic?.let { extrinsic ->
                    Operation(
                        id = it.id,
                        address = it.address,
                        time = it.timestamp,
                        chainAsset = chainAsset,
                        type = Operation.Type.Extrinsic(
                            hash = extrinsic.hash,
                            module = extrinsic.module,
                            call = extrinsic.call,
                            fee = extrinsic.fee.toBigIntegerOrNull().orZero(),
                            status = Operation.Status.fromSuccess(extrinsic.success)
                        )
                    )
                }
            }
            listOfNotNull(transfer, reward, extrinsic)
        }.flatten()
        return CursorPage(page.inc().toString(), operations)
    }

    private fun TransactionFilter.isAppliedOrNull(filters: Collection<TransactionFilter>) = when {
        this in filters -> true
        else -> null
    }
}
