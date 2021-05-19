package jp.co.soramitsu.feature_crowdloan_impl.data.repository

import jp.co.soramitsu.common.utils.Modules
import jp.co.soramitsu.common.utils.SuspendableProperty
import jp.co.soramitsu.common.utils.crowdloan
import jp.co.soramitsu.common.utils.hasModule
import jp.co.soramitsu.common.utils.u32ArgumentFromStorageKey
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_crowdloan_api.data.network.blockhain.binding.FundInfo
import jp.co.soramitsu.feature_crowdloan_api.data.network.blockhain.binding.ParaId
import jp.co.soramitsu.feature_crowdloan_api.data.network.blockhain.binding.bindFundInfo
import jp.co.soramitsu.feature_crowdloan_api.data.repository.CrowdloanRepository
import jp.co.soramitsu.feature_crowdloan_api.data.repository.ParachainMetadata
import jp.co.soramitsu.feature_crowdloan_impl.data.network.ParachainMetadataApi
import jp.co.soramitsu.feature_crowdloan_impl.data.network.mapParachainMetadataRemoteToParachainMetadata
import jp.co.soramitsu.runtime.ext.runtimeCacheName
import jp.co.soramitsu.runtime.storage.source.StorageDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class CrowdloanRepositoryImpl(
    private val remoteStorage: StorageDataSource,
    private val accountRepository: AccountRepository,
    private val runtimeProperty: SuspendableProperty<RuntimeSnapshot>,
    private val parachainMetadataApi: ParachainMetadataApi
) : CrowdloanRepository {

    override fun crowdloanAvailableFlow(): Flow<Boolean> {
        return runtimeProperty.observe().map {
            it.metadata.hasModule(Modules.CROWDLOAN)
        }
    }

    override suspend fun allFundInfos(): Map<ParaId, FundInfo> {
        return remoteStorage.queryByPrefix(
            prefixKeyBuilder = { it.metadata.crowdloan().storage("Funds").storageKey() },
            keyExtractor = { it.u32ArgumentFromStorageKey() },
            binding = { scale, runtime -> bindFundInfo(scale!!, runtime) }
        )
    }

    override suspend fun getParachainMetadata(): Map<ParaId, ParachainMetadata> {
        return withContext(Dispatchers.Default) {
            val networkType = accountRepository.getSelectedNode().networkType

            parachainMetadataApi.getParachainMetadata(networkType.runtimeCacheName())
                .associateBy { it.paraid }
                .mapValues { (_, remoteMetadata) -> mapParachainMetadataRemoteToParachainMetadata(remoteMetadata) }
        }
    }
}
