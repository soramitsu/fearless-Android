package jp.co.soramitsu.wallet.impl.data.historySource

import jp.co.soramitsu.common.data.model.CursorPage
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.shared_utils.runtime.AccountId
import jp.co.soramitsu.wallet.impl.data.mappers.toOperation
import jp.co.soramitsu.wallet.impl.domain.interfaces.TransactionFilter
import jp.co.soramitsu.wallet.impl.domain.model.Operation
import jp.co.soramitsu.xnetworking.lib.datasources.txhistory.api.adapters.HistoryInfoRemoteLoader
import jp.co.soramitsu.xnetworking.lib.datasources.txhistory.api.models.ChainInfo
import jp.co.soramitsu.xnetworking.lib.datasources.txhistory.api.models.TxFilter

class SoraHistorySource(
    private val historyInfoRemoteLoader: HistoryInfoRemoteLoader,
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
            historyInfoRemoteLoader.loadHistoryInfo(
                pageCount = pageSize,
                cursor = cursor,
                signAddress = accountAddress,
                chainInfo = ChainInfo.Simple(chain.id),
                filters = setOf(TxFilter.TRANSFER, TxFilter.REWARD, TxFilter.EXTRINSIC),
            )
        }.getOrNull()

        val soraOperations = soraHistory?.items.orEmpty().mapNotNull { item ->
                runCatching {
                    item.toOperation(
                        chain,
                        chainAsset,
                        accountAddress,
                        filters
                    )
                }.getOrNull()
            }

        val nextCursor = if (soraHistory?.endReached == true) null else soraHistory?.endCursor
        return CursorPage(nextCursor, soraOperations)
    }
}
