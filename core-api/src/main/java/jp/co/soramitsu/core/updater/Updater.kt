package jp.co.soramitsu.core.updater

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transform

/**
 * We do not want this extension to be visible outside of update system
 * So, we put it into marker interface, which will allow to reach it in consumers code
 */
interface SideEffectScope {

    fun <T> Flow<T>.noSideAffects(): Flow<Updater.SideEffect> = transform { }
}

interface Updater : SideEffectScope {

    /**
     * Implementations should be aware of cancellation
     */
    suspend fun listenForUpdates(storageSubscriptionBuilder: SubscriptionBuilder): Flow<SideEffect>

    interface SideEffect
}