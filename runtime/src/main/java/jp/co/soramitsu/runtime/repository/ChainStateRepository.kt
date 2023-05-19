package jp.co.soramitsu.runtime.repository

import jp.co.soramitsu.common.data.network.runtime.binding.BlockNumber
import jp.co.soramitsu.common.data.network.runtime.binding.bindBlockNumber
import jp.co.soramitsu.common.utils.babe
import jp.co.soramitsu.common.utils.numberConstant
import jp.co.soramitsu.common.utils.optionalNumberConstant
import jp.co.soramitsu.common.utils.system
import jp.co.soramitsu.core.extrinsic.mortality.IChainStateRepository
import jp.co.soramitsu.runtime.di.LOCAL_STORAGE_SOURCE
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.multiNetwork.getRuntime
import jp.co.soramitsu.runtime.storage.source.StorageDataSource
import jp.co.soramitsu.runtime.storage.source.observeNonNull
import jp.co.soramitsu.runtime.storage.source.queryNonNull
import jp.co.soramitsu.shared_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.shared_utils.runtime.metadata.storage
import jp.co.soramitsu.shared_utils.runtime.metadata.storageKey
import kotlinx.coroutines.flow.Flow
import java.math.BigInteger
import javax.inject.Inject
import javax.inject.Named

class ChainStateRepository @Inject constructor(
    @Named(LOCAL_STORAGE_SOURCE) private val localStorage: StorageDataSource,
    private val chainRegistry: ChainRegistry
) : IChainStateRepository {

    override suspend fun expectedBlockTimeInMillis(chainId: ChainId, defaultTime: BigInteger): BigInteger {
        val runtime = chainRegistry.getRuntime(chainId)

        return runCatching {
            runtime.metadata.babe().numberConstant("ExpectedBlockTime", runtime)
        }.getOrDefault(defaultTime)
    }

    override suspend fun blockHashCount(chainId: ChainId): BigInteger? {
        val runtime = chainRegistry.getRuntime(chainId)

        return runtime.metadata.system().optionalNumberConstant("BlockHashCount", runtime)
    }

    override suspend fun currentBlock(chainId: ChainId) = localStorage.queryNonNull(
        keyBuilder = ::currentBlockStorageKey,
        binding = { scale, runtime -> bindBlockNumber(scale, runtime) },
        chainId = chainId
    )

    override fun currentBlockNumberFlow(chainId: ChainId): Flow<BlockNumber> = localStorage.observeNonNull(
        keyBuilder = ::currentBlockStorageKey,
        binding = { scale, runtime -> bindBlockNumber(scale, runtime) },
        chainId = chainId
    )

    private fun currentBlockStorageKey(runtime: RuntimeSnapshot) = runtime.metadata.system().storage("Number").storageKey()
}
