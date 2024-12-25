package jp.co.soramitsu.wallet.impl.data.historySource

import jp.co.soramitsu.common.data.model.CursorPage
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.shared_utils.runtime.AccountId
import jp.co.soramitsu.wallet.impl.data.mappers.toOperation
import jp.co.soramitsu.wallet.impl.domain.interfaces.TransactionFilter
import jp.co.soramitsu.wallet.impl.domain.model.Operation
import jp.co.soramitsu.xnetworking.lib.datasources.txhistory.api.TxHistoryRepository
import jp.co.soramitsu.xnetworking.lib.datasources.txhistory.api.models.ChainInfo
import jp.co.soramitsu.xnetworking.lib.datasources.txhistory.api.models.TxFilter

class SoraHistorySource(
    private val txHistoryRepository: TxHistoryRepository,
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
        val soraHistory = runCatching {
            txHistoryRepository.getTransactionHistoryPaged(
                address = accountAddress,
                page = cursor?.toLong() ?: 1,
                pageCount = pageSize,
                chainInfo = ChainInfo.Simple(chain.id),
                filters = TxFilter.entries.toSet(),
            )
        }.getOrNull()
        val soraOperations = soraHistory?.items.orEmpty().mapNotNull { item ->
            runCatching {
                item.toOperation(
                    chain,
                    chainAsset,
                    accountAddress,
                    filters,
                )
            }.getOrNull()
        }

        val nextCursor = if (soraHistory?.endReached == true) {
            null
        } else {
            soraHistory?.page?.let { (it + 1).toString() }
        }
        return CursorPage(nextCursor, soraOperations)
    }
}
