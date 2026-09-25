package jp.co.soramitsu.common.data.storage.encrypt

/**
 * Debug-only hooks for process-death and durability-failure integration tests.
 * This class is absent from production release artifacts.
 */
object WalletSecureStorageHealthTestHooks {

    fun latchProcessRestartRequired() {
        WalletSecureStorageHealth.latchProcessRestartRequired()
    }

    fun reset() {
        WalletSecureStorageHealth.resetForTest()
    }
}
