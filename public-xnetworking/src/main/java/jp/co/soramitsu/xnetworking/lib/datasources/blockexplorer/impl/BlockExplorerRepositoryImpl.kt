package jp.co.soramitsu.xnetworking.lib.datasources.blockexplorer.impl

import jp.co.soramitsu.xnetworking.lib.datasources.blockexplorer.api.BlockExplorerRepository
import jp.co.soramitsu.xnetworking.lib.datasources.blockexplorer.api.BlockExplorerValue
import jp.co.soramitsu.xnetworking.lib.datasources.chainsconfig.api.ConfigDAO
import jp.co.soramitsu.xnetworking.lib.datasources.txhistory.api.TxHistoryRepository
import jp.co.soramitsu.xnetworking.lib.engines.rest.api.RestClient

class BlockExplorerRepositoryImpl(
    val configDAO: ConfigDAO,
    val restClient: RestClient,
    val txHistoryRepository: TxHistoryRepository
) : BlockExplorerRepository {
    override suspend fun getApy(chainId: String): List<BlockExplorerValue> = emptyList()

    override suspend fun getStakingRewarded(chainId: String, accountAddress: String): List<String> = emptyList()

    override suspend fun getValidatorsList(
        chainId: String,
        stashAccountAddress: String,
        historicalRange: List<String>
    ): List<String> = emptyList()
}
