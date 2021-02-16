package jp.co.soramitsu.core.updater

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transform

interface Updater {
    /**
     * Implementations should be aware of cancellation
     */
    suspend fun listenForUpdates(): Flow<SideEffect>

    interface SideEffect

    fun <T> Flow<T>.noSideAffects(): Flow<SideEffect> = transform { }
}