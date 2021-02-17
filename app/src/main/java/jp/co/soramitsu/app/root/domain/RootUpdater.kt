package jp.co.soramitsu.app.root.domain

import jp.co.soramitsu.common.data.network.StorageSubscriptionBuilder
import jp.co.soramitsu.core.updater.Updater
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.storage.subscribeUsing
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion

class RootUpdater(
    private val wrapped: Updater,
    private val socketService: SocketService
) {

    suspend fun listenForUpdates(): Flow<Updater.SideEffect> {
        val subscriptionBuilder = StorageSubscriptionBuilder.create()

        val updatesFlow = wrapped.listenForUpdates(subscriptionBuilder)
        val cancellable = socketService.subscribeUsing(subscriptionBuilder.proxy.build())

        return updatesFlow.onCompletion { cancellable.cancel() }
    }
}