package jp.co.soramitsu.feature_crowdloan_impl.data.repository

import jp.co.soramitsu.common.utils.ConcurrentHasher.concurrentBlake2b256
import jp.co.soramitsu.common.utils.Modules
import jp.co.soramitsu.common.utils.SuspendableProperty
import jp.co.soramitsu.common.utils.crowdloan
import jp.co.soramitsu.common.utils.hasModule
import jp.co.soramitsu.common.utils.numberConstant
import jp.co.soramitsu.common.utils.slots
import jp.co.soramitsu.common.utils.storageKeys
import jp.co.soramitsu.common.utils.u32ArgumentFromStorageKey
import jp.co.soramitsu.common.utils.useValue
import jp.co.soramitsu.core.model.Node
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.bytes
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.u32
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.toByteArray
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_crowdloan_api.data.network.blockhain.binding.Contribution
import jp.co.soramitsu.feature_crowdloan_api.data.network.blockhain.binding.FundInfo
import jp.co.soramitsu.feature_crowdloan_api.data.network.blockhain.binding.LeaseEntry
import jp.co.soramitsu.feature_crowdloan_api.data.network.blockhain.binding.ParaId
import jp.co.soramitsu.feature_crowdloan_api.data.network.blockhain.binding.bindContribution
import jp.co.soramitsu.feature_crowdloan_api.data.network.blockhain.binding.bindFundInfo
import jp.co.soramitsu.feature_crowdloan_api.data.network.blockhain.binding.bindLeases
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

private const val CONTRIBUTIONS_CHILD_SUFFIX = "crowdloan"

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
            keyExtractor = { it.u32ArgumentFromStorageKey() }
        ) { scale, runtime, paraId -> bindFundInfo(scale!!, runtime, paraId) }
    }

    override suspend fun getWinnerInfo(funds: Map<ParaId, FundInfo>): Map<ParaId, Boolean> {
        return remoteStorage.queryKeys(
            keysBuilder = { it.metadata.slots().storage("Leases").storageKeys(it, funds.keys) },
            binding = { scale, runtimeSnapshot -> scale?.let { bindLeases(it, runtimeSnapshot) } }
        ).mapValues { (paraId, leases) ->
            val fund = funds.getValue(paraId)

            leases?.let { isWinner(leases, fund.bidderAccountId) } ?: false
        }
    }

    private fun isWinner(leases: List<LeaseEntry?>, bidderAccount: AccountId): Boolean {
        return leases.any { it?.accountId.contentEquals(bidderAccount) }
    }

    override suspend fun getParachainMetadata(): Map<ParaId, ParachainMetadata> {
        return withContext(Dispatchers.Default) {
            val networkType = accountRepository.getSelectedNodeOrDefault().networkType

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
            binder = { scale, runtime -> bindFundInfo(scale!!, runtime, parachainId) },
            networkType = networkType
        )
    }

    override suspend fun minContribution(): BigInteger = runtimeProperty.useValue { runtime ->
        runtime.metadata.crowdloan().numberConstant("MinContribution", runtime)
    }

    override suspend fun getContribution(
        accountId: AccountId,
        paraId: ParaId,
        trieIndex: BigInteger
    ): Contribution? {
        return remoteStorage.queryChildState(
            storageKeyBuilder = { it.typeRegistry["AccountId"]!!.bytes(it, accountId).toHexString(withPrefix = true) },
            childKeyBuilder = {
                val suffix = (CONTRIBUTIONS_CHILD_SUFFIX.encodeToByteArray() + u32.toByteArray(it, trieIndex))
                    .concurrentBlake2b256()

                write(suffix)
            },
            binder = { scale, runtime -> scale?.let { bindContribution(it, runtime) } }
        )
    }
}
