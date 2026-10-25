package jp.co.soramitsu.core.runtime

import jp.co.soramitsu.core.models.ChainId
import jp.co.soramitsu.core.models.IChain
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot

interface IChainRegistry {
    suspend fun getChain(chainId: ChainId): IChain

    suspend fun getChains(): List<IChain>

    fun getConnection(chainId: String): ChainConnection

    suspend fun getRuntime(chainId: ChainId): RuntimeSnapshot
}
