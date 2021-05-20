package jp.co.soramitsu.feature_crowdloan_impl.data.repository

import jp.co.soramitsu.common.data.network.runtime.binding.BlockNumber
import jp.co.soramitsu.common.data.network.runtime.binding.bindBlockNumber
import jp.co.soramitsu.common.utils.system
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.runtime.storage.source.StorageDataSource
import jp.co.soramitsu.runtime.storage.source.queryNonNull

class ChainStateRepository(
    private val localStorage: StorageDataSource,
) {

    suspend fun currentBlockNumber(): BlockNumber = localStorage.queryNonNull(
        keyBuilder = { it.metadata.system().storage("Number").storageKey() },
        binding = { scale, runtime -> bindBlockNumber(scale, runtime) }
    )
}
