package jp.co.soramitsu.feature_crowdloan_impl.data.repository

import jp.co.soramitsu.common.data.network.runtime.binding.BlockNumber
import jp.co.soramitsu.common.data.network.runtime.binding.bindBlockNumber
import jp.co.soramitsu.common.utils.SuspendableProperty
import jp.co.soramitsu.common.utils.babe
import jp.co.soramitsu.common.utils.numberConstant
import jp.co.soramitsu.common.utils.system
import jp.co.soramitsu.core.model.Node
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.runtime.storage.source.StorageDataSource
import jp.co.soramitsu.runtime.storage.source.observeNonNull
import jp.co.soramitsu.runtime.storage.source.queryNonNull
import kotlinx.coroutines.flow.Flow
import java.math.BigInteger

class ChainStateRepository(
    private val localStorage: StorageDataSource,
    private val runtimeProperty: SuspendableProperty<RuntimeSnapshot>,
) {

    suspend fun expectedBlockTimeInMillis(): BigInteger {
        val runtime = runtimeProperty.get()

        return runtime.metadata.babe().numberConstant("ExpectedBlockTime", runtime)
    }

    suspend fun currentBlock() = localStorage.queryNonNull(
        keyBuilder = ::currentBlockStorageKey,
        binding = { scale, runtime -> bindBlockNumber(scale, runtime) },
    )

    fun currentBlockNumberFlow(networkType: Node.NetworkType): Flow<BlockNumber> = localStorage.observeNonNull(
        keyBuilder = ::currentBlockStorageKey,
        binding = { scale, runtime -> bindBlockNumber(scale, runtime) },
        networkType = networkType
    )

    private fun currentBlockStorageKey(runtime: RuntimeSnapshot) = runtime.metadata.system().storage("Number").storageKey()
}
