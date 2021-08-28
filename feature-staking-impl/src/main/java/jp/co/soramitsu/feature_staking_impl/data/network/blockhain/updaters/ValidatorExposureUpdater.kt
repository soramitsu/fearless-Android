package jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters

import jp.co.soramitsu.common.data.network.rpc.BulkRetriever
import jp.co.soramitsu.common.utils.SuspendableProperty
import jp.co.soramitsu.common.utils.staking
import jp.co.soramitsu.core.storage.StorageCache
import jp.co.soramitsu.core.updater.GlobalScopeUpdater
import jp.co.soramitsu.core.updater.SubscriptionBuilder
import jp.co.soramitsu.core.updater.Updater
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.currentNetworkType
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters.base.StakingUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import java.math.BigInteger

class ValidatorExposureUpdater(
    private val runtimeProperty: SuspendableProperty<RuntimeSnapshot>,
    private val bulkRetriever: BulkRetriever,
    private val accountRepository: AccountRepository,
    private val storageCache: StorageCache
) : GlobalScopeUpdater, StakingUpdater {

    override suspend fun listenForUpdates(storageSubscriptionBuilder: SubscriptionBuilder): Flow<Updater.SideEffect> {
        val runtime = runtimeProperty.get()

        return storageCache.observeActiveEraIndex(runtime, accountRepository.currentNetworkType())
            .map { eraStakersPrefix(runtime, it) }
            .filterNot(storageCache::isPrefixInCache)
            .onEach(::updateNominatorsForEra)
            .flowOn(Dispatchers.IO)
            .noSideAffects()
    }

    private fun eraStakersPrefix(runtime: RuntimeSnapshot, activeEraIndex: BigInteger): String {
        return runtime.metadata.staking().storage("ErasStakers").storageKey(runtime, activeEraIndex)
    }

    private suspend fun updateNominatorsForEra(eraStakersPrefix: String) = runCatching {
        bulkRetriever.fetchPrefixValuesToCache(eraStakersPrefix, storageCache)
    }
}
