package jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters

import android.util.Log
import jp.co.soramitsu.common.data.network.rpc.BulkRetriever
import jp.co.soramitsu.common.data.network.rpc.retrieveAllValues
import jp.co.soramitsu.common.data.network.runtime.binding.bindActiveEraIndex
import jp.co.soramitsu.common.utils.SuspendableProperty
import jp.co.soramitsu.core.model.StorageEntry
import jp.co.soramitsu.core.storage.StorageCache
import jp.co.soramitsu.core.updater.SubscriptionBuilder
import jp.co.soramitsu.core.updater.Updater
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.metadata.module
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import java.math.BigInteger

class ElectedNominatorsUpdater(
    private val runtimeProperty: SuspendableProperty<RuntimeSnapshot>,
    private val bulkRetriever: BulkRetriever,
    private val storageCache: StorageCache,
) : Updater {

    override suspend fun listenForUpdates(storageSubscriptionBuilder: SubscriptionBuilder): Flow<Updater.SideEffect> {
        val runtime = runtimeProperty.get()
        val activeEraKey = runtime.metadata.module("Staking").storage("ActiveEra").storageKey()

        return storageCache.observeEntry(activeEraKey)
            .map {
                val activeEraIndex = bindActiveEraIndex(it.content!!, runtime)

                eraStakersPrefix(runtime, activeEraIndex)
            }
            .filterNot(storageCache::isPrefixInCache)
            .onEach(::updateNominatorsForEra)
            .flowOn(Dispatchers.IO)
            .noSideAffects()
    }

    private fun eraStakersPrefix(runtime: RuntimeSnapshot, activeEraIndex: BigInteger): String {
        return runtime.metadata.module("Staking").storage("ErasStakers").storageKey(runtime, activeEraIndex)
    }

    private suspend fun updateNominatorsForEra(eraStakersPrefix: String) = runCatching {
        Log.d("RX", "Updating elected nominators")

        val allValues = bulkRetriever.retrieveAllValues(eraStakersPrefix)

        val runtimeVersion = storageCache.currentRuntimeVersion()

        val toInsert = allValues.map { (key, value) ->
            StorageEntry(key, value, runtimeVersion)
        }

        storageCache.insert(toInsert)
    }
}