package jp.co.soramitsu.runtime.network.updaters

import jp.co.soramitsu.common.data.network.StorageSubscriptionBuilder
import jp.co.soramitsu.common.utils.hasModule
import jp.co.soramitsu.core.updater.UpdateSystem
import jp.co.soramitsu.core.updater.Updater
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.storage.subscribeUsing
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.getRuntime
import jp.co.soramitsu.runtime.multiNetwork.getSocket
import jp.co.soramitsu.runtime.state.SingleAssetSharedState
import jp.co.soramitsu.runtime.state.selectedChainFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onCompletion

class SingleChainUpdateSystem(
    private val updaters: List<Updater>,
    private val chainRegistry: ChainRegistry,
    private val singleAssetSharedState: SingleAssetSharedState,
) : UpdateSystem {

    override fun start(): Flow<Updater.SideEffect> = singleAssetSharedState.selectedChainFlow().flatMapLatest { chain ->
        val socket = chainRegistry.getSocket(chain.id)
        val runtimeMetadata = chainRegistry.getRuntime(chain.id).metadata

        val scopeFlows = updaters.groupBy(Updater::scope).map { (scope, scopeUpdaters) ->
            scope.invalidationFlow().flatMapLatest {
                val subscriptionBuilder = StorageSubscriptionBuilder.create(socket)

                val updatersFlow = scopeUpdaters
                    .filter { it.requiredModules.all(runtimeMetadata::hasModule) }
                    .map { it.listenForUpdates(subscriptionBuilder).flowOn(Dispatchers.Default) }

                if (updatersFlow.isNotEmpty()) {
                    val cancellable = socket.subscribeUsing(subscriptionBuilder.build())

                    updatersFlow.merge().onCompletion {
                        cancellable.cancel()
                    }
                } else {
                    emptyFlow()
                }
            }
        }

        scopeFlows.merge()
    }.flowOn(Dispatchers.Default)
}
