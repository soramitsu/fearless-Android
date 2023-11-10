package jp.co.soramitsu.wallet.impl.data.historySource

import android.util.Log
import jp.co.soramitsu.common.data.model.CursorPage
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraMainChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraTestChainId
import jp.co.soramitsu.shared_utils.runtime.AccountId
import jp.co.soramitsu.wallet.impl.data.mappers.toOperation
import jp.co.soramitsu.wallet.impl.domain.interfaces.TransactionFilter
import jp.co.soramitsu.wallet.impl.domain.model.Operation
import jp.co.soramitsu.xnetworking.basic.networkclient.SoramitsuNetworkClient
import jp.co.soramitsu.xnetworking.basic.txhistory.TxHistoryItem
import jp.co.soramitsu.xnetworking.basic.txhistory.TxHistoryResult
import jp.co.soramitsu.xnetworking.fearlesswallet.txhistory.client.TxHistoryClientForFearlessWalletFactory
import jp.co.soramitsu.xnetworking.sorawallet.mainconfig.SoraRemoteConfigBuilder

class SoraHistorySource(
    private val soramitsuNetworkClient: SoramitsuNetworkClient,
    private val soraTxHistoryFactory: TxHistoryClientForFearlessWalletFactory,
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

        val client = when (chain.id) {
            soraMainChainId -> soraTxHistoryFactory.createSubSquid(soramitsuNetworkClient, pageSize)
            soraTestChainId -> soraTxHistoryFactory.createSubSquid(soramitsuNetworkClient, pageSize)
            else -> return CursorPage(soraStartPage.toString(), emptyList())
        }
        val url = "https://squid.subsquid.io/sora/v/v4/graphql"
        val soraHistory = kotlin.runCatching { client.getTransactionHistoryPaged(
            accountAddress,
            "sora",
            page,
            url
        ) }.onFailure {
            hashCode()
            Log.d("&&&", "sora history error: ${it}")
        }.getOrNull()
        Log.d("&&&", "page size: $pageSize result size:${soraHistory?.items?.size}")
        val soraHistoryItems: List<TxHistoryItem> = soraHistory?.items.orEmpty()
        val soraOperations = soraHistoryItems.mapNotNull {
            it.toOperation(
                chain,
                chainAsset,
                accountAddress,
                filters
            )
        }
        Log.d("&&&", "mapped size: ${soraOperations.size}")
        return CursorPage(page.inc().toString(), soraOperations)
    }
}
