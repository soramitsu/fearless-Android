package jp.co.soramitsu.walletconnect.impl.presentation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.concurrent.CancellationException

/**
 * Contains recoverable failures raised synchronously by a WalletConnect call.
 *
 * Deliberately contains [RuntimeException] except coroutine cancellation. Fatal
 * VM/linkage errors and [CancellationException] propagate unchanged.
 */
internal inline fun <T> walletConnectRuntimeBoundary(operation: () -> T): Result<T> = try {
    Result.success(operation())
} catch (error: CancellationException) {
    throw error
} catch (error: RuntimeException) {
    Result.failure(error)
}

/**
 * Runs a WalletConnect call and reports its outcome to [onSuccess] or [onError]. Relay and storage
 * failures are reported; cancellation and fatal errors propagate.
 */
internal inline fun callWalletConnect(
    onSuccess: () -> Unit,
    onError: (Throwable) -> Unit,
    operation: () -> Unit
) {
    try {
        operation()
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        onError(error)
        return
    }
    onSuccess()
}

/** Where the interactor runs the WalletConnect calls its callers do not wait for. */
internal object WalletConnectCalls {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}

internal inline fun <T> walletConnectValueOrBack(value: T?, onUnavailable: () -> Unit): T? = value.also {
    if (it == null) onUnavailable()
}

internal inline fun <T> newestWalletConnectValueOrBack(
    values: List<T>,
    onUnavailable: () -> Unit,
    order: (T) -> Long
): T? = walletConnectValueOrBack(values.maxByOrNull(order), onUnavailable)
