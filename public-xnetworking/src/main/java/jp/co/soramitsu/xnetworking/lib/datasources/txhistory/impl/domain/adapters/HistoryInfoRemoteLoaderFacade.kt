package jp.co.soramitsu.xnetworking.lib.datasources.txhistory.impl.domain.adapters

import jp.co.soramitsu.xnetworking.lib.datasources.chainsconfig.api.ConfigDAO
import jp.co.soramitsu.xnetworking.lib.datasources.txhistory.api.adapters.HistoryInfoRemoteLoader
import jp.co.soramitsu.xnetworking.lib.datasources.txhistory.api.models.ChainInfo
import jp.co.soramitsu.xnetworking.lib.datasources.txhistory.api.models.TxFilter
import jp.co.soramitsu.xnetworking.lib.datasources.txhistory.api.models.TxHistoryInfo
import jp.co.soramitsu.xnetworking.lib.engines.rest.api.RestClient

class HistoryInfoRemoteLoaderFacade(
    val configDAO: ConfigDAO,
    val restClient: RestClient
) : HistoryInfoRemoteLoader {
    override suspend fun loadHistoryInfo(
        pageCount: Int,
        cursor: String?,
        signAddress: String,
        chainInfo: ChainInfo,
        filters: Set<TxFilter>
    ): TxHistoryInfo = TxHistoryInfo(
        endCursor = null,
        endReached = true,
        items = emptyList()
    )
}
