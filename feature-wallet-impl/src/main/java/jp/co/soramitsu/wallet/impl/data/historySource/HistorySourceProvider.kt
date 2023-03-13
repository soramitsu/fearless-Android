package jp.co.soramitsu.wallet.impl.data.historySource

import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.impl.data.network.subquery.OperationsHistoryApi
import jp.co.soramitsu.xnetworking.networkclient.SoramitsuNetworkClient
import jp.co.soramitsu.xnetworking.txhistory.client.sorawallet.SubQueryClientForSoraWalletFactory

class HistorySourceProvider(
    private val walletOperationsApi: OperationsHistoryApi,
    private val chainRegistry: ChainRegistry,
    private val soramitsuNetworkClient: SoramitsuNetworkClient,
    private val soraSubqueryFactory: SubQueryClientForSoraWalletFactory
) {
    operator fun invoke(historyUrl: String, historyType: Chain.ExternalApi.Section.Type): HistorySource? {
        return when (historyType) {
            Chain.ExternalApi.Section.Type.SUBQUERY -> SubqueryHistorySource(walletOperationsApi, chainRegistry, historyUrl)
            Chain.ExternalApi.Section.Type.SORA -> SoraHistorySource(soramitsuNetworkClient, soraSubqueryFactory, historyUrl)
            Chain.ExternalApi.Section.Type.SUBSQUID -> SubsquidHistorySource(walletOperationsApi, historyUrl)
            Chain.ExternalApi.Section.Type.GIANTSQUID -> GiantsquidHistorySource(walletOperationsApi, historyUrl)
            else -> null
        }
    }
}
