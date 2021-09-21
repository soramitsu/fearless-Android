package jp.co.soramitsu.core.updater

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf

/**
 * We do not want this extension to be visible outside of update system
 * So, we put it into marker interface, which will allow to reach it in consumers code
 */
interface SideEffectScope {

    fun <T> Flow<T>.noSideAffects(): Flow<Updater.SideEffect> = emptyFlow()
}

interface UpdateScope {

    suspend fun invalidationFlow(): Flow<Any>
}

object GlobalScope : UpdateScope {

    override suspend fun invalidationFlow() = flowOf(Unit)
}

interface GlobalScopeUpdater : Updater {

    override val scope
        get() = GlobalScope
}

interface Updater : SideEffectScope {

    val requiredModules: List<String>

    val scope: UpdateScope

    /**
     * Implementations should be aware of cancellation
     */
    suspend fun listenForUpdates(
        storageSubscriptionBuilder: SubscriptionBuilder
    ): Flow<SideEffect>

    interface SideEffect
}
