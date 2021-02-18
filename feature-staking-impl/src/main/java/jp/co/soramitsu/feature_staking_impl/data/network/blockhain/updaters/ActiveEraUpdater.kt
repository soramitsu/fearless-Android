package jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters

import jp.co.soramitsu.common.utils.SuspendableProperty
import jp.co.soramitsu.core.model.StorageEntry
import jp.co.soramitsu.core.storage.StorageCache
import jp.co.soramitsu.core.updater.SubscriptionBuilder
import jp.co.soramitsu.core.updater.Updater
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.metadata.module
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach

class ActiveEraUpdater(
    private val runtimeProperty: SuspendableProperty<RuntimeSnapshot>,
    private val storageCache: StorageCache
) : Updater {

    override suspend fun listenForUpdates(storageSubscriptionBuilder: SubscriptionBuilder): Flow<Updater.SideEffect> {
        val runtime = runtimeProperty.get()
        val activeEraKey = runtime.metadata.module("Staking").storage("ActiveEra").storageKey()

        return storageSubscriptionBuilder.subscribe(activeEraKey)
            .onEach {
                val storageEntry = StorageEntry(
                    storageKey = activeEraKey,
                    content = it.value,
                    runtimeVersion = storageCache.currentRuntimeVersion()
                )

                storageCache.insert(storageEntry)
            }.noSideAffects()
    }
}