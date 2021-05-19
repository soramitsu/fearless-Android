package jp.co.soramitsu.app.root.domain

import jp.co.soramitsu.common.data.network.StorageSubscriptionBuilder
import jp.co.soramitsu.common.utils.SuspendableProperty
import jp.co.soramitsu.common.utils.hasModule
import jp.co.soramitsu.core.updater.Updater
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.storage.subscribeUsing
import jp.co.soramitsu.runtime.RuntimeUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.withContext

class UpdateSystem(
    private val runtimeUpdater: RuntimeUpdater,
    private val runtimeProperty: SuspendableProperty<RuntimeSnapshot>,
    private val updaters: List<Updater>,
    private val socketService: SocketService
) {

    suspend fun start(): Flow<Updater.SideEffect> = withContext(Dispatchers.Default) {
        runtimeUpdater.initFromCache() // make sure runtime is available to other updaters, since they may use it to construct storage keys

        val scopeFlows = updaters.groupBy(Updater::scope).map { (scope, scopeUpdaters) ->
            scope.invalidationFlow().flatMapLatest {
                val runtimeMetadata = runtimeProperty.get().metadata

                val subscriptionBuilder = StorageSubscriptionBuilder.create()

                val updatersFlow = scopeUpdaters
                    .filter { it.requiredModules.all(runtimeMetadata::hasModule) }
                    .map { it.listenForUpdates(subscriptionBuilder).flowOn(Dispatchers.IO) }

                if (updatersFlow.isNotEmpty()) {
                    val cancellable = socketService.subscribeUsing(subscriptionBuilder.proxy.build())

                    updatersFlow.merge().onCompletion { cancellable.cancel() }
                } else {
                    emptyFlow()
                }
            }
        }

        scopeFlows.merge()
    }
}
