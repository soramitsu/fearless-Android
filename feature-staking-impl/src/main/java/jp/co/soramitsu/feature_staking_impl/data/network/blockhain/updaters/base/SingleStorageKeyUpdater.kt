package jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters.base

import jp.co.soramitsu.common.utils.SuspendableProperty
import jp.co.soramitsu.core.model.StorageEntry
import jp.co.soramitsu.core.storage.StorageCache
import jp.co.soramitsu.core.updater.GlobalScopeUpdater
import jp.co.soramitsu.core.updater.SubscriptionBuilder
import jp.co.soramitsu.core.updater.Updater
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach

abstract class SingleStorageKeyUpdater(
    private val runtimeProperty: SuspendableProperty<RuntimeSnapshot>,
    private val storageCache: StorageCache
) : GlobalScopeUpdater {

    abstract fun storageKey(runtime: RuntimeSnapshot): String

    override suspend fun listenForUpdates(storageSubscriptionBuilder: SubscriptionBuilder): Flow<Updater.SideEffect> {
        val runtime = runtimeProperty.get()
        val storageKey = storageKey(runtime)

        return storageSubscriptionBuilder.subscribe(storageKey)
            .onEach {
                val storageEntry = StorageEntry(
                    storageKey = storageKey,
                    content = it.value,
                    runtimeVersion = storageCache.currentRuntimeVersion()
                )

                storageCache.insert(storageEntry)
            }.noSideAffects()
    }
}