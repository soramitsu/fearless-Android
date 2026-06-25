package jp.co.soramitsu.xnetworking.lib.datasources.txhistory.impl

import jp.co.soramitsu.xnetworking.lib.datasources.chainsconfig.api.ConfigDAO
import jp.co.soramitsu.xnetworking.lib.datasources.txhistory.api.HistoryItemsFilter
import jp.co.soramitsu.xnetworking.lib.datasources.txhistory.api.TxHistoryRepository
import jp.co.soramitsu.xnetworking.lib.datasources.txhistory.impl.builder.ExpectActualDBDriverFactory
import jp.co.soramitsu.xnetworking.lib.engines.rest.api.RestClient

class TxHistoryRepositoryImpl(
    val historyItemsFilter: HistoryItemsFilter,
    val restClient: RestClient,
    val configDAO: ConfigDAO,
    val databaseDriverFactory: ExpectActualDBDriverFactory
) : TxHistoryRepository
