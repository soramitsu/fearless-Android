package jp.co.soramitsu.walletconnect.impl.presentation

internal class WalletConnectDelegateRegistration(
    private val registerCoreDelegate: () -> Unit,
    private val registerWalletDelegate: () -> Unit
) {
    private val registrationLock = Any()

    @Volatile
    private var coreDelegateRegistered = false

    @Volatile
    private var walletDelegateRegistered = false

    fun registerIfReady(): Result<Unit> = synchronized(registrationLock) {
        walletConnectRuntimeBoundary {
            if (!coreDelegateRegistered) {
                registerCoreDelegate()
                coreDelegateRegistered = true
            }

            if (!walletDelegateRegistered) {
                registerWalletDelegate()
                walletDelegateRegistered = true
            }

            Unit
        }
    }
}
