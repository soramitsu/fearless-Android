package jp.co.soramitsu.feature_crowdloan_impl.data.repository

import jp.co.soramitsu.common.utils.crowdloan
import jp.co.soramitsu.common.utils.u32ArgumentFromStorageKey
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.feature_crowdloan_api.data.network.blockhain.binding.FundInfo
import jp.co.soramitsu.feature_crowdloan_api.data.network.blockhain.binding.ParaId
import jp.co.soramitsu.feature_crowdloan_api.data.network.blockhain.binding.bindFundInfo
import jp.co.soramitsu.feature_crowdloan_api.data.repository.CrowdloanRepository
import jp.co.soramitsu.runtime.storage.source.StorageDataSource

class CrowdloanRepositoryImpl(
    private val remoteStorage: StorageDataSource,
) : CrowdloanRepository {

    override suspend fun allFundInfos(): Map<ParaId, FundInfo> {
        return remoteStorage.queryByPrefix(
            prefixKeyBuilder = { it.metadata.crowdloan().storage("Funds").storageKey() },
            keyExtractor = { it.u32ArgumentFromStorageKey() },
            binding = { scale, runtime -> bindFundInfo(scale!!, runtime) }
        )
    }
}
