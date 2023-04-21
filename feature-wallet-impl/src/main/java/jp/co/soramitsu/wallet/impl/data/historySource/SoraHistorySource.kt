package jp.co.soramitsu.wallet.impl.data.historySource

import jp.co.soramitsu.common.data.model.CursorPage
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraMainChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraTestChainId
import jp.co.soramitsu.wallet.impl.data.mappers.toOperation
import jp.co.soramitsu.wallet.impl.domain.interfaces.TransactionFilter
import jp.co.soramitsu.wallet.impl.domain.model.Operation
import jp.co.soramitsu.xnetworking.networkclient.SoramitsuNetworkClient
import jp.co.soramitsu.xnetworking.sorawallet.mainconfig.SoraRemoteConfigBuilder
import jp.co.soramitsu.xnetworking.txhistory.TxHistoryItem
import jp.co.soramitsu.xnetworking.txhistory.TxHistoryResult
import jp.co.soramitsu.xnetworking.txhistory.client.sorawallet.SubQueryClientForSoraWalletFactory

class SoraHistorySource(
    private val soramitsuNetworkClient: SoramitsuNetworkClient,
    private val soraSubqueryFactory: SubQueryClientForSoraWalletFactory,
    private val soraProdRemoteConfigBuilder: SoraRemoteConfigBuilder,
    private val soraStageRemoteConfigBuilder: SoraRemoteConfigBuilder
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
        val soraStartPage = 1L
        val page = cursor?.toLongOrNull() ?: soraStartPage

        val subQueryClientForSora = when (chain.id) {
            soraMainChainId -> soraSubqueryFactory.create(soramitsuNetworkClient, pageSize, soraProdRemoteConfigBuilder)
            soraTestChainId -> soraSubqueryFactory.create(soramitsuNetworkClient, pageSize, soraStageRemoteConfigBuilder)
            else -> return CursorPage(soraStartPage.toString(), emptyList())
        }

        val soraHistory: TxHistoryResult<TxHistoryItem>? = subQueryClientForSora.getTransactionHistoryPaged(
            accountAddress,
            page
        )

        val soraHistoryItems: List<TxHistoryItem> = soraHistory?.items.orEmpty()
        val soraOperations = soraHistoryItems.mapNotNull { it.toOperation(chain, chainAsset, accountAddress, filters) }
        return CursorPage(page.inc().toString(), soraOperations)
    }
}
