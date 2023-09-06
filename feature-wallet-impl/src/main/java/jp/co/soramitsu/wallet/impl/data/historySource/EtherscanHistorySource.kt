package jp.co.soramitsu.wallet.impl.data.historySource

import jp.co.soramitsu.common.data.model.CursorPage
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.shared_utils.extensions.toHexString
import jp.co.soramitsu.shared_utils.runtime.AccountId
import jp.co.soramitsu.wallet.impl.data.network.subquery.OperationsHistoryApi
import jp.co.soramitsu.wallet.impl.domain.interfaces.TransactionFilter
import jp.co.soramitsu.wallet.impl.domain.model.Operation
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class EtherscanHistorySource(
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
        return kotlin.runCatching {
            walletOperationsApi.getEtherscanOperationsHistory(
                url = historyUrl,
                address = accountId.toHexString(true)
            )
        }.fold(onSuccess = {
            val operations = it.result.map { element ->
                val status = if (element.isError == 0) {
                    Operation.Status.COMPLETED
                } else {
                    Operation.Status.FAILED
                }
                val fee = element.gasUsed.multiply(element.gasPrice)
                Operation(
                    id = element.hash,
                    address = accountAddress,
                    time = element.timeStamp.toDuration(DurationUnit.SECONDS).inWholeMilliseconds,
                    chainAsset = chainAsset,
                    type = Operation.Type.Transfer(
                        hash = element.hash,
                        myAddress = accountAddress,
                        amount = element.value,
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