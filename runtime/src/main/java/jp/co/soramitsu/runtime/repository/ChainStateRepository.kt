package jp.co.soramitsu.runtime.repository

import jp.co.soramitsu.common.data.network.runtime.binding.BlockNumber
import jp.co.soramitsu.common.data.network.runtime.binding.bindBlockNumber
import jp.co.soramitsu.common.utils.babe
import jp.co.soramitsu.common.utils.numberConstant
import jp.co.soramitsu.common.utils.optionalNumberConstant
import jp.co.soramitsu.common.utils.system
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.multiNetwork.getRuntime
import jp.co.soramitsu.runtime.storage.source.StorageDataSource
import jp.co.soramitsu.runtime.storage.source.observeNonNull
import jp.co.soramitsu.runtime.storage.source.queryNonNull
import kotlinx.coroutines.flow.Flow
import java.math.BigInteger

class ChainStateRepository(
    private val localStorage: StorageDataSource,
    private val chainRegistry: ChainRegistry
) {

    suspend fun expectedBlockTimeInMillis(chainId: ChainId): BigInteger {
        val runtime = chainRegistry.getRuntime(chainId)

        return runtime.metadata.babe().numberConstant("ExpectedBlockTime", runtime)
    }

    suspend fun blockHashCount(chainId: ChainId): BigInteger? {
        val runtime = chainRegistry.getRuntime(chainId)

        return runtime.metadata.system().optionalNumberConstant("BlockHashCount", runtime)
    }

    suspend fun currentBlock(chainId: ChainId) = localStorage.queryNonNull(
        keyBuilder = ::currentBlockStorageKey,
        binding = { scale, runtime -> bindBlockNumber(scale, runtime) },
        chainId = chainId
    )

    fun currentBlockNumberFlow(chainId: ChainId): Flow<BlockNumber> = localStorage.observeNonNull(
        keyBuilder = ::currentBlockStorageKey,
        binding = { scale, runtime -> bindBlockNumber(scale, runtime) },
        chainId = chainId
    )

    private fun currentBlockStorageKey(runtime: RuntimeSnapshot) = runtime.metadata.system().storage("Number").storageKey()
}
