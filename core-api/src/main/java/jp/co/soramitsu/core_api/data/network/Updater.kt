package jp.co.soramitsu.core_api.data.network

interface Updater {
    /**
     * Implementations should be aware of cancellation
     */
    suspend fun listenForUpdates()
}