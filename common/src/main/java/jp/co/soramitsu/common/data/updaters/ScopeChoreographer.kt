package jp.co.soramitsu.common.data.updaters

import jp.co.soramitsu.common.data.network.StorageSubscriptionBuilder
import jp.co.soramitsu.core.updater.ScopedUpdater
import jp.co.soramitsu.core.updater.SubscriptionBuilder
import jp.co.soramitsu.core.updater.Updater
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.storage.subscribeUsing
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onCompletion

abstract class ScopeChoreographer<T>(
    private val socketService: SocketService,
    private val updaters: Array<ScopedUpdater<T>>
) : Updater {

    abstract suspend fun scopeKeyFlow(): Flow<T>

    override suspend fun listenForUpdates(
        storageSubscriptionBuilder: SubscriptionBuilder
    ): Flow<Updater.SideEffect> {
        return scopeKeyFlow()
            .flatMapLatest { scopeKey ->
                val accountSubscriptionBuilder = StorageSubscriptionBuilder.create()

                val flows = updaters.map { it.listenAccountUpdates(accountSubscriptionBuilder, scopeKey) }

                val cancellable = socketService.subscribeUsing(accountSubscriptionBuilder.proxy.build())

                flows.merge().onCompletion { cancellable.cancel() }
            }
    }
}