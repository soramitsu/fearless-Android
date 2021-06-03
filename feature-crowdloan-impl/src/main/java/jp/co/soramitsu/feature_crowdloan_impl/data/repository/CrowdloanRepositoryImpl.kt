package jp.co.soramitsu.feature_crowdloan_impl.data.repository

import jp.co.soramitsu.common.utils.Modules
import jp.co.soramitsu.common.utils.SuspendableProperty
import jp.co.soramitsu.common.utils.crowdloan
import jp.co.soramitsu.common.utils.hasModule
import jp.co.soramitsu.common.utils.numberConstant
import jp.co.soramitsu.common.utils.slots
import jp.co.soramitsu.common.utils.u32ArgumentFromStorageKey
import jp.co.soramitsu.common.utils.useValue
import jp.co.soramitsu.core.model.Node
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_crowdloan_api.data.network.blockhain.binding.FundInfo
import jp.co.soramitsu.feature_crowdloan_api.data.network.blockhain.binding.ParaId
import jp.co.soramitsu.feature_crowdloan_api.data.network.blockhain.binding.bindFundInfo
import jp.co.soramitsu.feature_crowdloan_api.data.repository.CrowdloanRepository
import jp.co.soramitsu.feature_crowdloan_api.data.repository.ParachainMetadata
import jp.co.soramitsu.feature_crowdloan_impl.data.network.api.parachain.ParachainMetadataApi
import jp.co.soramitsu.feature_crowdloan_impl.data.network.api.parachain.mapParachainMetadataRemoteToParachainMetadata
import jp.co.soramitsu.runtime.ext.runtimeCacheName
import jp.co.soramitsu.runtime.storage.source.StorageDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.math.BigInteger

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

    override suspend fun blocksPerLeasePeriod(): BigInteger = runtimeProperty.useValue { runtime ->
        runtime.metadata.slots().numberConstant("LeasePeriod", runtime)
    }

    override fun fundInfoFlow(parachainId: ParaId, networkType: Node.NetworkType): Flow<FundInfo> {
        return remoteStorage.observe(
            keyBuilder = { it.metadata.crowdloan().storage("Funds").storageKey(it, parachainId) },
            binder = { scale, runtime -> bindFundInfo(scale!!, runtime) },
            networkType = networkType
        )
    }

    override suspend fun minContribution(): BigInteger = runtimeProperty.useValue { runtime ->
        runtime.metadata.crowdloan().numberConstant("MinContribution", runtime)
    }
}
