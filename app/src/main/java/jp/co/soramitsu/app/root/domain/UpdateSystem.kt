package jp.co.soramitsu.app.root.domain

import jp.co.soramitsu.common.data.network.StorageSubscriptionBuilder
import jp.co.soramitsu.common.utils.hasModule
import jp.co.soramitsu.core.model.Node
import jp.co.soramitsu.core.model.chainId
import jp.co.soramitsu.core.updater.Updater
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.storage.subscribeUsing
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.getRuntime
import jp.co.soramitsu.runtime.multiNetwork.getSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onCompletion

// TODO update system - split
class UpdateSystem(
    private val updaters: List<Updater>,
    private val chainRegistry: ChainRegistry,
) {

    fun start(): Flow<Updater.SideEffect> = flow {
        val firstChain = chainRegistry.getChain(Node.NetworkType.POLKADOT.chainId)
        val socket = chainRegistry.getSocket(firstChain.id)
        val runtime = chainRegistry.getRuntime(firstChain.id)

        val scopeFlows = updaters.groupBy(Updater::scope).map { (scope, scopeUpdaters) ->
            scope.invalidationFlow().flatMapLatest {
                val runtimeMetadata = runtime.metadata

                val subscriptionBuilder = StorageSubscriptionBuilder.create(socket)

                val updatersFlow = scopeUpdaters
                    .filter { it.requiredModules.all(runtimeMetadata::hasModule) }
                    .map { it.listenForUpdates(subscriptionBuilder).flowOn(Dispatchers.IO) }

                if (updatersFlow.isNotEmpty()) {
                    val cancellable = socket.subscribeUsing(subscriptionBuilder.proxy.build())

                    updatersFlow.merge().onCompletion { cancellable.cancel() }
                } else {
                    emptyFlow()
                }
            }
        }

        emitAll(scopeFlows.merge())
    }.flowOn(Dispatchers.Default)
}
