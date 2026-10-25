package jp.co.soramitsu.xnetworking.lib.datasources.blockexplorer.api

data class BlockExplorerValue(
    val id: String,
    val value: String
)

interface BlockExplorerRepository {
    suspend fun getApy(chainId: String): List<BlockExplorerValue>
    suspend fun getStakingRewarded(chainId: String, accountAddress: String): List<String>
    suspend fun getValidatorsList(
        chainId: String,
        stashAccountAddress: String,
        historicalRange: List<String>
    ): List<String>
}
