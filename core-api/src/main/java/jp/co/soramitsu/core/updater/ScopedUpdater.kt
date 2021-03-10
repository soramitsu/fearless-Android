package jp.co.soramitsu.core.updater

import kotlinx.coroutines.flow.Flow

interface ScopedUpdater<T> : SideEffectScope {

    suspend fun listenAccountUpdates(
        accountSubscriptionBuilder: SubscriptionBuilder,
        scopeKey: T
    ): Flow<Updater.SideEffect>
}