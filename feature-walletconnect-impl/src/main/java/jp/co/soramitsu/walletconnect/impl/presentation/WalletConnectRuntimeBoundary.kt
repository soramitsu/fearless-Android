package jp.co.soramitsu.walletconnect.impl.presentation

import com.reown.walletkit.client.Wallet
import java.util.concurrent.CancellationException

/**
 * Contains recoverable failures raised synchronously by the optional Reown SDK.
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

internal inline fun callWalletConnect(onError: (Wallet.Model.Error) -> Unit, operation: () -> Unit) {
    walletConnectRuntimeBoundary(operation)
        .onFailure { error -> onError(Wallet.Model.Error(error)) }
}

internal inline fun <T> walletConnectValueOrBack(value: T?, onUnavailable: () -> Unit): T? = value.also {
    if (it == null) onUnavailable()
}

internal inline fun <T> newestWalletConnectValueOrBack(
    values: List<T>,
    onUnavailable: () -> Unit,
    order: (T) -> Long
): T? = walletConnectValueOrBack(values.maxByOrNull(order), onUnavailable)
