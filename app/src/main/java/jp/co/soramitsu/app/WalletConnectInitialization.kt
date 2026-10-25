package jp.co.soramitsu.app

import java.util.concurrent.CancellationException

internal fun initializeWalletConnectSafely(
    initialize: () -> Unit,
    onFailure: (RuntimeException) -> Unit
): Boolean = try {
    initialize()
    true
} catch (error: CancellationException) {
    throw error
} catch (error: RuntimeException) {
    onFailure(error)
    false
}
