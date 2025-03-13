package jp.co.soramitsu.wallet.impl.data.historySource

import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.TonRemoteSource
import jp.co.soramitsu.wallet.impl.data.network.subquery.OperationsHistoryApi
import jp.co.soramitsu.xnetworking.lib.datasources.txhistory.api.TxHistoryRepository
import jp.co.soramitsu.xnetworking.lib.datasources.txhistory.api.adapters.HistoryInfoRemoteLoader

class HistorySourceProvider(
    private val walletOperationsApi: OperationsHistoryApi,
    private val chainRegistry: ChainRegistry,
    private val txHistoryRepository: TxHistoryRepository,
    private val tonRemoteSource: TonRemoteSource
) {
    operator fun invoke(historyUrl: String, historyType: Chain.ExternalApi.Section.Type): HistorySource? {
        return when (historyType) {
            Chain.ExternalApi.Section.Type.SUBQUERY -> SubqueryHistorySource(walletOperationsApi, chainRegistry, historyUrl)
            Chain.ExternalApi.Section.Type.SORA -> SoraHistorySource(txHistoryRepository)
            Chain.ExternalApi.Section.Type.SUBSQUID -> SubsquidHistorySource(walletOperationsApi, historyUrl)
            Chain.ExternalApi.Section.Type.GIANTSQUID -> GiantsquidHistorySource(walletOperationsApi, historyUrl)
            Chain.ExternalApi.Section.Type.ETHERSCAN -> EtherscanHistorySource(walletOperationsApi, historyUrl)
            Chain.ExternalApi.Section.Type.OKLINK -> OkLinkHistorySource(walletOperationsApi, historyUrl)
            Chain.ExternalApi.Section.Type.BLOCKSCOUT -> BlockscoutHistorySource(walletOperationsApi, historyUrl)
            Chain.ExternalApi.Section.Type.REEF -> ReefHistorySource(walletOperationsApi, historyUrl)
            Chain.ExternalApi.Section.Type.KLAYTN -> KlaytnHistorySource(walletOperationsApi, historyUrl)
            Chain.ExternalApi.Section.Type.FIRE -> FireHistorySource(walletOperationsApi, historyUrl)
            Chain.ExternalApi.Section.Type.VICSCAN -> VicscanHistorySource(walletOperationsApi, historyUrl)
            Chain.ExternalApi.Section.Type.ZCHAINS -> ZchainsHistorySource(walletOperationsApi, historyUrl)
            Chain.ExternalApi.Section.Type.TON -> TonHistorySource(tonRemoteSource, historyUrl)
            else -> null
        }
    }
}
