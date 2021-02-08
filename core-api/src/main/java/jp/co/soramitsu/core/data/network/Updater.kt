package jp.co.soramitsu.core.data.network

interface Updater {
    /**
     * Implementations should be aware of cancellation
     */
    suspend fun listenForUpdates()
}