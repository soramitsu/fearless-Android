package jp.co.soramitsu.wallet.impl.data.historySource

import jp.co.soramitsu.common.data.model.CursorPage
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.shared_utils.runtime.AccountId
import jp.co.soramitsu.wallet.impl.data.mappers.toOperation
import jp.co.soramitsu.wallet.impl.domain.interfaces.TransactionFilter
import jp.co.soramitsu.wallet.impl.domain.model.Operation
import jp.co.soramitsu.xnetworking.basic.networkclient.SoramitsuNetworkClient
import jp.co.soramitsu.xnetworking.basic.txhistory.TxHistoryItem
import jp.co.soramitsu.xnetworking.fearlesswallet.txhistory.client.TxHistoryClientForFearlessWalletFactory

class SoraHistorySource(
    soramitsuNetworkClient: SoramitsuNetworkClient,
    soraTxHistoryFactory: TxHistoryClientForFearlessWalletFactory
) : HistorySource {

    private val client = soraTxHistoryFactory.createSubSquid(soramitsuNetworkClient, 100)

    override suspend fun getOperations(
        pageSize: Int,
        cursor: String?,
        filters: Set<TransactionFilter>,
        accountId: AccountId,
        chain: Chain,
        chainAsset: Asset,
        accountAddress: String
    ): CursorPage<Operation> {
        val soraStartPage = 1L
        val page = cursor?.toLongOrNull() ?: soraStartPage
        val url = chain.externalApi?.history?.url ?: throw IllegalArgumentException("No url")

        val soraHistory = kotlin.runCatching {
            client.getTransactionHistoryPaged(
                accountAddress,
                "sora",
                page,
                url
            )
        }.getOrNull()

        val soraHistoryItems: List<TxHistoryItem> = soraHistory?.items.orEmpty()
        val soraOperations =
            soraHistoryItems.mapNotNull { item ->
                runCatching {
                    item.toOperation(
                        chain,
                        chainAsset,
                        accountAddress,
                        filters
                    )
                }.getOrNull()
            }

        val nextCursor = if (soraHistory?.endReached == true) null else page.inc().toString()
        return CursorPage(nextCursor, soraOperations)
    }
}
